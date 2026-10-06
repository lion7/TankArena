package com.tankarena.sim.kubriko.server

import com.pandulapeter.kubriko.Kubriko
import com.pandulapeter.kubriko.actor.Actor
import com.pandulapeter.kubriko.actor.body.PointBody
import com.pandulapeter.kubriko.collision.CollisionManager
import com.pandulapeter.kubriko.helpers.ManualTickSource
import com.pandulapeter.kubriko.helpers.TickSource
import com.pandulapeter.kubriko.helpers.extensions.get
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.manager.ActorManager
import com.pandulapeter.kubriko.serialization.Serializable
import com.pandulapeter.kubriko.serialization.SerializableMetadata
import com.pandulapeter.kubriko.types.SceneOffset
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.MapMetadata
import com.tankarena.content.MapSceneSidecar
import com.tankarena.content.TileLayers
import com.tankarena.content.TerrainGridBuilder
import com.tankarena.protocol.snapshot.TerrainGrid
import com.tankarena.protocol.snapshot.TerrainMaterial
import com.tankarena.protocol.snapshot.PitShape
import com.tankarena.protocol.snapshot.TerrainTile
import com.tankarena.sim.kubriko.server.legacy.CanonicalMapDefinition
import com.tankarena.input.PlayerIntentFrame
import com.tankarena.protocol.Team
import com.tankarena.protocol.snapshot.ActorState
import com.tankarena.protocol.snapshot.ExplosionKind
import com.tankarena.protocol.snapshot.B52State
import com.tankarena.protocol.snapshot.DestroyerState
import com.tankarena.protocol.snapshot.EnforcerState
import com.tankarena.protocol.snapshot.FlagState
import com.tankarena.protocol.snapshot.GameEvent
import com.tankarena.protocol.snapshot.GoalState
import com.tankarena.protocol.snapshot.LockState
import com.tankarena.protocol.snapshot.ProductState
import com.tankarena.protocol.snapshot.TrainState
import com.tankarena.protocol.snapshot.WarpState
import com.tankarena.protocol.snapshot.ZeppelinState
import com.tankarena.protocol.snapshot.HudState
import com.tankarena.protocol.snapshot.PlayerView
import com.tankarena.protocol.snapshot.LockMarker
import com.tankarena.protocol.snapshot.LockMarkerState
import com.tankarena.protocol.snapshot.MineState
import com.tankarena.protocol.snapshot.ProjectileKind
import com.tankarena.protocol.snapshot.ProjectileOwnerKind
import com.tankarena.protocol.snapshot.ProjectileState
import com.tankarena.protocol.snapshot.RadarContact
import com.tankarena.protocol.snapshot.RadarContactKind
import com.tankarena.protocol.snapshot.TankState
import com.tankarena.protocol.snapshot.TurretState
import com.tankarena.protocol.snapshot.WallState
import com.tankarena.protocol.snapshot.WorldSnapshot
import com.tankarena.sim.kubriko.TerrainSlideManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

private const val MILLIS_PER_TICK: Int = 10
/** Legacy nearest_target(): a rocket locks onto anything within 15 px of the marker. */
private const val ROCKET_TARGET_RADIUS_PX: Int = 15
/** Legacy tank_write_marker(): the marker turns red beyond 400 px. */
private const val LOCK_OUT_OF_RANGE_PX: Int = 400
private val DEADLY_TERRAIN = setOf(
    TerrainMaterial.PIT_BIG,
    TerrainMaterial.PIT_SMALL,
    TerrainMaterial.LAVA,
)
private const val TANK_FOOTPRINT: Int = LEGACY_TILE_SIZE - 4
private const val TRAIN_FOOTPRINT: Int = 32
private const val ZEPPELIN_FOOTPRINT: Int = 32
private const val B52_FOOTPRINT: Int = 32

class ServerMatchPrototype private constructor(
    private val mapMetadata: MapMetadata,
    private val initialActors: List<Serializable<*>>,
    private val terrainGrid: TerrainGrid,
    private val solidLayer: List<Int>,
    val mode: MatchMode,
) {

    private val tickSource: ManualTickSource = TickSource.manual() as ManualTickSource
    private val serializationManager = SerializableMetadata.newSerializationManagerInstance(
        *tankArenaSerializableMetadata,
    )
    private val worldWidthPixels: Int = mapMetadata.widthTiles * LEGACY_TILE_SIZE
    private val worldHeightPixels: Int = mapMetadata.heightTiles * LEGACY_TILE_SIZE
    private val kubriko: Kubriko = Kubriko.newInstance(
        ActorManager.newInstance(
            initialActors = initialActors,
            shouldPutFarAwayActorsToSleep = false,
        ),
        CollisionManager.newInstance(),
        serializationManager,
        TerrainSlideManager(worldWidthPixels, worldHeightPixels),
        tickSource = tickSource,
    )
    private val actorManager: ActorManager = kubriko.get()
    private val actorIds: MutableMap<Actor, Long> = IdentityHashMap()
    private val tanksByPlayerIndex: MutableMap<Int, ServerTankActor> = HashMap()
    /** Tanks are never spawned or removed after [initialize], so this list is stable for the match. */
    private var allTanks: List<ServerTankActor> = emptyList()
    private var nextActorId: Long = 1L
    private var currentTick: Long = 0L
    private var isDisposed: Boolean = false
    private var goalGood: Int = 0
    private var goalBad: Int = 0
    private var missionStatus: MissionStatus = MissionStatus.IN_PROGRESS
    private val pendingEvents: MutableList<GameEvent> = mutableListOf()

    // Mission stats (T29)
    private var missionKills: Int = 0
    private var missionCaptures: Int = 0
    private var missionScore: Int = 0
    private var missionResultEmitted: Boolean = false
    private var lossReason: String = "GOALS_LOST"
    /** True when the objects' goal contributions can reach 100% on their own (see [evaluateMission]). */
    private var goalCounterReachable: Boolean = false

    val mission: MissionStatus get() = missionStatus
    val goalProgressGood: Int get() = goalGood
    val goalProgressBad: Int get() = goalBad

    enum class MissionStatus { IN_PROGRESS, WON, LOST }

    val worldWidth: Int get() = worldWidthPixels
    val worldHeight: Int get() = worldHeightPixels
    val missionCode: String get() = mapMetadata.missionCode

    fun initialize() {
        tickSource.start()
        for (actor in actorManager.allActors.value) {
            actorIds.getOrPut(actor) { nextActorId++ }
            if (actor is ServerTankActor && actor.playerIndex >= 0) {
                tanksByPlayerIndex.putIfAbsent(actor.playerIndex, actor)
            }
        }
        allTanks = actorManager.allActors.value.filterIsInstance<ServerTankActor>()
        for (tank in allTanks) {
            tank.configureRocketLock(
                maxX = worldWidthPixels - LEGACY_TILE_SIZE,
                maxY = worldHeightPixels - LEGACY_TILE_SIZE,
                limitRange = mode != MatchMode.DUAL,
            )
        }
        val all = actorManager.allActors.value
        val reachable = all.filterIsInstance<ServerTankActor>().sumOf { it.goalContribution.coerceAtLeast(0) } +
            all.filterIsInstance<ServerTurretActor>().sumOf { it.goalContribution.coerceAtLeast(0) } +
            all.filterIsInstance<ServerGoalActor>().filter { it.who == 0 }.sumOf { it.contribution.coerceAtLeast(0) }
        goalCounterReachable = reachable >= 100
    }

    fun tick(): WorldSnapshot = tick(intents = emptyMap())

    fun tick(intents: Map<Int, PlayerIntentFrame>): WorldSnapshot {
        if (isDisposed) return buildSnapshot(drainEvents = false)
        if (intents.isNotEmpty()) {
            for ((playerIndex, intent) in intents) {
                if (intent.selectTankId > 0) switchToTank(playerIndex, intent.selectTankId)
            }
            // Every tank in a player's group obeys that player's controls (legacy control_tank()).
            for (tank in allTanks) {
                if (tank.playerIndex < 0) continue
                intents[tank.playerIndex]?.let(tank::applyIntent)
            }
        }
        stepAiTanks()
        stepTurrets()
        applyTerrainEffects()
        tickSource.tick(MILLIS_PER_TICK)
        flushOutOfBoundsProjectiles()
        flushOutOfBoundsRockets()
        flushOutOfBoundsMortars()
        removeDeadProjectiles()
        triggerMineContacts()
        drainMineDetonations()
        drainRocketExplosions()
        drainMortarExplosions()
        removeDeadMines()
        removeDeadRockets()
        removeDeadMortars()
        drainFireRequests()
        drainMineRequests()
        drainRocketRequests()
        drainMortarRequests()
        drainTankLifecycleEvents()
        drainTurretDestructions()
        handOverDeadPlayers()
        stepWarps()
        stepDestroyers()
        stepEnforcers()
        stepTrains()
        stepZeppelins()
        stepB52s()
        collectFlags()
        collectProducts()
        collectGoals()
        evaluateMission()
        currentTick += 1
        return buildSnapshot(drainEvents = true)
    }

    fun snapshot(): WorldSnapshot = buildSnapshot(drainEvents = false)

    internal fun playerTankForTest(playerIndex: Int): ServerTankActor? = tanksByPlayerIndex[playerIndex]

    internal fun tanksForTest(): List<ServerTankActor> = allTanks

    internal fun turretsForTest(): List<ServerTurretActor> =
        actorManager.allActors.value.filterIsInstance<ServerTurretActor>()

    internal fun solidTileCountForTest(): Int = solidLayer.count { it >= 0 }

    internal fun terrainTileForTest(predicate: (TerrainTile) -> Boolean): Pair<Int, Int>? {
        for (y in 0 until mapMetadata.heightTiles) {
            for (x in 0 until mapMetadata.widthTiles) {
                if (predicate(terrainGrid.at(x, y))) return x to y
            }
        }
        return null
    }

    internal fun terrainCountForTest(material: TerrainMaterial): Int {
        var count = 0
        for (y in 0 until mapMetadata.heightTiles) {
            for (x in 0 until mapMetadata.widthTiles) {
                if (terrainGrid.at(x, y).material == material) count++
            }
        }
        return count
    }

    internal fun queueDamageForTest(playerIndex: Int, amount: Int) {
        tanksByPlayerIndex[playerIndex]?.queueDamage(amount)
    }

    /** Damage the first alive enemy tank (for scenario tests). */
    internal fun queueDamageToEnemyForTest(amount: Int) {
        val enemy = actorManager.allActors.value
            .filterIsInstance<ServerTankActor>()
            .firstOrNull { it.team == 1 && it.armor > 0 }
        enemy?.queueDamage(amount)
    }

    internal fun snapshotMines(): List<ServerMineActor> =
        actorManager.allActors.value.filterIsInstance<ServerMineActor>()

    internal fun snapshotRockets(): List<ServerRocketActor> =
        actorManager.allActors.value.filterIsInstance<ServerRocketActor>()

    internal fun snapshotMortars(): List<ServerMortarActor> =
        actorManager.allActors.value.filterIsInstance<ServerMortarActor>()

    internal fun injectMortarExplosionForTest(x: Int, y: Int, radius: Int, damage: Int) {
        val tanks = actorManager.allActors.value.filterIsInstance<ServerTankActor>()
        pendingEvents.addAll(AreaDamageResolver.resolve(
            tanks = tanks,
            x = x,
            y = y,
            radius = radius,
            damage = damage,
            kind = ExplosionKind.MORTAR,
        ))
    }

    internal fun injectArmedMineForTest(x: Int, y: Int, damage: Int, radius: Int) {
        val mine = ServerMineActor(
            ServerMineActor.State(
                body = PointBody(initialPosition = SceneOffset(x.toFloat().sceneUnit, y.toFloat().sceneUnit)),
                ownerActorId = 0L,
                damage = damage,
                radius = radius,
                activationTicksRemaining = 0,
            ),
        )
        val expected = actorManager.allActors.value.size + 1
        actorManager.add(listOf(mine))
        awaitActorCount(expected)
        actorIds.getOrPut(mine) { nextActorId++ }
    }

    fun buildPlayerViews(): List<PlayerView> {
        val all = actorManager.allActors.value
        val tanks = all.filterIsInstance<ServerTankActor>()
        val turrets = all.filterIsInstance<ServerTurretActor>()
        val goals = all.filterIsInstance<ServerGoalActor>()
        val statusText = when (missionStatus) {
            MissionStatus.IN_PROGRESS -> ""
            MissionStatus.WON -> "MISSION_WON"
            MissionStatus.LOST -> "MISSION_LOST"
        }
        val views = mutableListOf<PlayerView>()
        for ((playerIndex, tank) in tanksByPlayerIndex) {
            val actorId = actorIds[tank]
            views += PlayerView(
                playerId = playerIndex,
                controlledActorId = actorId,
                // Legacy draw_screen(): the view follows the rocket marker while it is out.
                cameraCenterX = if (tank.lockCameraTicks > 0) tank.lockX else tank.positionX,
                cameraCenterY = if (tank.lockCameraTicks > 0) tank.lockY else tank.positionY,
                hud = HudState(
                    armor = tank.armor,
                    shield = tank.shield,
                    invulnerableTicks = tank.invulnerableTicks,
                    fuel = tank.fuel,
                    lives = tank.lives,
                    missionProgress = goalGood,
                    missionCode = missionCode,
                    statusText = statusText,
                    score = missionScore,
                    kills = missionKills,
                    time = currentTick,
                    currentWeapon = tank.currentWeapon,
                    chainAmmo = tank.chainAmmo,
                    mineAmmo = tank.mineAmmo,
                    rocketAmmo = tank.rocketAmmo,
                    mortarAmmo = tank.mortarAmmo,
                    maxArmor = tank.maxArmor,
                    maxFuel = tank.maxFuel,
                    missionProgressBad = goalBad,
                ),
                radar = buildRadar(tank, tanks, turrets, goals),
                lockMarker = lockMarkerFor(tank),
            )
        }
        return views
    }

    private fun buildRadar(
        self: ServerTankActor,
        tanks: List<ServerTankActor>,
        turrets: List<ServerTurretActor>,
        goals: List<ServerGoalActor>,
    ): List<RadarContact> {
        val contacts = mutableListOf<RadarContact>()
        for (tank in tanks) {
            if (tank === self) continue
            val id = actorIds[tank] ?: continue
            contacts += RadarContact(
                actorId = id,
                approximateX = tank.positionX,
                approximateY = tank.positionY,
                kind = RadarContactKind.TANK,
                team = tank.team.toProtocolTeam(),
            )
        }
        for (turret in turrets) {
            if (!turret.isAlive) continue
            val id = actorIds[turret] ?: continue
            contacts += RadarContact(
                actorId = id,
                approximateX = turret.positionX,
                approximateY = turret.positionY,
                kind = RadarContactKind.TURRET,
                team = turret.team.toProtocolTeam(),
            )
        }
        for (goal in goals) {
            if (goal.isClaimed) continue
            val id = actorIds[goal] ?: continue
            val position = goal.body.position
            val size = goal.body.size
            contacts += RadarContact(
                actorId = id,
                approximateX = (position.x.raw + size.width.raw / 2f).toInt(),
                approximateY = (position.y.raw + size.height.raw / 2f).toInt(),
                kind = RadarContactKind.GOAL,
                team = when (goal.who) {
                    0 -> Team.PLAYER
                    1 -> Team.ENEMY
                    else -> Team.NEUTRAL
                },
            )
        }
        return contacts
    }

    fun dispose() {
        isDisposed = true
        kubriko.dispose()
    }

    /**
     * Kubriko applies additions made after `start()` on a background thread. Once `allActors` reflects them, the next
     * tick updates them too: the engine publishes its list of updated actors before `allActors`.
     */
    private fun awaitActorCount(expected: Int) = runBlocking {
        withTimeout(2_000) {
            while (actorManager.allActors.value.size != expected) {
                delay(2)
            }
        }
    }

    private fun stepWarps() {
        if (missionStatus != MissionStatus.IN_PROGRESS) return
        val tanks = actorManager.allActors.value.filterIsInstance<ServerTankActor>()
        for (warp in actorManager.allActors.value.filterIsInstance<ServerWarpActor>()) {
            if (warp.cooldownTicks > 0) {
                warp.cooldownTicks--
                continue
            }
            val size = warp.body.size
            val pos = warp.body.position
            val wx = (pos.x.raw + size.width.raw / 2f).toInt()
            val wy = (pos.y.raw + size.height.raw / 2f).toInt()
            val warpRadius = 15
            val trigger = tanks.firstOrNull { tank ->
                tank.armor > 0 && withinRadius(tank.positionX, tank.positionY, wx, wy, warpRadius)
            } ?: continue
            // Teleport tank
            trigger.teleportTo(warp.targetX, warp.targetY)
            warp.cooldownTicks = 30 // Prevent teleport loops
        }
    }

    private fun stepDestroyers() {
        if (missionStatus != MissionStatus.IN_PROGRESS) return
        for (destroyer in actorManager.allActors.value.filterIsInstance<ServerDestroyerActor>()) {
            if (destroyer.isFired) continue
            if (!destroyer.immediate) continue
            destroyer.isFired = true
            val size = destroyer.body.size
            val pos = destroyer.body.position
            val dx = (pos.x.raw + size.width.raw / 2f).toInt()
            val dy = (pos.y.raw + size.height.raw / 2f).toInt()
            val tanks = actorManager.allActors.value.filterIsInstance<ServerTankActor>()
            pendingEvents.addAll(AreaDamageResolver.resolve(
                tanks = tanks,
                x = dx,
                y = dy,
                radius = destroyer.radius,
                damage = 100,
                kind = ExplosionKind.ABOMB,
            ))
        }
    }

    private fun stepEnforcers() {
        val tanks = actorManager.allActors.value.filterIsInstance<ServerTankActor>()
        for (enforcer in actorManager.allActors.value.filterIsInstance<ServerEnforcerActor>()) {
            val size = enforcer.body.size
            val pos = enforcer.body.position
            val ex = (pos.x.raw + size.width.raw / 2f).toInt()
            val ey = (pos.y.raw + size.height.raw / 2f).toInt()
            for (tank in tanks) {
                if (tank.playerIndex >= 0) continue
                if (tank.armor <= 0) continue
                val targetGood = enforcer.good && tank.team == 0
                val targetBad = enforcer.bad && tank.team == 1
                if (!targetGood && !targetBad) continue
                if (!withinRadius(tank.positionX, tank.positionY, ex, ey, enforcer.radius)) continue
                // Enforce weapon on AI tank
                tank.applyEnforcedWeapon(enforcer.weapon)
            }
        }
    }

    private fun stepTrains() {
        for (train in actorManager.allActors.value.filterIsInstance<ServerTrainActor>()) {
            if (!train.alive || train.armor <= 0) continue
            // Simple forward movement (rails detection deferred to full rail system)
            train.positionX += 1
            train.body.position = SceneOffset(
                (train.positionX - TRAIN_FOOTPRINT / 2).toFloat().sceneUnit,
                (train.positionY - TRAIN_FOOTPRINT / 2).toFloat().sceneUnit,
            )
            // Wrap around
            if (train.positionX > worldWidthPixels) {
                train.positionX = 0
                train.body.position = SceneOffset(
                    (train.positionX - TRAIN_FOOTPRINT / 2).toFloat().sceneUnit,
                    (train.positionY - TRAIN_FOOTPRINT / 2).toFloat().sceneUnit,
                )
            }
        }
    }

    private fun stepZeppelins() {
        for (zep in actorManager.allActors.value.filterIsInstance<ServerZeppelinActor>()) {
            if (!zep.alive) continue
            zep.positionX += 10
            zep.body.position = SceneOffset(
                (zep.positionX - ZEPPELIN_FOOTPRINT / 2).toFloat().sceneUnit,
                (zep.positionY - ZEPPELIN_FOOTPRINT / 2).toFloat().sceneUnit,
            )
            if (zep.positionX > worldWidthPixels) {
                zep.positionX = -ZEPPELIN_FOOTPRINT
                zep.body.position = SceneOffset(
                    (zep.positionX - ZEPPELIN_FOOTPRINT / 2).toFloat().sceneUnit,
                    (zep.positionY - ZEPPELIN_FOOTPRINT / 2).toFloat().sceneUnit,
                )
            }
        }
    }

    private fun stepB52s() {
        for (b52 in actorManager.allActors.value.filterIsInstance<ServerB52Actor>()) {
            if (!b52.alive || b52.armor <= 0) continue
            b52.positionX += 5
            b52.body.position = SceneOffset(
                (b52.positionX - B52_FOOTPRINT / 2).toFloat().sceneUnit,
                (b52.positionY - B52_FOOTPRINT / 2).toFloat().sceneUnit,
            )
            b52.bombTimer++
            if (b52.bombTimer >= 500 && b52.bombCount < 10) {
                b52.bombTimer = 0
                b52.bombCount++
                val tanks = actorManager.allActors.value.filterIsInstance<ServerTankActor>()
                pendingEvents.addAll(AreaDamageResolver.resolve(
                    tanks = tanks,
                    x = b52.positionX,
                    y = b52.positionY,
                    radius = 60,
                    damage = 20,
                    kind = ExplosionKind.ABOMB,
                ))
            }
            if (b52.positionX > worldWidthPixels + B52_FOOTPRINT) {
                b52.alive = false
            }
        }
    }

    private fun collectProducts() {
        if (missionStatus != MissionStatus.IN_PROGRESS) return
        val tanks = actorManager.allActors.value.filterIsInstance<ServerTankActor>()
        for (product in actorManager.allActors.value.filterIsInstance<ServerProductActor>()) {
            if (product.isCollected) continue
            val size = product.body.size
            val pos = product.body.position
            val pcx = (pos.x.raw + size.width.raw / 2f).toInt()
            val pcy = (pos.y.raw + size.height.raw / 2f).toInt()
            val pickupRadius = 16
            val pickup = tanks.firstOrNull { tank ->
                tank.playerIndex >= 0 && tank.armor > 0 &&
                    withinRadius(tank.positionX, tank.positionY, pcx, pcy, pickupRadius)
            } ?: continue
            product.isCollected = true
            pendingEvents += GameEvent.ProductCollected(
                productActorId = actorIds[product] ?: 0L,
                tankActorId = actorIds[pickup]!!,
                price = product.price,
            )
        }
    }

    private fun collectFlags() {
        if (missionStatus != MissionStatus.IN_PROGRESS) return
        val tanks = actorManager.allActors.value.filterIsInstance<ServerTankActor>()
        for (flag in actorManager.allActors.value.filterIsInstance<ServerFlagActor>()) {
            // If flag is carried, follow carrier position
            if (flag.isCarried) {
                val carrierId = flag.carrierActorId ?: continue
                val carrier = tanks.find { actorIds[it] == carrierId }
                if (carrier == null || carrier.armor <= 0) {
                    // Carrier died — return flag to home
                    flag.isCarried = false
                    flag.carrierActorId = null
                    flag.body.position = SceneOffset(
                        (flag.homeX - 8).toFloat().sceneUnit,
                        (flag.homeY - 8).toFloat().sceneUnit,
                    )
                    pendingEvents += GameEvent.FlagReturned(flagActorId = actorIds[flag] ?: 0L)
                } else {
                    // Follow carrier
                    flag.body.position = SceneOffset(
                        (carrier.positionX - 8).toFloat().sceneUnit,
                        (carrier.positionY - 8).toFloat().sceneUnit,
                    )
                }
                continue
            }
            // Check for pickup: friendly tank enters flag radius
            val size = flag.body.size
            val pos = flag.body.position
            val fcx = (pos.x.raw + size.width.raw / 2f).toInt()
            val fcy = (pos.y.raw + size.height.raw / 2f).toInt()
            val pickupRadius = 16
            val pickup = tanks.firstOrNull { tank ->
                tank.playerIndex >= 0 && tank.armor > 0 &&
                    withinRadius(tank.positionX, tank.positionY, fcx, fcy, pickupRadius)
            } ?: continue
            // Pick up the flag
            flag.isCarried = true
            flag.carrierActorId = actorIds[pickup]
            pendingEvents += GameEvent.FlagCaptured(
                flagActorId = actorIds[flag] ?: 0L,
                tankActorId = actorIds[pickup]!!,
            )
        }
    }

    private fun collectGoals() {
        if (missionStatus != MissionStatus.IN_PROGRESS) return
        val tanks = actorManager.allActors.value.filterIsInstance<ServerTankActor>()
        for (actor in actorManager.allActors.value) {
            if (actor !is ServerGoalActor || actor.isClaimed) continue
            val position = actor.body.position
            val size = actor.body.size
            val cx = (position.x.raw + size.width.raw / 2f).toInt()
            val cy = (position.y.raw + size.height.raw / 2f).toInt()
            // Legacy control_goal(): who=0 is reached by a good tank, who=1 by an evil tank,
            // who=2 by a train; who=3 (a linked object) is not modelled yet.
            val reached = when (actor.who) {
                0 -> tanks.any { it.team == 0 && it.armor > 0 && withinRadius(it.positionX, it.positionY, cx, cy, actor.radius) }
                1 -> tanks.any { it.team == 1 && it.armor > 0 && withinRadius(it.positionX, it.positionY, cx, cy, actor.radius) }
                2 -> actorManager.allActors.value.filterIsInstance<ServerTrainActor>()
                    .any { it.alive && withinRadius(it.positionX, it.positionY, cx, cy, actor.radius) }
                else -> false
            }
            if (!reached) continue
            actor.isClaimed = true
            if (actor.contribution > 0) {
                missionCaptures++
                missionScore += 500
            }
            modifyGoalCounter(actor.contribution)
        }
    }

    private fun drainTankLifecycleEvents() {
        for (actor in actorManager.allActors.value) {
            if (actor !is ServerTankActor) continue
            val id = actorIds[actor] ?: continue
            val events = actor.drainLifecycleEvents(id)
            if (events.isNotEmpty()) {
                pendingEvents.addAll(events)
                // Track kills: when an enemy tank is destroyed, count it
                for (e in events) {
                    if (e !is GameEvent.TankDestroyed) continue
                    if (actor.team == 1) {
                        missionKills++
                        missionScore += 1000
                    }
                    onTankDestroyed(actor)
                }
            }
        }
    }

    /**
     * Legacy hit_tank(): a destroyed tank applies its goal contribution, except that losing the
     * last human-drivable tank costs the whole mission (-100).
     */
    private fun onTankDestroyed(tank: ServerTankActor) {
        if (tank.lives > 0) return // respawns at its pad; the goal counter only counts final deaths
        val isHumanTank = tank.playerIndex >= 0 || (!tank.computer && tank.team == 0)
        if (isHumanTank) {
            val othersAlive = actorManager.allActors.value.filterIsInstance<ServerTankActor>().any { other ->
                other !== tank && other.armor > 0 && (other.playerIndex >= 0 || (!other.computer && other.team == 0))
            }
            if (!othersAlive) {
                lossReason = "ALL_LIVES_LOST"
                modifyGoalCounter(-100)
                return
            }
        }
        modifyGoalCounter(tank.goalContribution)
        tank.goalContribution = 0
    }

    private fun drainTurretDestructions() {
        for (turret in actorManager.allActors.value) {
            if (turret !is ServerTurretActor || !turret.drainDestroyed()) continue
            val id = actorIds[turret] ?: continue
            pendingEvents += GameEvent.TurretDestroyed(id)
            pendingEvents += GameEvent.Explosion(turret.positionX, turret.positionY, LEGACY_TILE_SIZE, ExplosionKind.ROCKET)
            pendingEvents += GameEvent.Sound(com.tankarena.protocol.snapshot.SoundKind.EXPLODE, turret.positionX, turret.positionY)
            if (turret.team == 1) {
                missionKills++
                missionScore += 500
            }
            modifyGoalCounter(turret.goalContribution)
            turret.goalContribution = 0
        }
    }

    /** Legacy modify_goal_counter(): positive percentages feed the good counter, negative the bad one. */
    private fun modifyGoalCounter(percentage: Int) {
        if (mode == MatchMode.DUAL || missionStatus != MissionStatus.IN_PROGRESS) return
        when {
            percentage > 0 -> goalGood = (goalGood + percentage).coerceAtMost(100)
            percentage < 0 -> goalBad = (goalBad - percentage).coerceAtMost(100)
        }
    }

    /**
     * Legacy control.c: the camera follows another living tank of the player's group, and when
     * the whole group is gone for good control moves to another group of human-drivable tanks.
     */
    private fun handOverDeadPlayers() {
        val tanks = allTanks
        for ((playerIndex, primary) in tanksByPlayerIndex.toMap()) {
            if (primary.armor > 0 && primary.playerIndex == playerIndex) continue
            val group = tanks.filter { it.playerIndex == playerIndex }
            group.firstOrNull { it.armor > 0 }?.let {
                tanksByPlayerIndex[playerIndex] = it
                continue
            }
            if (group.any { it.lives > 0 }) continue // a pad tank is waiting to respawn
            if (mode == MatchMode.DUAL || missionStatus != MissionStatus.IN_PROGRESS) continue
            val nextId = tanks
                .filter { it.armor > 0 && it.playerIndex < 0 && !it.computer && it.team == 0 && it.controllerId >= 0 }
                .minOfOrNull { it.controllerId }
                ?: continue
            transferControl(playerIndex, nextId, tanks)
        }
    }

    /** Legacy F1–F10 in single / co-operative play. */
    private fun switchToTank(playerIndex: Int, controllerId: Int) {
        if (mode == MatchMode.DUAL || missionStatus != MissionStatus.IN_PROGRESS) return
        val current = tanksByPlayerIndex[playerIndex] ?: return
        if (current.controllerId == controllerId && current.armor > 0) return
        val tanks = allTanks
        val available = tanks.any {
            it.controllerId == controllerId && it.armor > 0 && it.playerIndex < 0 && !it.computer && it.team == 0
        }
        if (available) transferControl(playerIndex, controllerId, tanks)
    }

    private fun transferControl(playerIndex: Int, controllerId: Int, tanks: List<ServerTankActor>) {
        for (tank in tanks) {
            if (tank.playerIndex == playerIndex) tank.playerIndex = -1
        }
        val group = tanks.filter { it.controllerId == controllerId && it.playerIndex < 0 && !it.computer && it.team == 0 }
        group.forEach { it.playerIndex = playerIndex }
        val primary = group.firstOrNull { it.armor > 0 } ?: return
        tanksByPlayerIndex[playerIndex] = primary
        pendingEvents += GameEvent.ControlSwitched(playerIndex, actorIds[primary] ?: 0L)
    }

    private fun evaluateMission() {
        if (missionStatus != MissionStatus.IN_PROGRESS) return
        val all = actorManager.allActors.value
        val tanks = all.filterIsInstance<ServerTankActor>()

        if (mode == MatchMode.DUAL) {
            // Player vs player: the last player standing wins; goal counters are ignored.
            // Legacy control_player(): a player loses as soon as one of their pads runs out of lives.
            val p0Tanks = tanks.filter { it.playerIndex == 0 }
            val p1Tanks = tanks.filter { it.playerIndex == 1 }
            if (p0Tanks.isNotEmpty() && p1Tanks.isNotEmpty()) {
                val p0Dead = p0Tanks.any { it.armor <= 0 && it.lives <= 0 }
                val p1Dead = p1Tanks.any { it.armor <= 0 && it.lives <= 0 }
                if (p0Dead && !p1Dead) {
                    endMission(false, "PLAYER_ELIMINATED")
                } else if (p1Dead && !p0Dead) {
                    endMission(true, "OPPONENT_ELIMINATED")
                }
            }
            return
        }

        // Legacy modify_goal_counter(): 100% good wins, 100% bad loses.
        if (goalGood >= 100) {
            endMission(true, "GOALS_CAPTURED")
            return
        }
        if (goalBad >= 100) {
            endMission(false, lossReason)
            return
        }

        // Maps whose object contributions cannot reach 100% on their own (structure goals are
        // not modelled yet, and hand-built fixtures carry none) fall back to "destroy everything".
        if (!goalCounterReachable) {
            val enemyTanks = tanks.filter { it.team == 1 }
            val enemyTurrets = all.filterIsInstance<ServerTurretActor>().filter { it.team == 1 }
            val hasEnemies = enemyTanks.isNotEmpty() || enemyTurrets.isNotEmpty()
            if (hasEnemies && enemyTanks.all { it.armor <= 0 && it.lives <= 0 } && enemyTurrets.all { !it.isAlive }) {
                endMission(true, "ALL_ENEMIES_ELIMINATED")
                return
            }
        }

        val humanTanks = tanks.filter { it.playerIndex >= 0 || (!it.computer && it.team == 0) }
        if (humanTanks.isNotEmpty() && humanTanks.all { it.armor <= 0 && it.lives <= 0 }) {
            endMission(false, "ALL_LIVES_LOST")
        }
    }

    /**
     * End the mission with a win/loss result and emit MissionResult event.
     */
    private fun endMission(won: Boolean, reason: String) {
        missionStatus = if (won) MissionStatus.WON else MissionStatus.LOST
        if (won) {
            pendingEvents += GameEvent.MissionWon(playerId = 0, reason = reason)
        } else {
            pendingEvents += GameEvent.MissionLost(playerId = 0, reason = reason)
        }
        // Emit MissionResult once
        if (!missionResultEmitted) {
            missionResultEmitted = true
            pendingEvents += GameEvent.MissionResult(
                won = won,
                reason = reason,
                score = missionScore,
                kills = missionKills,
                captures = missionCaptures,
                ticks = currentTick,
            )
        }
    }

    private fun withinRadius(ax: Int, ay: Int, bx: Int, by: Int, radius: Int): Boolean {
        val dx = (ax - bx).toLong()
        val dy = (ay - by).toLong()
        val r = radius.toLong()
        return dx * dx + dy * dy <= r * r
    }

    private fun stepAiTanks() {
        val all = actorManager.allActors.value
        val tanks = all.filterIsInstance<ServerTankActor>()
        if (tanks.isEmpty()) return
        for (ai in tanks) {
            if (ai.playerIndex >= 0 || !ai.computer) continue
            if (ai.armor <= 0) continue
            if (!updateAiActivation(ai, tanks)) {
                ai.applyIntent(com.tankarena.input.PlayerIntentFrame())
                continue
            }
            // Legacy attack_tanks == 0 (cars, buses, trucks): never hunts tanks.
            val target = if (ai.aiAttackTanks > 0) pickAiTarget(ai, tanks) else null
            if (target != null) {
                ai.applyIntent(computeAiIntent(ai, target))
            } else {
                // No target — patrol waypoints if mode supports it
                val patrolDir = aiModeDispatcher.getPatrolDirection(ai.positionX, ai.positionY)
                if (patrolDir >= 0) {
                    val turn = turnDelta(ai.bodyDirection, patrolDir)
                    ai.applyIntent(com.tankarena.input.PlayerIntentFrame(
                        forward = ai.bodyDirection == patrolDir,
                        turnLeft = turn < 0,
                        turnRight = turn > 0,
                    ))
                } else {
                    ai.applyIntent(com.tankarena.input.PlayerIntentFrame())
                }
            }
        }
    }

    /**
     * Legacy determine_new_target(): a computer tank is dormant until a tank from its activator
     * side comes within its activation radius, and falls dormant again unless `stay_active`.
     */
    private fun updateAiActivation(ai: ServerTankActor, tanks: List<ServerTankActor>): Boolean {
        if (ai.aiActivationRadius < 0) return true
        val wakerTeam = if (ai.aiActivator == 0) 0 else 1
        val inRange = tanks.any { other ->
            other !== ai && other.armor > 0 && other.team == wakerTeam &&
                withinRadius(other.positionX, other.positionY, ai.positionX, ai.positionY, ai.aiActivationRadius)
        }
        ai.aiActive = inRange || (ai.aiStayActive && ai.aiActive)
        return ai.aiActive
    }

    private val pathfinder: TilePathfinder by lazy {
        TilePathfinder(mapMetadata.widthTiles, mapMetadata.heightTiles, navigationLayer())
    }

    /** Walls plus terrain that hurts on contact, so computer tanks route around pits and lava. */
    private fun navigationLayer(): List<Int> = solidLayer.mapIndexed { index, solid ->
        if (solid >= 0) return@mapIndexed solid
        val material = terrainGrid.at(index % mapMetadata.widthTiles, index / mapMetadata.widthTiles).material
        if (material in DEADLY_TERRAIN) 0 else -1
    }

    private val lineOfSight: LineOfSight by lazy {
        LineOfSight(mapMetadata.widthTiles, mapMetadata.heightTiles, solidLayer)
    }

    private lateinit var aiModeDispatcher: AiModeDispatcher

    private class AiPathCache(var path: List<Pair<Int, Int>> = emptyList(), var tick: Long = -1L)

    private val aiPathCaches: MutableMap<ServerTankActor, AiPathCache> = IdentityHashMap()

    private fun pickAiTarget(
        self: ServerTankActor,
        tanks: List<ServerTankActor>,
    ): ServerTankActor? {
        var best: ServerTankActor? = null
        var bestDistSq = Long.MAX_VALUE
        for (candidate in tanks) {
            if (candidate === self) continue
            if (candidate.armor <= 0) continue
            if (candidate.team == self.team) continue
            val dx = (candidate.positionX - self.positionX).toLong()
            val dy = (candidate.positionY - self.positionY).toLong()
            val distSq = dx * dx + dy * dy
            if (distSq < bestDistSq) {
                best = candidate
                bestDistSq = distSq
            }
        }
        return best
    }

    private fun computeAiIntent(
        self: ServerTankActor,
        target: ServerTankActor,
    ): com.tankarena.input.PlayerIntentFrame {
        val dx = target.positionX - self.positionX
        val dy = target.positionY - self.positionY
        val distSq = dx.toLong() * dx + dy.toLong() * dy

        // Recompute this tank's path every 15 ticks or when it has none
        val cache = aiPathCaches.getOrPut(self) { AiPathCache() }
        if ((currentTick - cache.tick) >= 15 || cache.path.isEmpty()) {
            cache.path = pathfinder.findPath(self.positionX, self.positionY, target.positionX, target.positionY)
            cache.tick = currentTick
        }
        val cachedPath = cache.path

        // Body direction: follow path if available, otherwise go directly
        val bodyDesired: Int
        if (cachedPath.isNotEmpty()) {
            val pathDir = pathfinder.directionToNextStep(self.positionX, self.positionY, cachedPath)
            bodyDesired = if (pathDir >= 0) pathDir else com.tankarena.core.LegacyDirections.fromFacing(
                dx.coerceIn(-1, 1),
                dy.coerceIn(-1, 1),
            )
        } else {
            bodyDesired = com.tankarena.core.LegacyDirections.fromFacing(
                dx.coerceIn(-1, 1),
                dy.coerceIn(-1, 1),
            )
        }

        // Turret aims directly at target
        val turretDesired = com.tankarena.core.LegacyDirections.fromFacing(
            dx.coerceIn(-1, 1),
            dy.coerceIn(-1, 1),
        )

        val fireRangeSq = (320L * 320L)
        val visible = lineOfSight.hasLineOfSight(self.positionX, self.positionY, target.positionX, target.positionY)
        // A hull-mounted gun (legacy rule_fire() uses motion.dir unless MAIN_TURRET) is aimed by
        // turning the whole tank toward a target in range.
        val hullAims = self.turretMode != TurretMode.TURRET && visible && distSq <= fireRangeSq
        // No route (target across a pit, lava or walls): hold position rather than drive into it.
        val canReach = cachedPath.isNotEmpty() || distSq <= LEGACY_TILE_SIZE.toLong() * LEGACY_TILE_SIZE * 4
        val hullDesired = if (hullAims) turretDesired else bodyDesired
        val bodyTurn = if (canReach || hullAims) turnDelta(self.bodyDirection, hullDesired) else 0
        val turretTurn = turnDelta(self.turretDirection, turretDesired)
        val bodyAligned = canReach && self.bodyDirection == hullDesired
        val gunAligned = self.aimDirection == turretDesired
        val fire = gunAligned && distSq <= fireRangeSq && self.primaryCooldownTicks == 0 && visible
        return com.tankarena.input.PlayerIntentFrame(
            forward = bodyAligned,
            turnLeft = bodyTurn < 0,
            turnRight = bodyTurn > 0,
            aimLeft = turretTurn < 0,
            aimRight = turretTurn > 0,
            firePrimary = fire,
        )
    }

    private fun turnDelta(current: Int, desired: Int): Int {
        val c = ((current % 16) + 16) % 16
        val d = ((desired % 16) + 16) % 16
        if (c == d) return 0
        val diff = (d - c + 16) % 16
        return if (diff <= 8) +1 else -1
    }

    private fun stepTurrets() {
        val tanks = actorManager.allActors.value.filterIsInstance<ServerTankActor>()
        if (tanks.isEmpty()) return
        for (actor in actorManager.allActors.value) {
            if (actor is ServerTurretActor) actor.step(tanks)
        }
    }

    /**
     * Apply terrain effects (lava damage, pit kill) to all tanks, and set the per-tank terrain
     * speed multiplier for the next motion step. Water only slows a tank down (its speed is in
     * the picture table); the legacy game never kills a tank for driving into it.
     */
    private fun applyTerrainEffects() {
        val tanks = actorManager.allActors.value.filterIsInstance<ServerTankActor>()
        for (tank in tanks) {
            if (tank.armor <= 0) continue
            val tileX = tank.positionX / LEGACY_TILE_SIZE
            val tileY = tank.positionY / LEGACY_TILE_SIZE
            val tile = terrainGrid.at(tileX, tileY)

            tank.setTerrainSpeedMultiplier(tile.speedMultiplier)

            // Legacy control_tank(): HT_LAVA hits for 2 every 10 ticks while the tank stays in lava.
            tank.stepLava(inLava = tile.material == TerrainMaterial.LAVA)

            val subX = (tank.positionX % LEGACY_TILE_SIZE + LEGACY_TILE_SIZE) % LEGACY_TILE_SIZE
            val subY = (tank.positionY % LEGACY_TILE_SIZE + LEGACY_TILE_SIZE) % LEGACY_TILE_SIZE
            if (inPit(tile, subX, subY)) {
                tank.queueDamage(999) // Instant kill
            }
        }
    }

    /**
     * Legacy `in_pit()` (src/check/pit.c): the centre tile of a pit always swallows a tank, while
     * an edge or corner tile only does so once the tank's centre is past the rim on the side
     * facing the pit. [subX]/[subY] are the tank centre's offset inside its tile.
     */
    private fun inPit(tile: TerrainTile, subX: Int, subY: Int): Boolean {
        val shape = tile.pitShape ?: return false
        val (low, high) = when (tile.material) {
            TerrainMaterial.PIT_BIG -> 8 to 24
            TerrainMaterial.PIT_SMALL -> 14 to 18
            else -> return false
        }
        return when (shape) {
            PitShape.CENTER -> true
            PitShape.TOP -> subY > low
            PitShape.BOTTOM -> subY < high
            PitShape.LEFT -> subX > low
            PitShape.RIGHT -> subX < high
            PitShape.TOP_LEFT -> subX > low && subY > low
            PitShape.TOP_RIGHT -> subX < high && subY > low
            PitShape.BOTTOM_LEFT -> subX > low && subY < high
            PitShape.BOTTOM_RIGHT -> subX < high && subY < high
        }
    }

    private fun flushOutOfBoundsProjectiles() {
        for (actor in actorManager.allActors.value) {
            if (actor is ServerProjectileActor) {
                actor.markOutOfBoundsIfNeeded(worldWidthPixels, worldHeightPixels)
            }
        }
    }

    private fun removeDeadProjectiles() {
        val doomed = actorManager.allActors.value
            .filterIsInstance<ServerProjectileActor>()
            .filter { it.isDead }
        if (doomed.isEmpty()) return
        val expected = actorManager.allActors.value.size - doomed.size
        actorManager.remove(doomed)
        awaitActorCount(expected)
        for (projectile in doomed) {
            actorIds.remove(projectile)
        }
    }

    private fun flushOutOfBoundsRockets() {
        for (actor in actorManager.allActors.value) {
            if (actor is ServerRocketActor) actor.markOutOfBoundsIfNeeded(worldWidthPixels, worldHeightPixels)
        }
    }

    /** Legacy tank_write_marker(): which marker picture to show, and where. */
    private fun lockMarkerFor(tank: ServerTankActor): LockMarker? {
        if (tank.lockCameraTicks <= 0) return null
        val state = when {
            !tank.lockActive -> LockMarkerState.LAUNCHED
            mode != MatchMode.DUAL && !withinRadius(tank.positionX, tank.positionY, tank.lockX, tank.lockY, LOCK_OUT_OF_RANGE_PX) ->
                LockMarkerState.OUT_OF_RANGE
            rocketTargetAt(tank.lockX, tank.lockY, tank) != null -> LockMarkerState.ON_TARGET
            else -> LockMarkerState.SEARCHING
        }
        return LockMarker(tank.lockX, tank.lockY, state)
    }

    /**
     * Legacy nearest_target(): the closest live tank or turret within 15 px of ([x], [y]),
     * other than [owner].
     */
    private fun rocketTargetAt(x: Int, y: Int, owner: ServerTankActor): RocketTarget? {
        var best: RocketTarget? = null
        var bestDistSq = ROCKET_TARGET_RADIUS_PX.toLong() * ROCKET_TARGET_RADIUS_PX
        for (actor in actorManager.allActors.value) {
            val (ax, ay, target) = when {
                actor is ServerTankActor && actor !== owner && actor.armor > 0 ->
                    Triple(actor.positionX, actor.positionY, RocketTarget { actor.takeIf { it.armor > 0 }?.let { it.positionX to it.positionY } })
                actor is ServerTurretActor && actor.isAlive ->
                    Triple(actor.positionX, actor.positionY, RocketTarget { actor.takeIf { it.isAlive }?.let { it.positionX to it.positionY } })
                else -> continue
            }
            val dx = (ax - x).toLong()
            val dy = (ay - y).toLong()
            val distSq = dx * dx + dy * dy
            if (distSq < bestDistSq) {
                best = target
                bestDistSq = distSq
            }
        }
        return best
    }

    private fun drainRocketExplosions() {
        val tanks = actorManager.allActors.value.filterIsInstance<ServerTankActor>()
        for (actor in actorManager.allActors.value) {
            if (actor !is ServerRocketActor) continue
            val explosion = actor.drainExplosion() ?: continue
            pendingEvents.addAll(AreaDamageResolver.resolve(
                tanks = tanks,
                x = explosion.x,
                y = explosion.y,
                radius = ROCKET_BLAST_RADIUS_PX,
                damage = actor.damage,
                kind = ExplosionKind.ROCKET,
                owner = actor.ownerRef,
                ownerImmune = true,
                turrets = actorManager.allActors.value.filterIsInstance<ServerTurretActor>(),
            ))
        }
    }

    private fun removeDeadRockets() {
        val doomed = actorManager.allActors.value.filterIsInstance<ServerRocketActor>().filter { it.isDead }
        if (doomed.isEmpty()) return
        val expected = actorManager.allActors.value.size - doomed.size
        actorManager.remove(doomed)
        awaitActorCount(expected)
        for (rocket in doomed) actorIds.remove(rocket)
    }

    private fun drainRocketRequests() {
        val spawns = mutableListOf<ServerRocketActor>()
        for (tank in allTanks) {
            val request = tank.drainRocketRequest() ?: continue
            spawns += rocketFromRequest(tank, request)
        }
        if (spawns.isEmpty()) return
        val expected = actorManager.allActors.value.size + spawns.size
        actorManager.add(spawns)
        awaitActorCount(expected)
        for (rocket in spawns) actorIds.getOrPut(rocket) { nextActorId++ }
    }

    private fun rocketFromRequest(
        owner: ServerTankActor,
        request: ServerTankActor.RocketRequest,
    ): ServerRocketActor {
        val ownerActorId = actorIds[owner] ?: 0L
        val rocket = ServerRocketActor(
            ServerRocketActor.State(
                body = PointBody(
                    initialPosition = SceneOffset(
                        request.originX.toFloat().sceneUnit,
                        request.originY.toFloat().sceneUnit,
                    ),
                ),
                ownerActorId = ownerActorId,
                damage = request.damage,
                targetX = request.targetX,
                targetY = request.targetY,
            ),
        )
        rocket.ownerRef = owner
        rocket.target = rocketTargetAt(request.targetX, request.targetY, owner)

        // Emit sound event for rocket launch
        pendingEvents += GameEvent.Sound(
            kind = com.tankarena.protocol.snapshot.SoundKind.ROCKET,
            x = request.originX,
            y = request.originY,
        )

        return rocket
    }

    private fun flushOutOfBoundsMortars() {
        for (actor in actorManager.allActors.value) {
            if (actor is ServerMortarActor) actor.markOutOfBoundsIfNeeded(worldWidthPixels, worldHeightPixels)
        }
    }

    private fun drainMortarExplosions() {
        val tanks = actorManager.allActors.value.filterIsInstance<ServerTankActor>()
        for (actor in actorManager.allActors.value) {
            if (actor !is ServerMortarActor) continue
            val explosion = actor.drainExplosion() ?: continue
            pendingEvents.addAll(AreaDamageResolver.resolve(
                tanks = tanks,
                x = explosion.x,
                y = explosion.y,
                radius = explosion.radius,
                damage = explosion.damage,
                kind = ExplosionKind.MORTAR,
                owner = actor.ownerRef,
                turrets = actorManager.allActors.value.filterIsInstance<ServerTurretActor>(),
            ))
        }
    }

    private fun removeDeadMortars() {
        val doomed = actorManager.allActors.value.filterIsInstance<ServerMortarActor>().filter { it.isDead }
        if (doomed.isEmpty()) return
        val expected = actorManager.allActors.value.size - doomed.size
        actorManager.remove(doomed)
        awaitActorCount(expected)
        for (mortar in doomed) actorIds.remove(mortar)
    }

    private fun drainMortarRequests() {
        val spawns = mutableListOf<ServerMortarActor>()
        for (tank in allTanks) {
            val request = tank.drainMortarRequest() ?: continue
            spawns += mortarFromRequest(tank, request)
        }
        if (spawns.isEmpty()) return
        val expected = actorManager.allActors.value.size + spawns.size
        actorManager.add(spawns)
        awaitActorCount(expected)
        for (mortar in spawns) actorIds.getOrPut(mortar) { nextActorId++ }
    }

    private fun mortarFromRequest(
        owner: ServerTankActor,
        request: ServerTankActor.MortarRequest,
    ): ServerMortarActor {
        val ownerActorId = actorIds[owner] ?: 0L
        val mortar = ServerMortarActor(
            ServerMortarActor.State(
                body = PointBody(
                    initialPosition = SceneOffset(
                        request.originX.toFloat().sceneUnit,
                        request.originY.toFloat().sceneUnit,
                    ),
                ),
                ownerActorId = ownerActorId,
                damage = request.damage,
                maxRadius = request.maxRadius,
                velocityX = request.velocityX,
                velocityY = request.velocityY,
            ),
        )
        mortar.ownerRef = owner
        
        // Emit sound event for mortar launch
        pendingEvents += GameEvent.Sound(
            kind = com.tankarena.protocol.snapshot.SoundKind.MORTAR,
            x = request.originX,
            y = request.originY,
        )
        
        return mortar
    }

    private fun triggerMineContacts() {
        val mines = actorManager.allActors.value.filterIsInstance<ServerMineActor>().filter { it.isActive && !it.isDead }
        if (mines.isEmpty()) return
        val tanks = actorManager.allActors.value.filterIsInstance<ServerTankActor>().filter { it.armor > 0 }
        for (mine in mines) {
            val triggerRadius = mine.radius + SERVER_TANK_HALF
            val victim = tanks.firstOrNull { tank ->
                withinRadius(tank.positionX, tank.positionY, mine.positionX, mine.positionY, triggerRadius)
            } ?: continue
            mine.detonateOn(victim)
        }
    }

    private fun drainMineDetonations() {
        val tanks = actorManager.allActors.value.filterIsInstance<ServerTankActor>()
        for (actor in actorManager.allActors.value) {
            if (actor !is ServerMineActor) continue
            val det = actor.drainDetonation() ?: continue
            pendingEvents.addAll(AreaDamageResolver.resolve(
                tanks = tanks,
                x = det.x,
                y = det.y,
                radius = actor.radius,
                damage = actor.damage,
                kind = ExplosionKind.MINE,
            ))
        }
    }

    private fun removeDeadMines() {
        val doomed = actorManager.allActors.value
            .filterIsInstance<ServerMineActor>()
            .filter { it.isDead }
        if (doomed.isEmpty()) return
        val expected = actorManager.allActors.value.size - doomed.size
        actorManager.remove(doomed)
        awaitActorCount(expected)
        for (mine in doomed) actorIds.remove(mine)
    }

    private fun drainMineRequests() {
        val spawns = mutableListOf<ServerMineActor>()
        for (tank in allTanks) {
            val request = tank.drainMineRequest() ?: continue
            spawns += mineFromRequest(tank, request)
        }
        if (spawns.isEmpty()) return
        val expected = actorManager.allActors.value.size + spawns.size
        actorManager.add(spawns)
        awaitActorCount(expected)
        for (mine in spawns) actorIds.getOrPut(mine) { nextActorId++ }
    }

    private fun mineFromRequest(
        owner: ServerTankActor,
        request: ServerTankActor.MineRequest,
    ): ServerMineActor {
        val ownerActorId = actorIds[owner] ?: 0L
        val mine = ServerMineActor(
            ServerMineActor.State(
                body = PointBody(
                    initialPosition = SceneOffset(
                        request.originX.toFloat().sceneUnit,
                        request.originY.toFloat().sceneUnit,
                    ),
                ),
                ownerActorId = ownerActorId,
                damage = request.damage,
                radius = request.radius,
            ),
        )
        // Legacy mines are dropped silently; SoundKind.MINE is the detonation.
        return mine
    }

    private fun drainFireRequests() {
        val spawns = mutableListOf<ServerProjectileActor>()
        for (tank in allTanks) {
            val request = tank.drainFireRequest() ?: continue
            spawns += projectileFromRequest(tank, request)
        }
        for (actor in actorManager.allActors.value) {
            if (actor is ServerTurretActor) {
                val request = actor.drainFireRequest() ?: continue
                spawns += projectileFromTurretRequest(actor, request)
            }
        }
        if (spawns.isEmpty()) return
        val expected = actorManager.allActors.value.size + spawns.size
        actorManager.add(spawns)
        awaitActorCount(expected)
        for (projectile in spawns) {
            actorIds.getOrPut(projectile) { nextActorId++ }
        }
    }

    private fun projectileFromRequest(
        owner: ServerTankActor,
        request: ServerTankActor.FireRequest,
    ): ServerProjectileActor {
        val ownerActorId = actorIds[owner] ?: 0L
        val projectile = ServerProjectileActor(
            ServerProjectileActor.State(
                body = PointBody(
                    initialPosition = SceneOffset(
                        request.originX.toFloat().sceneUnit,
                        request.originY.toFloat().sceneUnit,
                    ),
                ),
                ownerActorId = ownerActorId,
                ownerKind = ProjectileOwnerKind.TANK,
                damage = request.damage,
                velocityX = request.velocityX,
                velocityY = request.velocityY,
                ttlTicks = request.ttlTicks,
                weaponKind = request.weaponKind,
            ),
        )
        projectile.ownerRef = owner
        
        // Emit sound event for firing
        pendingEvents += GameEvent.Sound(
            kind = soundKindForWeapon(request.weaponKind),
            x = request.originX,
            y = request.originY,
        )
        
        return projectile
    }

    private fun projectileFromTurretRequest(
        owner: ServerTurretActor,
        request: ServerTurretActor.FireRequest,
    ): ServerProjectileActor {
        val ownerActorId = actorIds[owner] ?: 0L
        val projectile = ServerProjectileActor(
            ServerProjectileActor.State(
                body = PointBody(
                    initialPosition = SceneOffset(
                        request.originX.toFloat().sceneUnit,
                        request.originY.toFloat().sceneUnit,
                    ),
                ),
                ownerActorId = ownerActorId,
                ownerKind = ProjectileOwnerKind.TURRET,
                damage = request.damage,
                velocityX = request.velocityX,
                velocityY = request.velocityY,
            ),
        )
        projectile.ownerTurretRef = owner

        // Emit sound event for turret firing
        pendingEvents += GameEvent.Sound(
            kind = com.tankarena.protocol.snapshot.SoundKind.MAIN,
            x = request.originX,
            y = request.originY,
        )
        
        return projectile
    }

    /**
     * Map weapon kind constants to sound kind enum.
     */
    private fun soundKindForWeapon(weaponKind: Int): com.tankarena.protocol.snapshot.SoundKind {
        return when (weaponKind) {
            WEAPON_MAIN -> com.tankarena.protocol.snapshot.SoundKind.MAIN
            WEAPON_CHAIN -> com.tankarena.protocol.snapshot.SoundKind.CHAIN
            WEAPON_ROCKET -> com.tankarena.protocol.snapshot.SoundKind.ROCKET
            WEAPON_MORTAR -> com.tankarena.protocol.snapshot.SoundKind.MORTAR
            else -> com.tankarena.protocol.snapshot.SoundKind.MAIN
        }
    }

    private fun buildSnapshot(drainEvents: Boolean): WorldSnapshot {
        val states = actorManager.allActors.value.mapNotNull { actor ->
            val id = actorIds[actor] ?: return@mapNotNull null
            projectToWireState(actor, id)
        }
        val events = when {
            pendingEvents.isEmpty() -> emptyList()
            drainEvents -> pendingEvents.toList().also { pendingEvents.clear() }
            else -> pendingEvents.toList()
        }
        return WorldSnapshot(tick = currentTick, actors = states, events = events)
    }

    private fun projectToWireState(actor: Actor, id: Long): ActorState? {
        return when (actor) {
            is ServerWallActor -> {
                val size = actor.body.size
                val position = actor.body.position
                WallState(
                    actorId = id,
                    x = (position.x.raw + size.width.raw / 2f).toInt(),
                    y = (position.y.raw + size.height.raw / 2f).toInt(),
                    width = size.width.raw.toInt(),
                    height = size.height.raw.toInt(),
                )
            }

            is ServerTankActor -> {
                TankState(
                    actorId = id,
                    team = actor.team.toProtocolTeam(),
                    tankType = actor.tankType,
                    x = actor.positionX,
                    y = actor.positionY,
                    vx = actor.velocityX,
                    vy = actor.velocityY,
                    bodyDirection = actor.bodyDirection,
                    turretDirection = actor.turretDirection,
                    armor = actor.armor,
                    alive = actor.armor > 0,
                    invulnerable = actor.invulnerableTicks > 0,
                    controlled = actor.playerIndex >= 0,
                    primaryCooldownTicks = actor.primaryCooldownTicks,
                    currentWeapon = actor.currentWeapon,
                    chainAmmo = actor.chainAmmo,
                    mineAmmo = actor.mineAmmo,
                    rocketAmmo = actor.rocketAmmo,
                    mortarAmmo = actor.mortarAmmo,
                )
            }

            is ServerTurretActor -> {
                val size = actor.body.size
                val position = actor.body.position
                TurretState(
                    actorId = id,
                    team = actor.team.toProtocolTeam(),
                    x = (position.x.raw + size.width.raw / 2f).toInt(),
                    y = (position.y.raw + size.height.raw / 2f).toInt(),
                    turretDirection = actor.turretDirection,
                    alive = actor.armor > 0,
                    primaryCooldownTicks = actor.cooldownTicks,
                )
            }

            is ServerGoalActor -> {
                val size = actor.body.size
                val position = actor.body.position
                GoalState(
                    actorId = id,
                    team = when (actor.who) {
                        0 -> Team.PLAYER
                        1 -> Team.ENEMY
                        else -> Team.NEUTRAL
                    },
                    x = (position.x.raw + size.width.raw / 2f).toInt(),
                    y = (position.y.raw + size.height.raw / 2f).toInt(),
                    captured = actor.isClaimed,
                )
            }

            is ServerProjectileActor -> ProjectileState(
                actorId = id,
                ownerId = actor.ownerActorId,
                x = actor.positionX,
                y = actor.positionY,
                vx = actor.velocityX,
                vy = actor.velocityY,
                ownerKind = actor.ownerKind,
                kind = if (actor.weaponKind == WEAPON_CHAIN) ProjectileKind.CHAIN else ProjectileKind.BULLET,
            )

            is ServerRocketActor -> ProjectileState(
                actorId = id,
                ownerId = actor.ownerActorId,
                x = actor.positionX.toInt(),
                y = actor.positionY.toInt(),
                kind = ProjectileKind.ROCKET,
            )

            is ServerMortarActor -> ProjectileState(
                actorId = id,
                ownerId = actor.ownerActorId,
                x = actor.positionX.toInt(),
                y = actor.positionY.toInt(),
                kind = ProjectileKind.MORTAR,
            )

            is ServerMineActor -> MineState(
                actorId = id,
                x = actor.positionX,
                y = actor.positionY,
                armed = actor.isActive,
            )

            is ServerFlagActor -> {
                val size = actor.body.size
                val position = actor.body.position
                FlagState(
                    actorId = id,
                    flagType = actor.flagType,
                    number = actor.number,
                    x = (position.x.raw + size.width.raw / 2f).toInt(),
                    y = (position.y.raw + size.height.raw / 2f).toInt(),
                    isCarried = actor.isCarried,
                )
            }

            is ServerProductActor -> {
                val size = actor.body.size
                val position = actor.body.position
                ProductState(
                    actorId = id,
                    productType = actor.productType,
                    price = actor.price,
                    x = (position.x.raw + size.width.raw / 2f).toInt(),
                    y = (position.y.raw + size.height.raw / 2f).toInt(),
                    isCollected = actor.isCollected,
                )
            }

            is ServerLockActor -> {
                val size = actor.body.size
                val position = actor.body.position
                LockState(
                    actorId = id,
                    activation = actor.activation,
                    target = actor.target,
                    x = (position.x.raw + size.width.raw / 2f).toInt(),
                    y = (position.y.raw + size.height.raw / 2f).toInt(),
                    isFired = actor.isFired,
                )
            }

            is ServerWarpActor -> {
                val size = actor.body.size
                val position = actor.body.position
                WarpState(
                    actorId = id,
                    targetX = actor.targetX,
                    targetY = actor.targetY,
                    x = (position.x.raw + size.width.raw / 2f).toInt(),
                    y = (position.y.raw + size.height.raw / 2f).toInt(),
                    cooldownTicks = actor.cooldownTicks,
                )
            }

            is ServerDestroyerActor -> {
                val size = actor.body.size
                val position = actor.body.position
                DestroyerState(
                    actorId = id,
                    radius = actor.radius,
                    what = actor.what,
                    immediate = actor.immediate,
                    x = (position.x.raw + size.width.raw / 2f).toInt(),
                    y = (position.y.raw + size.height.raw / 2f).toInt(),
                    isFired = actor.isFired,
                )
            }

            is ServerEnforcerActor -> {
                val size = actor.body.size
                val position = actor.body.position
                EnforcerState(
                    actorId = id,
                    radius = actor.radius,
                    weapon = actor.weapon,
                    delay = actor.delay,
                    good = actor.good,
                    bad = actor.bad,
                    x = (position.x.raw + size.width.raw / 2f).toInt(),
                    y = (position.y.raw + size.height.raw / 2f).toInt(),
                )
            }

            is ServerTrainActor -> TrainState(
                actorId = id,
                x = actor.positionX,
                y = actor.positionY,
                isEngine = actor.isEngine,
                armor = actor.armor,
                alive = actor.alive,
            )

            is ServerZeppelinActor -> ZeppelinState(
                actorId = id,
                x = actor.positionX,
                y = actor.positionY,
                alive = actor.alive,
            )

            is ServerB52Actor -> B52State(
                actorId = id,
                x = actor.positionX,
                y = actor.positionY,
                armor = actor.armor,
                alive = actor.alive,
                bombCount = actor.bombCount,
            )

            else -> null
        }
    }

    private fun Int.toProtocolTeam(): Team = when (this) {
        0 -> Team.PLAYER
        1 -> Team.ENEMY
        else -> Team.NEUTRAL
    }

    companion object {
        internal fun fromCanonicalMap(map: CanonicalMapDefinition, mode: MatchMode = MatchMode.SINGLE): ServerMatchPrototype {
            // Round-trip through scene JSON so fixtures exercise the same load path as shipped scenes.
            val serializationManagerForBuild = SerializableMetadata.newSerializationManagerInstance(
                *tankArenaSerializableMetadata,
            )
            val sceneJson = CanonicalSceneBuilder.buildSceneJson(map, serializationManagerForBuild)
            return create(map.metadata, map.layers, serializationManagerForBuild.deserializeActors(sceneJson), mode)
        }

        /**
         * Production bootstrap: a committed scene plus its sidecar. The sidecar's tile layers drive
         * terrain effects, pits, A* pathing and line-of-sight exactly as on the fixture path.
         */
        fun fromSceneJson(sceneJson: String, sidecar: MapSceneSidecar, mode: MatchMode = MatchMode.SINGLE): ServerMatchPrototype {
            val serializationManagerForBuild = SerializableMetadata.newSerializationManagerInstance(
                *tankArenaSerializableMetadata,
            )
            val actors = serializationManagerForBuild.deserializeActors(sceneJson)
            return create(sidecar.metadata, sidecar.tileLayers, actors, mode)
        }

        private fun create(
            metadata: MapMetadata,
            layers: TileLayers?,
            actors: List<Serializable<*>>,
            mode: MatchMode,
        ): ServerMatchPrototype {
            val cellCount = metadata.widthTiles * metadata.heightTiles
            val solidLayer = layers?.solid?.takeIf { it.size == cellCount } ?: List(cellCount) { -1 }
            val instance = ServerMatchPrototype(
                mapMetadata = metadata,
                initialActors = MatchSetup.assignControl(actors, mode),
                terrainGrid = TerrainGridBuilder.build(layers, metadata),
                solidLayer = solidLayer,
                mode = mode,
            )
            instance.aiModeDispatcher = AiModeDispatcher(
                mode = metadata.modeCompatibility,
                waypoints = emptyList(), // Authored waypoints deferred
                pathfinder = instance.pathfinder,
            )
            return instance
        }
    }
}

private typealias IdentityHashMap<K, V> = java.util.IdentityHashMap<K, V>

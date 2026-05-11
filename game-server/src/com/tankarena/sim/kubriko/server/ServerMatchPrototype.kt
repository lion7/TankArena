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
import com.tankarena.content.TerrainGridBuilder
import com.tankarena.protocol.snapshot.TerrainGrid
import com.tankarena.protocol.snapshot.TerrainMaterial
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
private const val TANK_FOOTPRINT: Int = LEGACY_TILE_SIZE - 4
private const val TRAIN_FOOTPRINT: Int = 32
private const val ZEPPELIN_FOOTPRINT: Int = 32
private const val B52_FOOTPRINT: Int = 32

class ServerMatchPrototype private constructor(
    private val mapMetadata: MapMetadata,
    private val initialActors: List<Serializable<*>>,
    private val terrainGrid: TerrainGrid,
    private val solidLayer: List<Int>,
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
    private var nextActorId: Long = 1L
    private var currentTick: Long = 0L
    private var isDisposed: Boolean = false
    private var goalGood: Int = 0
    private var goalBad: Int = 0
    private var missionStatus: MissionStatus = MissionStatus.IN_PROGRESS
    private val pendingEvents: MutableList<GameEvent> = mutableListOf()

    val mission: MissionStatus get() = missionStatus
    val goalProgressGood: Int get() = goalGood
    val goalProgressBad: Int get() = goalBad

    enum class MissionStatus { IN_PROGRESS, WON, LOST }

    val worldWidth: Int get() = worldWidthPixels
    val worldHeight: Int get() = worldHeightPixels
    val missionCode: String get() = mapMetadata.missionCode

    fun initialize() {
        tickSource.start()
        awaitActorsPopulated(expectedSize = initialActors.size)
        for (actor in actorManager.allActors.value) {
            actorIds.getOrPut(actor) { nextActorId++ }
            if (actor is ServerTankActor && actor.playerIndex >= 0) {
                tanksByPlayerIndex[actor.playerIndex] = actor
            }
        }
    }

    fun tick(): WorldSnapshot = tick(intents = emptyMap())

    fun tick(intents: Map<Int, PlayerIntentFrame>): WorldSnapshot {
        if (isDisposed) return buildSnapshot(drainEvents = false)
        if (intents.isNotEmpty()) {
            for ((playerIndex, intent) in intents) {
                tanksByPlayerIndex[playerIndex]?.applyIntent(intent)
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
        triggerRocketContacts()
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

    internal fun queueDamageForTest(playerIndex: Int, amount: Int) {
        tanksByPlayerIndex[playerIndex]?.queueDamage(amount)
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
                cameraCenterX = tank.positionX,
                cameraCenterY = tank.positionY,
                hud = HudState(
                    armor = tank.armor,
                    shield = tank.shield,
                    invulnerableTicks = tank.invulnerableTicks,
                    fuel = tank.fuel,
                    lives = tank.lives,
                    missionProgress = goalGood,
                    missionCode = missionCode,
                    statusText = statusText,
                ),
                radar = buildRadar(tank, tanks, turrets, goals),
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

    private fun awaitActorsPopulated(expectedSize: Int) = runBlocking {
        withTimeout(2_000) {
            while (actorManager.allActors.value.size < expectedSize) {
                delay(5)
            }
        }
    }

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
            val claimant = tanks.firstOrNull { tank ->
                tank.playerIndex >= 0 && tank.armor > 0 && withinRadius(tank.positionX, tank.positionY, cx, cy, actor.radius)
            } ?: continue
            actor.isClaimed = true
            when (actor.who) {
                0 -> goalGood = (goalGood + actor.contribution).coerceIn(0, 100)
                else -> goalBad = (goalBad + actor.contribution).coerceIn(0, 100)
            }
            // keep claimant reference for future telemetry; unused right now
            @Suppress("UNUSED_VARIABLE") val claimedBy = claimant
        }
    }

    private fun drainTankLifecycleEvents() {
        for (actor in actorManager.allActors.value) {
            if (actor !is ServerTankActor) continue
            val id = actorIds[actor] ?: continue
            val events = actor.drainLifecycleEvents(id)
            if (events.isNotEmpty()) pendingEvents.addAll(events)
        }
    }

    private fun evaluateMission() {
        if (missionStatus != MissionStatus.IN_PROGRESS) return
        if (goalGood >= 100) {
            missionStatus = MissionStatus.WON
            pendingEvents += GameEvent.MissionWon(playerId = 0)
            return
        }
        val all = actorManager.allActors.value
        val playerTanks = all.filterIsInstance<ServerTankActor>().filter { it.playerIndex >= 0 }
        val playersAllDead = playerTanks.isNotEmpty() &&
            playerTanks.all { it.armor <= 0 && it.lives <= 0 }
        if (playersAllDead) {
            missionStatus = MissionStatus.LOST
            pendingEvents += GameEvent.MissionLost(playerId = 0)
            return
        }
        val enemyTanks = all.filterIsInstance<ServerTankActor>().filter { it.team == 1 }
        val enemyTurrets = all.filterIsInstance<ServerTurretActor>().filter { it.team == 1 }
        val hasEnemies = enemyTanks.isNotEmpty() || enemyTurrets.isNotEmpty()
        if (hasEnemies) {
            val enemiesAllDead = enemyTanks.all { it.armor <= 0 && it.lives <= 0 } &&
                enemyTurrets.all { it.armor <= 0 }
            if (enemiesAllDead) {
                missionStatus = MissionStatus.WON
                pendingEvents += GameEvent.MissionWon(playerId = 0)
            }
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
            if (ai.playerIndex >= 0) continue
            if (ai.armor <= 0) continue
            val target = pickAiTarget(ai, tanks)
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

    private val pathfinder: TilePathfinder by lazy {
        TilePathfinder(mapMetadata.widthTiles, mapMetadata.heightTiles, solidLayer)
    }

    private val lineOfSight: LineOfSight by lazy {
        LineOfSight(mapMetadata.widthTiles, mapMetadata.heightTiles, solidLayer)
    }

    private lateinit var aiModeDispatcher: AiModeDispatcher

    private var cachedPath: List<Pair<Int, Int>> = emptyList()
    private var cachedPathTick: Long = -1L

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

        // Recompute path every 15 ticks or if significantly displaced
        val shouldRepath = (currentTick - cachedPathTick) >= 15 || cachedPath.isEmpty()
        if (shouldRepath) {
            cachedPath = pathfinder.findPath(self.positionX, self.positionY, target.positionX, target.positionY)
            cachedPathTick = currentTick
        }

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

        val bodyTurn = turnDelta(self.bodyDirection, bodyDesired)
        val turretTurn = turnDelta(self.turretDirection, turretDesired)
        val bodyAligned = self.bodyDirection == bodyDesired
        val turretAligned = self.turretDirection == turretDesired
        val fireRangeSq = (320L * 320L)
        val visible = lineOfSight.hasLineOfSight(self.positionX, self.positionY, target.positionX, target.positionY)
        val fire = turretAligned && distSq <= fireRangeSq && self.primaryCooldownTicks == 0 && visible
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
     * Apply terrain effects (lava damage, water kill, pit kill) to all tanks.
     * Also set per-tank terrain speed multipliers for the next motion step.
     */
    private fun applyTerrainEffects() {
        val tanks = actorManager.allActors.value.filterIsInstance<ServerTankActor>()
        for (tank in tanks) {
            if (tank.armor <= 0) continue
            val tileX = tank.positionX / LEGACY_TILE_SIZE
            val tileY = tank.positionY / LEGACY_TILE_SIZE
            val tile = terrainGrid.at(tileX, tileY)

            // Set terrain speed multiplier for next motion step
            tank.setTerrainSpeedMultiplier(tile.speedMultiplier)

            // Lava: continuous damage (1 per tick, legacy HT_LAVA)
            if (tile.material == TerrainMaterial.LAVA) {
                tank.queueDamage(1)
            }

            // Water: kills non-amphibious tanks
            if (tile.material == TerrainMaterial.WATER) {
                tank.queueDamage(999) // Instant kill
            }

            // Pit detection: check sub-tile position within the pit tile
            if (tile.material == TerrainMaterial.PIT_BIG || tile.material == TerrainMaterial.PIT_SMALL) {
                val subX = (tank.positionX % LEGACY_TILE_SIZE + LEGACY_TILE_SIZE) % LEGACY_TILE_SIZE
                val subY = (tank.positionY % LEGACY_TILE_SIZE + LEGACY_TILE_SIZE) % LEGACY_TILE_SIZE
                val inPit = checkPit(subX, subY, tile.material, tileX, tileY)
                if (inPit) {
                    tank.queueDamage(999) // Instant kill
                }
            }
        }
    }

    /**
     * Check if a tank at sub-tile position (subX, subY) is inside a pit.
     * Legacy in_pit() checks tile type + sub-tile position.
     *
     * Big pit: kill when subY > 8 && subY < 24 (center of tile)
     * Small pit: kill when subY > 14 && subY < 18 (narrower center)
     *
     * Ramp tiles (RAMP_SMALL, RAMP_BIG) allow escape — not checked here.
     */
    private fun checkPit(subX: Int, subY: Int, material: TerrainMaterial, tileX: Int, tileY: Int): Boolean {
        // Simplified pit check: center of tank within pit danger zone
        // Legacy uses y2 (sub-tile Y offset of tank center within tile)
        val y2 = subY
        val x2 = subX

        return when (material) {
            TerrainMaterial.PIT_BIG -> {
                // Big pit: danger zone is y2 > 8 && y2 < 24
                // Different pit variants have different shapes (TL, TR, BL, BR, T, B, L, R, C)
                // For now, use center-based check — all big pit variants kill in center region
                y2 > 8 && y2 < 24 && x2 > 8 && x2 < 24
            }
            TerrainMaterial.PIT_SMALL -> {
                // Small pit: danger zone is y2 > 14 && y2 < 18 (narrower)
                y2 > 14 && y2 < 18 && x2 > 14 && x2 < 18
            }
            else -> false
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

    private fun triggerRocketContacts() {
        val rockets = actorManager.allActors.value.filterIsInstance<ServerRocketActor>().filter { !it.isDead }
        if (rockets.isEmpty()) return
        val tanks = actorManager.allActors.value.filterIsInstance<ServerTankActor>().filter { it.armor > 0 }
        for (rocket in rockets) {
            val target = tanks.firstOrNull { tank ->
                tank !== rocket.ownerRef &&
                    withinRadius(tank.positionX, tank.positionY, rocket.positionX.toInt(), rocket.positionY.toInt(), SERVER_TANK_HALF + 4)
            } ?: continue
            rocket.explodeOn(target)
        }
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
        for (tank in tanksByPlayerIndex.values) {
            val request = tank.drainRocketRequest() ?: continue
            spawns += rocketFromRequest(tank, request)
        }
        for (actor in actorManager.allActors.value) {
            if (actor is ServerTankActor && actor.playerIndex < 0) {
                val request = actor.drainRocketRequest() ?: continue
                spawns += rocketFromRequest(actor, request)
            }
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
        val target = pickRocketTarget(owner)
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
                velocityX = request.initialVelocityX,
                velocityY = request.initialVelocityY,
                targetX = target?.positionX ?: (request.originX + request.initialVelocityX.toInt() * 100),
                targetY = target?.positionY ?: (request.originY + request.initialVelocityY.toInt() * 100),
            ),
        )
        rocket.ownerRef = owner
        
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
        for (tank in tanksByPlayerIndex.values) {
            val request = tank.drainMortarRequest() ?: continue
            spawns += mortarFromRequest(tank, request)
        }
        for (actor in actorManager.allActors.value) {
            if (actor is ServerTankActor && actor.playerIndex < 0) {
                val request = actor.drainMortarRequest() ?: continue
                spawns += mortarFromRequest(actor, request)
            }
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

    private fun pickRocketTarget(owner: ServerTankActor): ServerTankActor? {
        var best: ServerTankActor? = null
        var bestDistSq = Long.MAX_VALUE
        for (candidate in actorManager.allActors.value) {
            if (candidate !is ServerTankActor) continue
            if (candidate === owner) continue
            if (candidate.armor <= 0) continue
            if (candidate.team == owner.team) continue
            val dx = (candidate.positionX - owner.positionX).toLong()
            val dy = (candidate.positionY - owner.positionY).toLong()
            val distSq = dx * dx + dy * dy
            if (distSq < bestDistSq) {
                best = candidate
                bestDistSq = distSq
            }
        }
        return best
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
        for (tank in tanksByPlayerIndex.values) {
            val request = tank.drainMineRequest() ?: continue
            spawns += mineFromRequest(tank, request)
        }
        for (actor in actorManager.allActors.value) {
            if (actor is ServerTankActor && actor.playerIndex < 0) {
                val request = actor.drainMineRequest() ?: continue
                spawns += mineFromRequest(actor, request)
            }
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
        
        // Emit sound event for mine deployment
        pendingEvents += GameEvent.Sound(
            kind = com.tankarena.protocol.snapshot.SoundKind.MINE,
            x = request.originX,
            y = request.originY,
        )
        
        return mine
    }

    private fun drainFireRequests() {
        val spawns = mutableListOf<ServerProjectileActor>()
        for (tank in tanksByPlayerIndex.values) {
            val request = tank.drainFireRequest() ?: continue
            spawns += projectileFromRequest(tank, request)
        }
        for (actor in actorManager.allActors.value) {
            if (actor is ServerTankActor && actor.playerIndex < 0) {
                val request = actor.drainFireRequest() ?: continue
                spawns += projectileFromRequest(actor, request)
            }
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
        internal fun fromCanonicalMap(map: CanonicalMapDefinition): ServerMatchPrototype {
            val serializationManagerForBuild = SerializableMetadata.newSerializationManagerInstance(
                *tankArenaSerializableMetadata,
            )
            val sceneJson = CanonicalSceneBuilder.buildSceneJson(map, serializationManagerForBuild)
            val actors = serializationManagerForBuild.deserializeActors(sceneJson)
            val terrainGrid = TerrainGridBuilder.build(map.layers, map.metadata)
            val instance = ServerMatchPrototype(
                mapMetadata = map.metadata,
                initialActors = actors,
                terrainGrid = terrainGrid,
                solidLayer = map.layers.solid,
            )
            instance.aiModeDispatcher = AiModeDispatcher(
                mode = map.metadata.modeCompatibility,
                waypoints = emptyList(), // Authored waypoints deferred
                pathfinder = instance.pathfinder,
            )
            return instance
        }

        fun fromSceneJson(sceneJson: String, mapMetadata: MapMetadata): ServerMatchPrototype {
            val serializationManagerForBuild = SerializableMetadata.newSerializationManagerInstance(
                *tankArenaSerializableMetadata,
            )
            val actors = serializationManagerForBuild.deserializeActors(sceneJson)
            val terrainGrid = TerrainGrid.empty(mapMetadata.widthTiles, mapMetadata.heightTiles)
            val solidLayer = List(mapMetadata.widthTiles * mapMetadata.heightTiles) { -1 }
            val instance = ServerMatchPrototype(
                mapMetadata = mapMetadata,
                initialActors = actors,
                terrainGrid = terrainGrid,
                solidLayer = solidLayer,
            )
            instance.aiModeDispatcher = AiModeDispatcher(
                mode = mapMetadata.modeCompatibility,
                waypoints = emptyList(),
                pathfinder = instance.pathfinder,
            )
            return instance
        }
    }
}

private typealias IdentityHashMap<K, V> = java.util.IdentityHashMap<K, V>

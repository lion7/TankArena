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
import com.tankarena.protocol.snapshot.AbombState
import com.tankarena.protocol.snapshot.ActorState
import com.tankarena.protocol.snapshot.BonusState
import com.tankarena.protocol.snapshot.ManState
import com.tankarena.protocol.snapshot.EffectKind
import com.tankarena.protocol.snapshot.TileState
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
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

private const val MILLIS_PER_TICK: Int = 10
/** Legacy nearest_target(): a rocket locks onto anything within 15 px of the marker. */
private const val ROCKET_TARGET_RADIUS_PX: Int = 15
/** Legacy create_mortar(): each growth step of the blast hits structures for 5. */
private const val MORTAR_STEP_POWER: Int = 5
/** Legacy HT_FLAME hit radius. */
private const val FLAME_HIT_RADIUS_PX: Int = 15
/** Legacy tank_write_marker(): the marker turns red beyond 400 px. */
private const val LOCK_OUT_OF_RANGE_PX: Int = 400
// Legacy src/objects/train.c: quarter-circle rails of radius 49, wagons 31 px apart, at most
// 50 px/s, the engine's smoke 11 px ahead of its centre, and wagon 7 carrying an A-bomb.
private const val RAILS_RADIUS: Int = 49
private const val WAGON_DISTANCE: Int = 31
private const val TRAIN_MAX_SPEED: Int = 50
private val TRAIN_SMOKE_OFFSET: Pair<Int, Int> = 0 to -11
private const val ABOMB_WAGON: Int = 7
/** Legacy b52.c: where the ten bombs leave the bay, the four engines, and the bombs' power. */
private val B52_BOMBS: List<Pair<Int, Int>> = listOf(
    -27 to 17, -15 to 17, -21 to 23, -14 to 28, -27 to 30,
    16 to 17, 28 to 20, 15 to 24, 27 to 29, 9 to 32,
)
private val B52_ENGINES: List<Pair<Int, Int>> = listOf(-16 to 9, -10 to 8, 9 to 8, 15 to 9)
private const val B52_BOMB_POWER: Int = 20
/** Legacy `prodname[]` (src/objects/product.c). */
private val PRODUCT_NAMES: List<String> = listOf(
    "WASHING POWDER", "CHOCOLATE BAR", "APPLE", "WINE", "M&M'S", "TOILET PAPER", "ONION", "CIGARETTES",
    "COOKIES", "BEER", "COLA", "SAUSAGE", "BAMI", "CAN", "SPAGHETTI",
)
/** Legacy `vzep`. */
private const val ZEPPELIN_SPEED: Float = 10f
/** Legacy create_mine(): r = 3. */
private const val MINE_RADIUS_PX: Int = 3
/** Legacy HT_SROCK, as the man layer stores a soldier's weapon. */
private const val HT_SROCK: Int = 10
/** Legacy create_bonus(): the random roll (out of 10000) below which each bonus type drops. */
private val RANDOM_BONUS_THRESHOLDS = intArrayOf(300, 600, 700, 800, 850, 900, 950, 1000, 1050, 1100, 1150, 1200, 1225, 1250)
private const val TANK_FOOTPRINT: Int = LEGACY_TILE_SIZE - 4
private const val TRAIN_FOOTPRINT: Int = 32
private const val ZEPPELIN_FOOTPRINT: Int = 32
private const val B52_FOOTPRINT: Int = 32

class ServerMatchPrototype private constructor(
    private val mapMetadata: MapMetadata,
    private val initialActors: List<Serializable<*>>,
    private val terrainGrid: TerrainGrid,
    /** The map's buildings: strengths, ruins and the pixel masks tanks and bullets collide with. */
    internal val structures: StructureGrid,
    val mode: MatchMode,
    /** Legacy make_tank(): human tanks start with the blinking start shield. */
    private val startShields: Boolean,
    /** The map's tile layers, for the bonus and man layers. */
    private val layers: TileLayers? = null,
    /** Player vs player: the players' vehicles, the race length and the round. */
    val dual: DualSetup = DualSetup(),
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
    /** Player vs player only: pads, flags and races (legacy player.c, flag.c). */
    private var dualRules: DualRules? = null
    private var nextActorId: Long = 1L
    private var currentTick: Long = 0L
    private var isDisposed: Boolean = false
    private var goalGood: Int = 0
    private var goalBad: Int = 0
    private var missionStatus: MissionStatus = MissionStatus.IN_PROGRESS
    private val pendingEvents: MutableList<GameEvent> = mutableListOf()

    /** Runtime-only objects (see MatchObjects.kt). */
    private val bonuses: MutableList<Bonus> = mutableListOf()
    private val soldiers: MutableList<Soldier> = mutableListOf()
    private val abombs: MutableList<Abomb> = mutableListOf()
    private val pendingSoldierShots: MutableList<Pair<Soldier, Soldier.Shot>> = mutableListOf()
    /** Legacy `nuke_total`: how far every colour is brightened while A-bombs go off. */
    private var flash: Int = 0
    private val objectRandom = kotlin.random.Random(mapMetadata.widthTiles * 7919 + mapMetadata.heightTiles * 104729)

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
            tank.configureForMatch(
                maxX = worldWidthPixels - LEGACY_TILE_SIZE,
                maxY = worldHeightPixels - LEGACY_TILE_SIZE,
                dual = mode == MatchMode.DUAL,
                night = mapMetadata.night,
            )
            tank.surroundings = tankSurroundings
            // Legacy make_tank(): human tanks start with a blinking shield (`shield.icount = 15`).
            if (startShields && !tank.computer) tank.grantInvulnerability(START_SHIELD_TICKS)
        }
        // Legacy prepare_player() / prepare_flag(): pads, races and flags only play in player vs
        // player; elsewhere the flags are removed.
        val flags = actorManager.allActors.value.filterIsInstance<ServerFlagActor>()
        val unused = if (mode == MatchMode.DUAL) {
            DualRules(dual, mapMetadata.world, mapMetadata.widthTiles, mapMetadata.heightTiles, structures, objectRandom)
                .also { dualRules = it }
                .prepare(allTanks, flags)
        } else {
            flags
        }
        if (unused.isNotEmpty()) actorManager.remove(unused)
        linkTrains()
        prepareB52s()
        prepareLocks()
        // Legacy control_turret(): turrets placed off a building are gone from the first tick
        // (their goal contribution still counts, but they are not kills).
        removeFallenTurrets()
        drainTurretDestructions()
        createAllBonuses()
        missionKills = 0
        missionScore = 0
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
        checkProjectileStructureHits()
        checkProjectileVehicleHits()
        triggerFlameContacts()
        checkSmallObjectHits()
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
        stepSoldiers()
        drainFireRequests()
        drainMineRequests()
        drainRocketRequests()
        drainMortarRequests()
        drainAbombRequests()
        drainSoldierRequests()
        stepAbombs()
        touchSmallObjects()
        checkVehicleOverlaps()
        drainTankLifecycleEvents()
        drainTurretDestructions()
        handOverDeadPlayers()
        stepWarps()
        stepLocks()
        stepDestroyers()
        stepEnforcers()
        stepTrains()
        stepZeppelins()
        stepB52s()
        collectProducts()
        collectGoals()
        evaluateMission()
        currentTick += 1
        return buildSnapshot(drainEvents = true)
    }

    fun snapshot(): WorldSnapshot = buildSnapshot(drainEvents = false)

    internal fun playerTankForTest(playerIndex: Int): ServerTankActor? = tanksByPlayerIndex[playerIndex]

    internal fun tanksForTest(): List<ServerTankActor> = allTanks

    internal fun trainsForTest(): List<ServerTrainActor> = trains

    internal fun locksForTest(): List<ServerLockActor> = locks

    internal fun zeppelinsForTest(): List<ServerZeppelinActor> = actorManager.allActors.value.filterIsInstance<ServerZeppelinActor>()

    internal fun hitTrainForTest(car: ServerTrainActor, power: Int) = hitTrain(car, power)

    internal fun turretsForTest(): List<ServerTurretActor> =
        actorManager.allActors.value.filterIsInstance<ServerTurretActor>()

    internal fun solidTileCountForTest(): Int = structures.blockingLayer().count { it >= 0 }

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
                    // Legacy write_lives(): flag hunts and races keep the pad's four lives.
                    lives = if (tank.infiniteLives) LEGACY_PAD_LIVES else tank.lives,
                    missionProgress = goalGood,
                    missionCode = missionCode,
                    statusText = statusText,
                    score = missionScore,
                    kills = missionKills,
                    time = currentTick,
                    currentWeapon = tank.currentWeapon,
                    ammo = tank.ammoCounts,
                    hasFlamer = tank.spec.hasFlamer,
                    tankId = tank.controllerId,
                    maxArmor = tank.maxArmor,
                    maxFuel = tank.maxFuel,
                    missionProgressBad = goalBad,
                    messageLine1 = tank.messageLine1,
                    messageLine2 = tank.messageLine2,
                    messageSteps = tank.messageSteps,
                    vehicleType = tank.tankType,
                    alive = tank.armor > 0,
                    flagHunt = dualRules?.flagHunt == true,
                    flags = tank.flagsPicked,
                    roundWon = dualRules?.winner == playerIndex,
                    roundLost = dualRules?.winner == (playerIndex xor 1),
                ),
                radar = buildRadar(tank, tanks, turrets, goals),
                lockMarker = lockMarkerFor(tank),
            )
        }
        return views
    }

    /**
     * Legacy write_radar() (src/game/radar.c): every live tank including the player's own, every
     * turret and every rocket; the client keeps those within 15 tiles of the player.
     */
    private fun buildRadar(
        self: ServerTankActor,
        tanks: List<ServerTankActor>,
        turrets: List<ServerTurretActor>,
        @Suppress("UNUSED_PARAMETER") goals: List<ServerGoalActor>,
    ): List<RadarContact> {
        val contacts = mutableListOf<RadarContact>()
        for (tank in tanks) {
            if (tank.armor <= 0) continue
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
        for (rocket in actorManager.allActors.value.filterIsInstance<ServerRocketActor>()) {
            if (rocket.isDead) continue
            val id = actorIds[rocket] ?: continue
            contacts += RadarContact(
                actorId = id,
                approximateX = rocket.positionX.toInt(),
                approximateY = rocket.positionY.toInt(),
                kind = RadarContactKind.PROJECTILE,
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

    // ---- Destroyers, locks and enforcers (src/objects/destroy.c, lock.c, enforce.c) ----

    private var destroyers: List<ServerDestroyerActor> = emptyList()
    private var locks: List<ServerLockActor> = emptyList()
    private val lockOwners: MutableMap<ServerLockActor, Actor?> = IdentityHashMap()
    private val lockTargets: MutableMap<ServerLockActor, Actor?> = IdentityHashMap()

    /** Centre of any scene object, for legacy nearest_object(). */
    private fun centreOf(actor: Actor): Pair<Int, Int>? = when (actor) {
        is ServerTankActor -> actor.positionX to actor.positionY
        is ServerTrainActor -> actor.positionX to actor.positionY
        is ServerMineActor -> actor.positionX to actor.positionY
        is ServerB52Actor -> actor.positionX to actor.positionY
        is ServerZeppelinActor -> actor.positionX to actor.positionY
        is com.pandulapeter.kubriko.actor.traits.Positionable -> {
            val body = actor.body
            if (body is com.pandulapeter.kubriko.actor.body.BoxBody) {
                (body.position.x.raw + body.size.width.raw / 2f).toInt() to (body.position.y.raw + body.size.height.raw / 2f).toInt()
            } else {
                body.position.x.raw.toInt() to body.position.y.raw.toInt()
            }
        }
        else -> null
    }

    /** Legacy nearest_object(): the closest other object within 15 px of ([x], [y]). */
    private fun nearestObject(self: Actor, x: Int, y: Int): Actor? {
        var best: Actor? = null
        var bestDistSq = 15L * 15L
        for (actor in actorManager.allActors.value) {
            if (actor === self || actor is ServerWallActor) continue
            val (ax, ay) = centreOf(actor) ?: continue
            val distSq = (ax - x).toLong() * (ax - x) + (ay - y).toLong() * (ay - y)
            if (distSq < bestDistSq) {
                best = actor
                bestDistSq = distSq
            }
        }
        return best
    }

    /**
     * Legacy prepare_lock(): a lock watches the object nearest to it (activation 1) or the
     * building under it (activation 0); it acts on the object nearest to its target point, or on
     * the structure there, which it makes indestructible until the lock blows.
     */
    private fun prepareLocks() {
        locks = actorManager.allActors.value.filterIsInstance<ServerLockActor>()
        destroyers = actorManager.allActors.value.filterIsInstance<ServerDestroyerActor>()
        for (lock in locks) {
            val (x, y) = centreOf(lock) ?: continue
            if (lock.activation == 1) lockOwners[lock] = nearestObject(lock, x, y)
            when (lock.target) {
                0 -> structures.makeIndestructible(Math.floorDiv(lock.lockX, LEGACY_TILE_SIZE), Math.floorDiv(lock.lockY, LEGACY_TILE_SIZE))
                1, 2 -> lockTargets[lock] = nearestObject(lock, lock.lockX, lock.lockY)
            }
        }
    }

    /** Legacy remove_object() for the objects a lock watches: is this one gone (or a dead vehicle)? */
    private fun isGone(actor: Actor?): Boolean = when (actor) {
        null -> true
        is ServerTankActor -> actor.armor <= 0
        is ServerTurretActor -> !actor.isAlive
        is ServerTrainActor -> !actor.alive
        is ServerDestroyerActor -> actor.isFired
        is ServerLockActor -> actor.isFired
        is ServerGoalActor -> actor.isClaimed
        is ServerMineActor -> actor.isDead
        is ServerB52Actor -> !actor.alive || actor.armor <= 0
        else -> false
    }

    /** Legacy control_lock(). */
    private fun stepLocks() {
        for (lock in locks) {
            if (lock.isFired) continue
            val (x, y) = centreOf(lock) ?: continue
            val trigger = when (lock.activation) {
                0 -> !structures.isStanding(Math.floorDiv(x, LEGACY_TILE_SIZE), Math.floorDiv(y, LEGACY_TILE_SIZE))
                1 -> isGone(lockOwners[lock])
                else -> false
            }
            if (trigger) blowLock(lock)
        }
    }

    /**
     * Legacy blow_lock(): take the locked structure down (HT_REMOVE), destroy the target object
     * (HT_DESTROY) or simply remove it; then the lock itself is used up.
     */
    private fun blowLock(lock: ServerLockActor) {
        lock.isFired = true
        when (lock.target) {
            0 -> {
                val tileX = Math.floorDiv(lock.lockX, LEGACY_TILE_SIZE)
                val tileY = Math.floorDiv(lock.lockY, LEGACY_TILE_SIZE)
                pendingEvents += GameEvent.Effect(EffectKind.EXPLOSION, lock.lockX, lock.lockY)
                pendingEvents += GameEvent.Sound(com.tankarena.protocol.snapshot.SoundKind.EXPLODE, lock.lockX, lock.lockY)
                hitStructure(tileX, tileY, StructureLayer.SOLID, HitKind.NORMAL, StructureGrid.INDESTRUCTIBLE, EffectKind.EXPLOSION, remove = true)
            }
            1 -> lockTargets[lock]?.let(::destroyObject)
            2 -> lockTargets[lock]?.let(::removeObject)
        }
        modifyGoalCounter(lock.goalContribution)
    }

    /** Legacy hit_obj_for(target, ..., HT_DESTROY, 1000). */
    private fun destroyObject(actor: Actor) {
        centreOf(actor)?.let { (x, y) ->
            if (actor !is ServerMineActor) {
                pendingEvents += GameEvent.Effect(EffectKind.EXPLOSION, x, y)
                pendingEvents += GameEvent.Sound(com.tankarena.protocol.snapshot.SoundKind.EXPLODE, x, y)
            }
        }
        when (actor) {
            is ServerTankActor -> actor.queueDestroy()
            is ServerTrainActor -> hitTrain(actor, StructureGrid.INDESTRUCTIBLE)
            is ServerDestroyerActor -> fireDestroyer(actor)
            is ServerMineActor -> actor.detonateOn(null)
            is ServerB52Actor -> hitB52(actor, StructureGrid.INDESTRUCTIBLE)
            // Turrets, goals and locks have no hit function in the original.
            else -> Unit
        }
    }

    /** Legacy remove_object(target): the object is simply gone (and does what it does when removed). */
    private fun removeObject(actor: Actor) {
        when (actor) {
            // Vehicles cannot leave the match here; a removed vehicle counts as destroyed.
            is ServerTankActor -> actor.queueDestroy()
            is ServerTurretActor -> actor.fall()
            is ServerTrainActor -> removeTrain(actor)
            is ServerDestroyerActor -> fireDestroyer(actor)
            is ServerLockActor -> if (!actor.isFired) {
                actor.isFired = true
                modifyGoalCounter(actor.goalContribution)
            }
            is ServerGoalActor -> if (!actor.isClaimed) {
                actor.isClaimed = true
                modifyGoalCounter(actor.contribution)
            }
            is ServerMineActor -> actor.detonateOn(null)
            is ServerB52Actor -> actor.alive = false
            else -> Unit
        }
    }

    /** Legacy prepare_destroyer(): an immediate destroyer goes off on the first tick. */
    private fun stepDestroyers() {
        for (destroyer in destroyers) {
            if (!destroyer.isFired && destroyer.immediate) fireDestroyer(destroyer)
        }
    }

    /**
     * Legacy remove_destroyer(): flatten every destructible building (`what & 1`) and destroy
     * every vehicle, soldier and the like (`what & 2`) within the radius — HT_DESTROY, power 1000,
     * through every shield.
     */
    private fun fireDestroyer(destroyer: ServerDestroyerActor) {
        if (destroyer.isFired) return
        destroyer.isFired = true
        val (x, y) = centreOf(destroyer) ?: return
        val radius = destroyer.radius
        if (destroyer.what and 1 != 0) {
            var y2 = y - radius
            while (y2 < y + radius) {
                var x2 = x - radius
                while (x2 < x + radius) {
                    val tileX = Math.floorDiv(x2, LEGACY_TILE_SIZE)
                    val tileY = Math.floorDiv(y2, LEGACY_TILE_SIZE)
                    if (withinRadius(x2, y2, x, y, radius + 16) && structures.strength(tileX, tileY, StructureLayer.SOLID) != 0) {
                        pendingEvents += GameEvent.Effect(EffectKind.EXPLOSION, x2, y2)
                        pendingEvents += GameEvent.Sound(com.tankarena.protocol.snapshot.SoundKind.EXPLODE, x2, y2)
                        hitStructure(tileX, tileY, StructureLayer.SOLID, HitKind.NORMAL, StructureGrid.INDESTRUCTIBLE, EffectKind.EXPLOSION)
                    }
                    x2 += LEGACY_TILE_SIZE
                }
                y2 += LEGACY_TILE_SIZE
            }
            repeat(5) {
                val angle = 2 * Math.PI * objectRandom.nextInt(360) / 360
                pendingEvents += GameEvent.Effect(EffectKind.EXPLOSION, x + (radius * Math.cos(angle)).toInt(), y + (radius * Math.sin(angle)).toInt())
            }
        }
        if (destroyer.what and 2 != 0) {
            for (tank in allTanks) {
                if (tank.armor > 0 && tank.height > 0 && withinRadius(tank.positionX, tank.positionY, x, y, maxOf(radius, tank.spec.radius))) {
                    tank.queueDestroy()
                }
            }
            blastSmallObjects(x, y, radius, power = StructureGrid.INDESTRUCTIBLE)
        }
        modifyGoalCounter(destroyer.goalContribution)
    }

    /**
     * Legacy control_enforcer(): computer tanks of the enforced side within the radius have the
     * weapon selected, and every `delay` ticks one of them fires it.
     */
    private fun stepEnforcers() {
        for (enforcer in actorManager.allActors.value.filterIsInstance<ServerEnforcerActor>()) {
            if (enforcer.cooldownTicks > 0) enforcer.cooldownTicks -= 1
            val (ex, ey) = centreOf(enforcer) ?: continue
            for (tank in allTanks) {
                if (!tank.computer || tank.armor <= 0) continue
                if (!(enforcer.good && tank.team == 0) && !(enforcer.bad && tank.team != 0)) continue
                val dx = tank.positionX - ex
                val dy = tank.positionY - ey
                if (dx * dx + dy * dy >= enforcer.radius * enforcer.radius) continue
                val fire = enforcer.fire && enforcer.cooldownTicks == 0
                tank.enforce(enforcer.weapon, fire = fire, keep = enforcer.change)
                if (fire) enforcer.cooldownTicks = enforcer.delay
            }
        }
    }

    // ---- Trains (src/objects/train.c) ----

    private var trains: List<ServerTrainActor> = emptyList()
    /** Turrets riding wagons (make_turret(..., wagon)). */
    private val ridingTurrets: MutableMap<ServerTurretActor, ServerTrainActor> = IdentityHashMap()
    private val trainRandom = kotlin.random.Random(mapMetadata.widthTiles * 131 + mapMetadata.heightTiles * 17)

    /** Legacy prepare_train() with relocate(): tie each car to its neighbours and its turret. */
    private fun linkTrains() {
        trains = actorManager.allActors.value.filterIsInstance<ServerTrainActor>()
        val byRef = trains.filter { it.ref != 0 }.associateBy { it.ref }
        for (car in trains) {
            car.front = byRef[car.frontRef]?.takeIf { it !== car }
            car.rear = byRef[car.rearRef]?.takeIf { it !== car }
            car.syncBody()
        }
        for (turret in actorManager.allActors.value.filterIsInstance<ServerTurretActor>()) {
            if (turret.ownerRef == 0) continue
            val wagon = byRef[turret.ownerRef] ?: continue
            wagon.turret = turret
            ridingTurrets[turret] = wagon
            turret.moveTo(wagon.positionX, wagon.positionY)
        }
    }

    private fun stepTrains() {
        for (car in trains) {
            if (car.alive && car.driving) controlTrain(car)
        }
    }

    /** The legacy type of the base (layer 0) picture at a tile, for following the rails. */
    private fun railTypeAt(tileX: Int, tileY: Int): String? {
        val index = tileX + tileY * mapMetadata.widthTiles
        return com.tankarena.content.PictureCatalog.legacyRow(mapMetadata.world, structures.base.getOrElse(index) { -1 })?.type
    }

    /**
     * Legacy control_train(): the car's place follows from its speed and the time it has been on
     * its rail segment (straight, or a quarter circle of radius 49); a wagon closes up to 31 px
     * behind the car in front; the car crushes vehicles and soldiers, wrecks other trains, ploughs
     * through buildings (losing 20 px/s), and pulls the car behind it.
     */
    private fun controlTrain(car: ServerTrainActor) {
        if (!car.alive) return
        var v = car.speed
        val dir = car.railsDir
        val turnDirection = car.direction
        val now = currentTick
        if (v > 0) {
            val elapsed = (now - car.segmentStartTick).toFloat()
            var a = 0f
            var x = 0f
            var y = 0f
            when (car.railsType) {
                RAILS_HORIZONTAL -> {
                    x = v * elapsed / 100f
                    car.left = (car.railsX + car.railsStart + dir * x - 16).toInt()
                    car.top = car.railsY - 16
                    car.direction = if (dir == 1) 4 else 12
                }
                RAILS_VERTICAL -> {
                    y = v * elapsed / 100f
                    car.left = car.railsX - 16
                    car.top = (car.railsY + car.railsStart + dir * y - 16).toInt()
                    car.direction = if (dir == 1) 8 else 0
                }
                else -> {
                    a = v * elapsed / 100f / RAILS_RADIUS
                    val a2 = car.railsStart - dir * a
                    car.direction = directionFromRadians(a2 + if (dir == 1) 1.5 * Math.PI else 0.5 * Math.PI)
                    car.left = (car.railsX + RAILS_RADIUS * Math.cos(a2.toDouble()) - 16).toInt()
                    car.top = (car.railsY - RAILS_RADIUS * Math.sin(a2.toDouble()) - 16).toInt()
                }
            }
            if (a > 0.51f * Math.PI || x >= 33f || y >= 33f) {
                car.segmentStartTick = now
                setupNewRails(car, car.endX, car.endY)
            }
        }
        val front = car.front
        if (front != null && v > 0) {
            val d = kotlin.math.sqrt(((car.left - front.left).toDouble().let { it * it }) + (car.top - front.top).toDouble().let { it * it }).toInt() - WAGON_DISTANCE
            val dc = now - car.segmentStartTick
            val cc = (kotlin.math.abs(d).toFloat() * 100f / v / 10f).toLong()
            if (kotlin.math.abs(d) < 100) {
                if (d < -2 && dc > cc) car.segmentStartTick += cc
                else if (d > 2) car.segmentStartTick -= cc
            }
        }
        car.syncBody()
        trainCollisions(car)
        if (!car.alive) return
        val hit = IntArray(2)
        val oblock = com.tankarena.content.PictureMasks.mask("OBLOCK")
        if (oblock != null && structures.overlap(oblock, car.left, car.top, hit) > 0) {
            if (structures.strength(hit[0], hit[1], StructureLayer.SOLID) == StructureGrid.INDESTRUCTIBLE) {
                removeTrain(car)
                return
            }
            val bx = hit[0] * LEGACY_TILE_SIZE
            val by = hit[1] * LEGACY_TILE_SIZE
            pendingEvents += GameEvent.Effect(EffectKind.EXPLOSION, bx, by)
            pendingEvents += GameEvent.Sound(com.tankarena.protocol.snapshot.SoundKind.EXPLODE, bx, by)
            hitStructure(hit[0], hit[1], StructureLayer.SOLID, HitKind.NORMAL, StructureGrid.INDESTRUCTIBLE, EffectKind.EXPLOSION)
            v = if (v < 25) 5 else v - 20
            changeSpeed(car, v)
        }
        if (car.smokes) {
            if (car.smokeCooldownTicks > 0) car.smokeCooldownTicks -= 1
            if (car.smokeCooldownTicks == 0) {
                car.smokeCooldownTicks = 20 + trainRandom.nextInt(20)
                val (sx, sy) = rotate(turnDirection, TRAIN_SMOKE_OFFSET)
                pendingEvents += GameEvent.Effect(
                    EffectKind.ROOK,
                    car.positionX + sx - 2 + trainRandom.nextInt(4),
                    car.positionY + sy - 2 + trainRandom.nextInt(4),
                )
            }
        }
        car.turret?.takeIf { it.isAlive }?.moveTo(car.positionX, car.positionY)
        if (front != null) {
            changeSpeed(car, front.speed)
        } else {
            if (car.speedChangeCooldownTicks > 0) car.speedChangeCooldownTicks -= 1
            if (car.speedChangeCooldownTicks == 0) {
                v = if (car.isEngine) minOf(v + 5, TRAIN_MAX_SPEED) else if (v > 5) v - 5 else 0
                changeSpeed(car, v)
                car.speedChangeCooldownTicks = ServerTrainActor.SPEED_CHANGE_TICKS
            }
        }
        car.rear?.let(::controlTrain)
    }

    /** Legacy change_speed(): keep the car where it is on its segment while its speed changes. */
    private fun changeSpeed(car: ServerTrainActor, newSpeed: Int) {
        val old = car.speed
        car.speed = newSpeed
        if (newSpeed == 0) return
        if (old == 0) car.segmentStartTick = currentTick
        val dc = ((currentTick - car.segmentStartTick).toFloat() * old / newSpeed).toLong()
        car.segmentStartTick = currentTick - dc
    }

    /** Legacy radtoa16(in2pi(a)). */
    private fun directionFromRadians(angle: Double): Int {
        val a = ((angle % (2 * Math.PI)) + 2 * Math.PI) % (2 * Math.PI)
        return ((Math.PI / 2 - a) / (2 * Math.PI / 16)).let { Math.round(it).toInt() }.mod(16)
    }

    /**
     * Legacy setup_new_rails(): look at the rail picture on the next tile (wrapping round the map)
     * and set up the straight piece or quarter circle the car runs along next. A car that runs
     * out of rails (or onto a destroyed bridge) stops for good.
     */
    private fun setupNewRails(car: ServerTrainActor, tileX0: Int, tileY0: Int) {
        val tileX = if (tileX0 < 0) mapMetadata.widthTiles - 1 else if (tileX0 >= mapMetadata.widthTiles) 0 else tileX0
        val tileY = if (tileY0 < 0) mapMetadata.heightTiles - 1 else if (tileY0 >= mapMetadata.heightTiles) 0 else tileY0
        car.endX = tileX
        car.endY = tileY
        val b = LEGACY_TILE_SIZE
        fun straight(type: Int) {
            car.railsType = type
            car.railsDir = car.endDir
            car.railsStart = if (car.endDir == 1) -16f else 16f
            car.railsX = tileX * b + 16
            car.railsY = tileY * b + 16
            if (type == RAILS_HORIZONTAL) car.endX += car.railsDir else car.endY += car.railsDir
        }
        fun curve(start: Double, cx: Int, cy: Int, dir: Int, endType: Int, endDir: Int, endX: Int, endY: Int) {
            car.railsType = RAILS_CIRCLE
            car.railsStart = start.toFloat()
            car.railsX = cx
            car.railsY = cy
            car.railsDir = dir
            car.endType = endType
            car.endDir = endDir
            car.endX = endX
            car.endY = endY
        }
        val pi = Math.PI
        val x = tileX * b
        val y = tileY * b
        when (railTypeAt(tileX, tileY)) {
            "RAILS_H", "BRIDGE_H" -> straight(RAILS_HORIZONTAL)
            "RAILS_V", "RAILS_S_B", "RAILS_S_D", "RAILS_W_A", "RAILS_W_C", "BRIDGE_V" -> straight(RAILS_VERTICAL)
            "RAILS_CR" -> straight(car.endType)
            "RAILS_TL_B", "RAILS_W_B" -> curve(0.5 * pi, x + b, y + 2 * b, -1, RAILS_VERTICAL, 1, tileX - 1, tileY + 2)
            "RAILS_TL_C" -> curve(pi, x + 2 * b, y + b, 1, RAILS_HORIZONTAL, 1, tileX + 2, tileY - 1)
            "RAILS_TR_A", "RAILS_S_A" -> curve(0.5 * pi, x, y + 2 * b, 1, RAILS_VERTICAL, 1, tileX + 1, tileY + 2)
            "RAILS_TR_D" -> curve(0.0, x - b, y + b, -1, RAILS_HORIZONTAL, -1, tileX - 2, tileY - 1)
            "RAILS_BL_A" -> curve(pi, x + 2 * b, y, -1, RAILS_HORIZONTAL, 1, tileX + 2, tileY + 1)
            "RAILS_BL_D" -> curve(1.5 * pi, x + b, y - b, 1, RAILS_VERTICAL, -1, tileX - 1, tileY - 2)
            "RAILS_BR_B" -> curve(0.0, x - b, y, 1, RAILS_HORIZONTAL, -1, tileX - 2, tileY + 1)
            "RAILS_BR_C" -> curve(1.5 * pi, x, y - b, -1, RAILS_VERTICAL, -1, tileX + 1, tileY - 2)
            else -> {
                car.driving = false
                car.speed = 0
            }
        }
    }

    /**
     * Legacy check_obj_collide() for a train car: it sets off mines, kills soldiers, destroys
     * vehicles at about its height and wrecks other trains that are not its neighbours.
     */
    private fun trainCollisions(car: ServerTrainActor) {
        val r = ServerTrainActor.RADIUS
        for (tank in allTanks) {
            if (tank.armor <= 0 || kotlin.math.abs(tank.height - 1) >= 2) continue
            val reach = r + tank.spec.radius
            val dx = tank.positionX - car.positionX
            val dy = tank.positionY - car.positionY
            if (dx * dx + dy * dy >= reach * reach) continue
            val mx = (car.positionX + tank.positionX) / 2
            val my = (car.positionY + tank.positionY) / 2
            pendingEvents += GameEvent.Effect(EffectKind.EXPLOSION, mx, my)
            pendingEvents += GameEvent.Sound(com.tankarena.protocol.snapshot.SoundKind.EXPLODE, mx, my)
            tank.queueDestroy()
        }
        for (other in trains) {
            if (other === car || !other.alive || other === car.front || other === car.rear) continue
            val dx = other.positionX - car.positionX
            val dy = other.positionY - car.positionY
            if (dx * dx + dy * dy < (2 * r) * (2 * r)) removeTrain(other)
        }
        for (mine in actorManager.allActors.value) {
            if (mine !is ServerMineActor || !mine.isActive) continue
            val reach = r + MINE_RADIUS_PX
            val dx = mine.positionX - car.positionX
            val dy = mine.positionY - car.positionY
            if (dx * dx + dy * dy < reach * reach) mine.detonateOn(null)
        }
        for (soldier in soldiers) {
            if (!soldier.removed && withinRadius(soldier.x, soldier.y, car.positionX, car.positionY, r + SOLDIER_RADIUS_PX)) removeSoldier(soldier)
        }
        soldiers.removeAll { it.removed }
    }

    /** Legacy hit_train(). */
    private fun hitTrain(car: ServerTrainActor, power: Int) {
        if (!car.alive) return
        car.armor -= power
        if (car.armor <= 0) removeTrain(car)
    }

    /**
     * Legacy remove_train(): the car explodes, its turret goes with it, an A-bomb wagon goes off,
     * and the car behind it now leads what is left of the train.
     */
    private fun removeTrain(car: ServerTrainActor) {
        if (!car.alive) return
        car.alive = false
        car.armor = 0
        car.turret?.fall()
        if (car.picture == ABOMB_WAGON) abombs += Abomb(nextActorId++, car.positionX, car.positionY, countdown = 0, owner = null)
        repeat(5) {
            pendingEvents += GameEvent.Effect(EffectKind.EXPLOSION, car.positionX - 8 + trainRandom.nextInt(16), car.positionY - 8 + trainRandom.nextInt(16))
        }
        modifyGoalCounter(car.goalContribution)
        car.goalContribution = 0
        car.front?.let { if (it.rear === car) it.rear = null }
        car.rear?.let {
            it.driving = true
            it.front = null
        }
    }

    /** Legacy control_zeppelin(): drift west at 10 px/s and come back in from the east. */
    private fun stepZeppelins() {
        for (zep in actorManager.allActors.value.filterIsInstance<ServerZeppelinActor>()) {
            if (!zep.alive) continue
            zep.exactX -= ZEPPELIN_SPEED * 0.01f
            if (zep.exactX - LEGACY_TILE_SIZE / 2 < -4 * LEGACY_TILE_SIZE) {
                zep.exactX = ((mapMetadata.widthTiles + 4) * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2).toFloat()
                zep.positionY = airRandom.nextInt(worldHeightPixels) + LEGACY_TILE_SIZE / 2
            }
            zep.positionX = Math.round(zep.exactX)
            zep.body.position = SceneOffset(
                (zep.positionX - ZEPPELIN_FOOTPRINT / 2).toFloat().sceneUnit,
                (zep.positionY - ZEPPELIN_FOOTPRINT / 2).toFloat().sceneUnit,
            )
            if (--zep.frameCooldownTicks <= 0) {
                zep.frame = zep.frame xor 1
                zep.frameCooldownTicks = 100
            }
        }
    }

    // ---- B52 bombers (src/objects/b52.c) ----

    private var b52s: List<ServerB52Actor> = emptyList()
    private val airRandom = kotlin.random.Random(mapMetadata.widthTiles * 523 + mapMetadata.heightTiles * 97)

    /** Legacy prepare_b52(): a bomber waits 500..1000 ticks before it starts its take-off run. */
    private fun prepareB52s() {
        b52s = actorManager.allActors.value.filterIsInstance<ServerB52Actor>()
        for (b52 in b52s) {
            b52.moveTo(b52.positionX.toFloat(), b52.positionY.toFloat())
            b52.speedCooldownTicks = 500 + airRandom.nextInt(500)
        }
    }

    private fun stepB52s() {
        for (b52 in b52s) {
            if (!b52.alive) continue
            when (b52.phase) {
                ServerB52Actor.Phase.LIFTING -> liftB52(b52)
                ServerB52Actor.Phase.FLYING -> flyB52(b52)
                ServerB52Actor.Phase.CRASHING -> crashB52(b52)
                // Legacy control_b52_wreck(): the wreck burns for 300 ticks.
                ServerB52Actor.Phase.WRECK -> if (b52.explosionCooldownTicks > 0) {
                    b52.explosionCooldownTicks -= 1
                    pendingEvents += GameEvent.Effect(EffectKind.EXPLOSION, b52.positionX - 20 + airRandom.nextInt(40), b52.positionY - 20 + airRandom.nextInt(40))
                }
            }
        }
    }

    /** Count a legacy `tinit()` timer down; true when it has run out. */
    private fun ready(ticks: Int): Boolean = ticks <= 0

    /** Legacy lift_b52(): speed up by 1 px/s every 80 ticks to 25, and climb every 60 ticks above 20 px/s. */
    private fun liftB52(b52: ServerB52Actor) {
        controlB52(b52)
        if (b52.phase != ServerB52Actor.Phase.LIFTING) return
        b52.speedCooldownTicks -= 1
        b52.heightCooldownTicks -= 1
        if (ready(b52.speedCooldownTicks) && b52.speed < 25) {
            b52.speed += 1
            b52.speedCooldownTicks = 80
        }
        if (ready(b52.heightCooldownTicks) && b52.speed > 20) {
            b52.height += 1
            b52.heightCooldownTicks = 60
        }
        if (b52.height == ServerB52Actor.CRUISE_HEIGHT) b52.phase = ServerB52Actor.Phase.FLYING
    }

    /**
     * Legacy control_b52(): fly on along the heading; collide with vehicles, trains and other
     * bombers at about the same height (all of them destroyed); low down, a building is fatal.
     */
    private fun controlB52(b52: ServerB52Actor) {
        // Legacy control_b52(): on night maps the navigation lights blink, 10 ticks on, 200..300 off.
        if (mapMetadata.night) {
            b52.lightCooldownTicks -= 1
            if (ready(b52.lightCooldownTicks)) {
                b52.lightOn = !b52.lightOn
                b52.lightCooldownTicks = if (b52.lightOn) 10 else 200 + airRandom.nextInt(100)
            }
        }
        val (fx, fy) = com.tankarena.core.LegacyDirections.unitVector(b52.heading * 4)
        b52.moveTo(b52.exactX + fx * b52.speed * 0.01f, b52.exactY + fy * b52.speed * 0.01f)
        val x = b52.positionX
        val y = b52.positionY
        val r = ServerB52Actor.RADIUS
        fun crash(otherX: Int, otherY: Int) {
            val mx = (x + otherX) / 2
            val my = (y + otherY) / 2
            pendingEvents += GameEvent.Effect(EffectKind.EXPLOSION, mx, my)
            pendingEvents += GameEvent.Sound(com.tankarena.protocol.snapshot.SoundKind.EXPLODE, mx, my)
            hitB52(b52, StructureGrid.INDESTRUCTIBLE)
        }
        for (tank in allTanks) {
            if (kotlin.math.abs(tank.height - b52.height) >= 2) continue
            if (!withinRadius(tank.positionX, tank.positionY, x, y, r + tank.spec.radius - 1)) continue
            tank.queueDestroy()
            crash(tank.positionX, tank.positionY)
        }
        if (kotlin.math.abs(1 - b52.height) < 2) {
            for (car in trains) {
                if (!car.alive || !withinRadius(car.positionX, car.positionY, x, y, r + ServerTrainActor.RADIUS - 1)) continue
                hitTrain(car, StructureGrid.INDESTRUCTIBLE)
                crash(car.positionX, car.positionY)
            }
        }
        for (other in b52s) {
            if (other === b52 || !other.alive || kotlin.math.abs(other.height - b52.height) >= 2) continue
            if (!withinRadius(other.positionX, other.positionY, x, y, 2 * r - 1)) continue
            hitB52(other, StructureGrid.INDESTRUCTIBLE)
            crash(other.positionX, other.positionY)
        }
        if (b52.height <= 1) {
            for (soldier in soldiers) if (!soldier.removed && withinRadius(soldier.x, soldier.y, x, y, r + SOLDIER_RADIUS_PX - 1)) removeSoldier(soldier)
            soldiers.removeAll { it.removed }
            for (mine in actorManager.allActors.value) {
                if (mine is ServerMineActor && mine.isActive && withinRadius(mine.positionX, mine.positionY, x, y, r + MINE_RADIUS_PX - 1)) mine.detonateOn(null)
            }
        }
        if (b52.height <= 3) {
            val tileX = Math.floorDiv(x, LEGACY_TILE_SIZE)
            val tileY = Math.floorDiv(y, LEGACY_TILE_SIZE)
            val layer = structures.blastLayerAt(tileX, tileY)
            if (layer != null) {
                pendingEvents += GameEvent.Effect(EffectKind.EXPLOSION, x, y)
                hitStructure(tileX, tileY, layer, HitKind.NORMAL, StructureGrid.INDESTRUCTIBLE, EffectKind.EXPLOSION)
                hitB52(b52, StructureGrid.INDESTRUCTIBLE)
            }
        }
    }

    /**
     * Legacy fly_b52(): leaving the map, the bomber comes back over a random edge on a random
     * heading; every 650 ticks it opens its bay (four frames of 30 ticks) and drops ten bombs of
     * power 20, which hit a building or a vehicle under them, or else may leave a crater.
     */
    private fun flyB52(b52: ServerB52Actor) {
        val left = b52.positionX - LEGACY_TILE_SIZE / 2
        val top = b52.positionY - LEGACY_TILE_SIZE / 2
        controlB52(b52)
        if (b52.phase != ServerB52Actor.Phase.FLYING) return
        val b = LEGACY_TILE_SIZE
        if (left > (mapMetadata.widthTiles + 3) * b || left < -3 * b || top > (mapMetadata.heightTiles + 3) * b || top < -3 * b) {
            resetB52(b52)
            return
        }
        b52.bombCooldownTicks -= 1
        if (!ready(b52.bombCooldownTicks)) return
        b52.bomb = (b52.bomb + 1) % 5
        b52.bombCooldownTicks = if (b52.bomb == 4) 500 else 30
        if (b52.bomb != 4) return
        b52.bombCount += 1
        for (offset in B52_BOMBS) {
            val (dx, dy) = rotate(b52.heading * 4, offset)
            val x = b52.positionX + dx
            val y = b52.positionY + dy
            if (bombStructure(x, y) || bombObjects(b52, x, y)) continue
            pendingEvents += GameEvent.Effect(EffectKind.EXPLOSION, x, y)
            if (airRandom.nextInt(10) == 0) {
                val tileX = Math.floorDiv(x, LEGACY_TILE_SIZE)
                val tileY = Math.floorDiv(y, LEGACY_TILE_SIZE)
                if (structures.placeCrater(tileX, tileY)) {
                    val i = tileX + tileY * mapMetadata.widthTiles
                    terrainGrid.update(tileX, tileY, TerrainGridBuilder.tileFor(mapMetadata.world, structures.base[i], structures.solid[i]))
                }
            }
        }
    }

    /** Legacy check_wall_hit_point(..., HT_BOMB, 20): a bomb on an opaque pixel of a structure hits it. */
    private fun bombStructure(x: Int, y: Int): Boolean {
        val layer = structures.hitLayerAt(x, y) ?: return false
        pendingEvents += GameEvent.Effect(EffectKind.EXPLOSION, x, y)
        pendingEvents += GameEvent.Sound(com.tankarena.protocol.snapshot.SoundKind.EXPLODE, x, y)
        hitStructure(Math.floorDiv(x, LEGACY_TILE_SIZE), Math.floorDiv(y, LEGACY_TILE_SIZE), layer, HitKind.NORMAL, B52_BOMB_POWER, EffectKind.EXPLOSION)
        return true
    }

    /**
     * Legacy check_obj_hit_point(..., HT_BOMB, 20): a vehicle under the bomb takes it (and stops
     * it); soldiers, bonuses, trains and low bombers within their radius are hit as well.
     */
    private fun bombObjects(bomber: ServerB52Actor, x: Int, y: Int): Boolean {
        fun hitAt(px: Int, py: Int) {
            pendingEvents += GameEvent.Effect(EffectKind.EXPLOSION, px, py)
            pendingEvents += GameEvent.Sound(com.tankarena.protocol.snapshot.SoundKind.EXPLODE, px, py)
        }
        for (tank in allTanks) {
            if (!withinRadius(tank.positionX, tank.positionY, x, y, tank.spec.radius)) continue
            hitAt(x, y)
            tank.queueDamage(B52_BOMB_POWER)
            return true
        }
        for (soldier in soldiers) if (!soldier.removed && withinRadius(soldier.x, soldier.y, x, y, SOLDIER_RADIUS_PX - 1)) {
            hitAt(x, y)
            removeSoldier(soldier)
        }
        for (bonus in bonuses) if (!bonus.removed && withinRadius(bonus.x, bonus.y, x, y, BONUS_RADIUS_PX - 1)) destroyBonus(bonus)
        for (car in trains) if (car.alive && withinRadius(car.positionX, car.positionY, x, y, ServerTrainActor.RADIUS - 1)) {
            hitAt(x, y)
            hitTrain(car, B52_BOMB_POWER)
        }
        for (other in b52s) if (other !== bomber && other.alive && other.height < 8 && withinRadius(other.positionX, other.positionY, x, y, ServerB52Actor.RADIUS - 1)) {
            hitAt(x, y)
            hitB52(other, B52_BOMB_POWER)
        }
        soldiers.removeAll { it.removed }
        bonuses.removeAll { it.removed }
        return false
    }

    /** Legacy reset_b52(): back in over a random edge on a random heading. */
    private fun resetB52(b52: ServerB52Actor) {
        val b = LEGACY_TILE_SIZE
        b52.heading = airRandom.nextInt(4)
        val (left, top) = when (b52.heading) {
            0 -> airRandom.nextInt(worldWidthPixels) to (mapMetadata.heightTiles + 2) * b
            1 -> -2 * b to airRandom.nextInt(worldHeightPixels)
            2 -> airRandom.nextInt(worldWidthPixels) to -2 * b
            else -> (mapMetadata.widthTiles + 2) * b to airRandom.nextInt(worldHeightPixels)
        }
        b52.moveTo((left + b / 2).toFloat(), (top + b / 2).toFloat())
    }

    /** Legacy hit_b52() and hit_b52_wreck(). */
    private fun hitB52(b52: ServerB52Actor, power: Int) {
        if (!b52.alive) return
        when (b52.phase) {
            ServerB52Actor.Phase.WRECK -> if (b52.explosionCooldownTicks <= 0) {
                repeat(10) {
                    pendingEvents += GameEvent.Effect(EffectKind.EXPLOSION, b52.positionX - 20 + airRandom.nextInt(40), b52.positionY - 20 + airRandom.nextInt(40))
                }
                b52.alive = false
            }
            else -> {
                b52.armor -= power
                if (b52.armor <= 0) {
                    b52.armor = 0
                    b52.bomb = 4
                    b52.phase = ServerB52Actor.Phase.CRASHING
                }
            }
        }
    }

    /**
     * Legacy crash_b52(): a shot-down bomber flies on, sinking one step every 60 ticks with an
     * engine burning, and explodes the last bit of the way; on the ground it is a burning wreck
     * and its goal share counts.
     */
    private fun crashB52(b52: ServerB52Actor) {
        controlB52(b52)
        b52.heightCooldownTicks -= 1
        if (ready(b52.heightCooldownTicks)) {
            b52.height -= 1
            b52.heightCooldownTicks = 60
        }
        if (b52.height <= 2) {
            pendingEvents += GameEvent.Effect(EffectKind.EXPLOSION, b52.positionX - 20 + airRandom.nextInt(40), b52.positionY - 20 + airRandom.nextInt(40))
        }
        if (b52.height <= 0) {
            b52.height = 0
            b52.phase = ServerB52Actor.Phase.WRECK
            modifyGoalCounter(b52.goalContribution)
            b52.goalContribution = 0
            b52.explosionCooldownTicks = 300
            return
        }
        b52.explosionCooldownTicks -= 1
        if (ready(b52.explosionCooldownTicks)) {
            val (ex, ey) = rotate(b52.heading * 4, B52_ENGINES[airRandom.nextInt(4)])
            pendingEvents += GameEvent.Effect(EffectKind.EXPLOSION, b52.positionX + ex, b52.positionY + ey)
            b52.explosionCooldownTicks = 40
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
            pickup.showMessage("PICKED UP", PRODUCT_NAMES.getOrElse(product.productType) { "" })
            pendingEvents += GameEvent.ProductCollected(
                productActorId = actorIds[product] ?: 0L,
                tankActorId = actorIds[pickup]!!,
                price = product.price,
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
                    if (e is GameEvent.DamageTaken || e is GameEvent.TankDestroyed) announceHit(actor)
                    if (e !is GameEvent.TankDestroyed) continue
                    // Legacy hit_tank(): every other side's tanks may refill at their base again.
                    for (other in allTanks) {
                        if (other.playerIndex != actor.playerIndex) other.allowBaseRefill()
                    }
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
     * Legacy hit_tank(): outside player-vs-player, the players hear when one of their other
     * drivable tanks (those with an F1–F10 id) is hit.
     */
    private fun announceHit(tank: ServerTankActor) {
        if (mode == MatchMode.DUAL || tank.controllerId < 0) return
        for (viewed in tanksByPlayerIndex.values) {
            if (viewed !== tank) viewed.showMessage("TANK ${tank.controllerId} HAS", "BEEN HIT!")
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
            val rules = dualRules ?: return
            val flags = all.filterIsInstance<ServerFlagActor>()
            val decided = rules.step(allTanks, flags) { car ->
                pendingEvents += GameEvent.Sound(com.tankarena.protocol.snapshot.SoundKind.BEEP, car.positionX, car.positionY)
            }
            if (decided) {
                pendingEvents += GameEvent.RoundWon(rules.winner)
                endMission(rules.winner == 0, "ROUND_WON_BY_PLAYER_${rules.winner + 1}")
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

    /** Legacy control_computer_tank() for every computer tank (ComputerTankAi). */
    private fun stepAiTanks() {
        aiObjectsTick = -1
        for (ai in allTanks) {
            if (ai.playerIndex >= 0 || !ai.computer || ai.armor <= 0) continue
            ai.applyIntent(ComputerTankAi.control(ai, aiWorld))
        }
    }

    private var aiObjectsCache: List<AiObject> = emptyList()
    private var aiObjectsTick = -1L

    /** The world as control_computer_tank() reads it. */
    private val aiWorld = object : ComputerTankWorld {
        override val tick: Long get() = currentTick
        override val widthTiles: Int get() = mapMetadata.widthTiles
        override val heightTiles: Int get() = mapMetadata.heightTiles

        override val objects: List<AiObject>
            get() {
                if (aiObjectsTick != currentTick) {
                    aiObjectsCache = aiObjects()
                    aiObjectsTick = currentTick
                }
                return aiObjectsCache
            }

        override fun structureGoal(tileX: Int, tileY: Int): Int = structures.standingGoal(tileX, tileY)

        override fun pitOrLava(x: Int, y: Int): Boolean {
            val tileX = Math.floorDiv(x, LEGACY_TILE_SIZE)
            val tileY = Math.floorDiv(y, LEGACY_TILE_SIZE)
            if (tileX !in 0 until mapMetadata.widthTiles || tileY !in 0 until mapMetadata.heightTiles) return false
            val tile = terrainGrid.at(tileX, tileY)
            if (tile.material == TerrainMaterial.LAVA) return true
            return inPit(tile, x - tileX * LEGACY_TILE_SIZE, y - tileY * LEGACY_TILE_SIZE)
        }

        override fun terrainSpeedPercent(x: Int, y: Int): Int {
            val tileX = Math.floorDiv(x, LEGACY_TILE_SIZE)
            val tileY = Math.floorDiv(y, LEGACY_TILE_SIZE)
            if (tileX !in 0 until mapMetadata.widthTiles || tileY !in 0 until mapMetadata.heightTiles) return 100
            val i = tileX + tileY * mapMetadata.widthTiles
            var speed = pictureSpeed(structures.base[i])
            if (structures.solid[i] >= 0 && !structures.isStanding(tileX, tileY)) speed *= pictureSpeed(structures.solid[i])
            return (speed * 100).toInt()
        }

        private fun pictureSpeed(picture: Int): Float =
            if (picture < 0) 1f else com.tankarena.content.PictureCatalog.legacyRow(mapMetadata.world, picture)?.speed ?: 1f
    }

    /**
     * The legacy object list as the computer tanks see it: every object with its height `h`,
     * radius `r` (0 for the ones nothing bumps into) and goal value `gc`. Wrecks stay obstacles.
     */
    private fun aiObjects(): List<AiObject> {
        val list = ArrayList<AiObject>()
        for (actor in actorManager.allActors.value) {
            val (x, y) = centreOf(actor) ?: continue
            when (actor) {
                is ServerTankActor -> list += AiObject(actor, x, y, actor.height, actor.spec.radius, tank = actor)
                is ServerTrainActor -> if (actor.alive) list += AiObject(actor, x, y, 1, ServerTrainActor.RADIUS, goal = actor.goalContribution)
                is ServerB52Actor -> if (actor.alive) list += AiObject(actor, x, y, actor.height, ServerB52Actor.RADIUS, goal = actor.goalContribution)
                is ServerZeppelinActor -> if (actor.alive) list += AiObject(actor, x, y, 20, 20)
                is ServerMineActor -> if (!actor.isDead) list += AiObject(actor, x, y, 0, 3)
                is ServerRocketActor -> list += AiObject(actor, x, y, 6, 3)
                is ServerTurretActor -> if (actor.isAlive) list += AiObject(actor, x, y, 1, 0, goal = actor.goalContribution)
                is ServerGoalActor -> if (!actor.isClaimed) {
                    list += AiObject(actor, x, y, 24, 0, goal = actor.contribution, privateGoal = actor.who == 3)
                }
                is ServerLockActor -> if (!actor.isFired) list += AiObject(actor, x, y, 24, 0, goal = actor.goalContribution)
                is ServerDestroyerActor -> if (!actor.isFired) list += AiObject(actor, x, y, 24, 0, goal = actor.goalContribution)
                is ServerEnforcerActor -> list += AiObject(actor, x, y, 24, 0)
                is ServerFlagActor -> list += AiObject(actor, x, y, 0, 4)
                is ServerProductActor -> list += AiObject(actor, x, y, 0, 4)
                else -> Unit
            }
        }
        for (soldier in soldiers) if (!soldier.removed) list += AiObject(soldier, soldier.x, soldier.y, 0, 5)
        for (bonus in bonuses) if (!bonus.removed) list += AiObject(bonus, bonus.x, bonus.y, 0, 2)
        for (bomb in abombs) if (!bomb.removed) list += AiObject(bomb, bomb.x, bomb.y, 0, 3)
        return list
    }

    /** What a tank needs to know about the world it drives in (legacy new_position()). */
    private val tankSurroundings = object : TankSurroundings {
        override val structures: StructureGrid get() = this@ServerMatchPrototype.structures
        override val worldWidth: Int get() = worldWidthPixels
        override val worldHeight: Int get() = worldHeightPixels
        override val tick: Long get() = currentTick
        override fun tanks(): List<ServerTankActor> = allTanks
        override fun baseType(tileX: Int, tileY: Int): String? {
            if (tileX !in 0 until mapMetadata.widthTiles || tileY !in 0 until mapMetadata.heightTiles) return null
            return com.tankarena.content.PictureCatalog.legacyRow(mapMetadata.world, structures.base[tileX + tileY * mapMetadata.widthTiles])?.type
        }
        override fun trainAt(x: Int, y: Int, radius: Int, height: Int): Boolean = trains.any { car ->
            car.alive && kotlin.math.abs(height - 1) < 2 &&
                (car.positionX - x) * (car.positionX - x) + (car.positionY - y) * (car.positionY - y) <
                (radius + ServerTrainActor.RADIUS) * (radius + ServerTrainActor.RADIUS)
        } || b52s.any { b52 ->
            // Legacy check_obj_collide(): a bomber (or its wreck) is an obstacle too.
            b52.alive && kotlin.math.abs(height - b52.height) < 2 &&
                (b52.positionX - x) * (b52.positionX - x) + (b52.positionY - y) * (b52.positionY - y) <
                (radius + ServerB52Actor.RADIUS) * (radius + ServerB52Actor.RADIUS)
        }
        override fun damageStructure(tileX: Int, tileY: Int, layer: StructureLayer, kind: HitKind, power: Int, by: ServerTankActor?) {
            hitStructure(tileX, tileY, layer, kind, power, explosion = EffectKind.RUIN, by = by)
        }
    }

    /**
     * Legacy hit_wall_for()/hit_wall(): damage a structure and play out what destroying it does —
     * collapse effects, the tile's goal-counter share, bridges turning into water, burning fuel
     * dumps, and turrets falling with the building under them.
     */
    private fun hitStructure(
        tileX: Int,
        tileY: Int,
        layer: StructureLayer,
        kind: HitKind,
        power: Int,
        explosion: EffectKind,
        /** Legacy HT_ABOMB: buildings an A-bomb flattens fall without the collapse sound and debris. */
        silent: Boolean = false,
        /** The vehicle that did it (legacy `owner`), whom paratroopers from the building go after. */
        by: ServerTankActor? = null,
        /** Legacy HT_REMOVE: a lock takes even an indestructible structure down. */
        remove: Boolean = false,
    ) {
        for (destroyed in structures.hit(tileX, tileY, layer, kind, power, remove)) {
            onStructureDestroyed(destroyed, explosion, silent, by)
        }
    }

    private fun onStructureDestroyed(destroyed: DestroyedStructure, hitExplosion: EffectKind, silent: Boolean, by: ServerTankActor?) {
        val centreX = destroyed.tileX * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2
        val centreY = destroyed.tileY * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2
        if (!silent && destroyed.legacyType != "BUSH_SIDE") {
            pendingEvents += GameEvent.Sound(com.tankarena.protocol.snapshot.SoundKind.COLLAPSE, centreX, centreY)
            val effect = when (destroyed.explosion) {
                "RUIN" -> if (hitExplosion == EffectKind.CHAIN) EffectKind.RUIN else hitExplosion
                "EXPLOSION" -> EffectKind.EXPLOSION
                "SPLASH" -> EffectKind.SPLASH
                "FLAME" -> EffectKind.FLAME
                "OIL_SPLASH" -> EffectKind.OIL_SPLASH
                else -> EffectKind.RUIN
            }
            if (effect == EffectKind.OIL_SPLASH) {
                pendingEvents += GameEvent.Effect(effect, centreX, centreY)
            } else {
                repeat(5) {
                    pendingEvents += GameEvent.Effect(effect, centreX - 13 + structureRandom.nextInt(26), centreY - 13 + structureRandom.nextInt(26))
                }
            }
        }
        if (destroyed.layer != StructureLayer.SOLID) return
        modifyGoalCounter(destroyed.goalContribution)
        createBonus(destroyed.tileX, destroyed.tileY)
        dropParatroopers(destroyed.tileX, destroyed.tileY, by)
        when (destroyed.legacyType) {
            "BRIDGE_H", "BRIDGE_V", "BRIDGE_CR" -> terrainGrid.update(
                destroyed.tileX,
                destroyed.tileY,
                com.tankarena.content.PictureCatalog.resolve(mapMetadata.world, structures.base[destroyed.tileX + destroyed.tileY * mapMetadata.widthTiles]),
            )
            // Legacy create_parts(PT_FLAME): a fuel dump goes up in flames.
            "FUEL_DUMP" -> repeat(8) {
                pendingEvents += GameEvent.Effect(EffectKind.FLAME, centreX - 24 + structureRandom.nextInt(48), centreY - 24 + structureRandom.nextInt(48))
            }
            // Legacy create_abomb(0, x2 + 16, y2 + 16, 0): an A-bomb box goes off at once. The
            // original passes the tile's centre as the bomb's top-left, so the blast centre lies
            // half a tile right of and below the box.
            "ABOX" -> abombs += Abomb(nextActorId++, centreX + LEGACY_TILE_SIZE / 2, centreY + LEGACY_TILE_SIZE / 2, countdown = 0, owner = null)
        }
        removeFallenTurrets()
    }

    private val structureRandom = kotlin.random.Random(mapMetadata.widthTiles * 31 + mapMetadata.heightTiles)

    /**
     * Legacy control_turret(): a map turret stands on a building and is removed (applying its
     * goal contribution) as soon as that building is gone.
     */
    private fun removeFallenTurrets() {
        for (turret in actorManager.allActors.value) {
            if (turret !is ServerTurretActor || !turret.isAlive || turret in ridingTurrets) continue
            val tileX = turret.positionX / LEGACY_TILE_SIZE
            val tileY = turret.positionY / LEGACY_TILE_SIZE
            if (!structures.isStanding(tileX, tileY)) turret.fall()
        }
    }

    /**
     * Legacy control_bullets() with line_wall_hit_point(): bullets and flames that cross an opaque
     * pixel of a structure stop there and damage it. A turret's own shots leave its building
     * (they ignore structures within two tiles of it).
     */
    private fun checkProjectileStructureHits() {
        for (projectile in actorManager.allActors.value) {
            // Legacy control_bullets(): a small rocket within its safety distance passes everything.
            if (projectile !is ServerProjectileActor || projectile.isDead || !projectile.armed) continue
            val turret = projectile.ownerTurretRef
            val (fromX, fromY) = projectile.previousPosition
            val toX = projectile.positionX
            val toY = projectile.positionY
            val steps = maxOf(kotlin.math.abs(toX - fromX), kotlin.math.abs(toY - fromY)).coerceAtLeast(1)
            for (step in 1..steps) {
                val x = fromX + (toX - fromX) * step / steps
                val y = fromY + (toY - fromY) * step / steps
                if (turret != null && turret !in ridingTurrets && withinRadius(turret.positionX, turret.positionY, x, y, 2 * LEGACY_TILE_SIZE)) continue
                val layer = structures.hitLayerAt(x, y) ?: continue
                val kind = if (projectile.weaponKind == WEAPON_FLAME) HitKind.FLAME else HitKind.NORMAL
                val effect = when (projectile.weaponKind) {
                    WEAPON_CHAIN -> EffectKind.CHAIN
                    WEAPON_FLAME -> EffectKind.FLAME
                    else -> EffectKind.EXPLOSION
                }
                val tileX = Math.floorDiv(x, LEGACY_TILE_SIZE)
                val tileY = Math.floorDiv(y, LEGACY_TILE_SIZE)
                hitStructure(tileX, tileY, layer, kind, projectile.damage, effect, by = projectile.ownerRef)
                // Legacy control_bullets(): a shot (not a flame) that hits a force field still
                // standing bounces off it.
                val bounce = if (projectile.weaponKind != WEAPON_FLAME && structures.isStanding(tileX, tileY)) {
                    fieldBounce(tileX, tileY, projectileDirection(projectile))
                } else {
                    null
                }
                if (bounce != null) projectile.deflect(x, y, bounce) else projectile.stopAt(x, y)
                break
            }
        }
    }

    /**
     * Legacy line_obj_hit_point() (src/check/hitobj.c) for the vehicles: a shot that got past the
     * walls this tick hits the first vehicle, in object order, whose body picture lies on its path.
     * The original walks the path from its start one pixel per step diagonally (both axes move by
     * their sign) for as many steps as the longer axis, plus one. Vehicles sinking into a pit
     * (height below 1) are only hit by rockets, which are not shots here; flames burn by radius
     * (triggerFlameContacts()).
     */
    private fun checkProjectileVehicleHits() {
        for (projectile in actorManager.allActors.value) {
            if (projectile !is ServerProjectileActor || projectile.isDead || !projectile.armed) continue
            if (projectile.weaponKind == WEAPON_FLAME) continue
            val (fromX, fromY) = projectile.previousPosition
            val dx = projectile.positionX - fromX
            val dy = projectile.positionY - fromY
            val steps = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
            val stepX = Integer.signum(dx)
            val stepY = Integer.signum(dy)
            for (tank in allTanks) {
                if (projectile.ownerKind == ProjectileOwnerKind.TANK && tank === projectile.ownerRef) continue
                if (tank.armor <= 0 || tank.height < 1) continue
                var x = fromX
                var y = fromY
                var hit = false
                repeat(steps + 1) {
                    if (hit) return@repeat
                    x += stepX
                    y += stepY
                    if (tank.bodyCovers(x, y)) hit = true
                }
                if (hit) {
                    projectile.hitVehicle(tank, x, y)
                    break
                }
            }
        }
    }

    /** The 16-step direction a projectile flies in. */
    private fun projectileDirection(projectile: ServerProjectileActor): Int =
        com.tankarena.core.LegacyDirections.fromAngle((projectile.velocityX * 1000).roundToInt(), (projectile.velocityY * 1000).roundToInt())

    /**
     * Legacy control_bullets(): the four force-field pictures mirror a shot — horizontal
     * (`8 - dir`), vertical (`-dir`) and the two diagonals (`4 - dir`, `12 - dir`). Null when the
     * tile's solid picture is no force field.
     */
    private fun fieldBounce(tileX: Int, tileY: Int, direction: Int): Int? {
        val picture = structures.solid.getOrNull(tileX + tileY * mapMetadata.widthTiles) ?: return null
        return when (com.tankarena.content.PictureCatalog.legacyRow(mapMetadata.world, picture)?.type) {
            "FIELD_H" -> (8 - direction).mod(16)
            "FIELD_V" -> (0 - direction).mod(16)
            "FIELD_P" -> (4 - direction).mod(16)
            "FIELD_N" -> (12 - direction).mod(16)
            else -> null
        }
    }

    /** Legacy check_wall_hit() at a world point: wears down the top or else the solid structure there. */
    private fun blastStructureAt(
        x: Int,
        y: Int,
        power: Int,
        explosion: EffectKind,
        silent: Boolean = false,
        by: ServerTankActor? = null,
    ) {
        val tileX = Math.floorDiv(x, LEGACY_TILE_SIZE)
        val tileY = Math.floorDiv(y, LEGACY_TILE_SIZE)
        val layer = structures.blastLayerAt(tileX, tileY) ?: return
        hitStructure(tileX, tileY, layer, HitKind.NORMAL, power, explosion, silent, by)
    }

    /**
     * Legacy create_mortar(): the blast grows from 5 to 60 px, and each step (hit_wall_big())
     * wears 5 off every structure within the radius plus 16.
     */
    private fun blastStructuresAround(x: Int, y: Int, by: ServerTankActor?) {
        for (radius in MORTAR_MIN_RADIUS_PX..MORTAR_MAX_RADIUS_PX step 5) {
            var y2 = y - radius
            while (y2 < y + radius) {
                var x2 = x - radius
                while (x2 < x + radius) {
                    if (withinRadius(x2, y2, x, y, radius + 16)) blastStructureAt(x2, y2, MORTAR_STEP_POWER, EffectKind.EXPLOSION, by = by)
                    x2 += LEGACY_TILE_SIZE
                }
                y2 += LEGACY_TILE_SIZE
            }
        }
    }

    // ---- Bonuses, soldiers and A-bombs (src/objects/bonus.c, man.c, abomb.c) ----

    /** A tile of the map's bonus layer (`mapm.b`, a signed char: 255 is -1, no bonus). */
    private fun layerBonus(tileX: Int, tileY: Int): Int {
        val value = layers?.bonusLayer?.getOrNull(tileX + tileY * mapMetadata.widthTiles) ?: return -1
        return if (value > 127) value - 256 else value
    }

    /** Legacy create_all_bonusses(): without random bonuses, the bonus layer's bonuses lie on every open tile. */
    private fun createAllBonuses() {
        if (mapMetadata.randomBonus) return
        for (tileY in 0 until mapMetadata.heightTiles) {
            for (tileX in 0 until mapMetadata.widthTiles) {
                if (!structures.isStanding(tileX, tileY)) makeBonus(tileX, tileY, layerBonus(tileX, tileY))
            }
        }
    }

    /** Legacy create_bonus(): a destroyed building leaves a random bonus (12.5 % of the time) or the layer's. */
    private fun createBonus(tileX: Int, tileY: Int) {
        if (!mapMetadata.randomBonus) {
            makeBonus(tileX, tileY, layerBonus(tileX, tileY))
            return
        }
        val roll = objectRandom.nextInt(10000)
        makeBonus(tileX, tileY, RANDOM_BONUS_THRESHOLDS.indexOfFirst { roll < it })
    }

    /** Legacy make_bonus(): a bonus within 4 px of the tile's centre. */
    private fun makeBonus(tileX: Int, tileY: Int, type: Int) {
        if (type < 0) return
        val x = tileX * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2 - 4 + objectRandom.nextInt(8)
        val y = tileY * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2 - 4 + objectRandom.nextInt(8)
        bonuses += Bonus(nextActorId++, x, y, type)
    }

    /** Legacy hit_bonus(): anything that hits a bonus blows it up. */
    private fun destroyBonus(bonus: Bonus) {
        if (bonus.removed) return
        bonus.removed = true
        pendingEvents += GameEvent.Effect(EffectKind.EXPLOSION, bonus.x, bonus.y)
    }

    /** Legacy remove_man(): a soldier who goes leaves blood and a scream, however he goes. */
    private fun removeSoldier(soldier: Soldier) {
        if (soldier.removed) return
        soldier.removed = true
        pendingEvents += GameEvent.Effect(EffectKind.BLOOD, soldier.x, soldier.y)
        pendingEvents += GameEvent.Sound(com.tankarena.protocol.snapshot.SoundKind.SCREAM, soldier.x, soldier.y)
    }

    /** Legacy nearest_target(): the vehicle closest to ([x], [y]) within 15 px, which a soldier follows. */
    private fun vehicleNear(x: Int, y: Int): ServerTankActor? = allTanks
        .filter { withinRadius(it.positionX, it.positionY, x, y, ROCKET_TARGET_RADIUS_PX) }
        .minByOrNull { (it.positionX - x).toLong() * (it.positionX - x) + (it.positionY - y).toLong() * (it.positionY - y) }

    private fun drainSoldierRequests() {
        for (tank in allTanks) {
            for (request in tank.drainSoldierRequests()) {
                soldiers += Soldier(
                    id = nextActorId++,
                    x = request.x,
                    y = request.y,
                    targetX = request.targetX,
                    targetY = request.targetY,
                    target = vehicleNear(request.targetX, request.targetY),
                    shotKind = if (request.gunner) WEAPON_CHAIN else WEAPON_FLAME,
                    power = request.power,
                    owner = tank,
                    random = objectRandom,
                )
            }
        }
    }

    /**
     * Legacy hit_wall(): soldiers of the tile's man layer jump out of a destroyed building, around
     * it, and go for whoever destroyed it (or a random spot near it).
     */
    private fun dropParatroopers(tileX: Int, tileY: Int, by: ServerTankActor?) {
        val index = tileX + tileY * mapMetadata.widthTiles
        val count = layers?.manAmountLayer?.getOrNull(index) ?: 0
        if (count <= 0) return
        val hitType = layers?.manTypeLayer?.getOrNull(index) ?: 0
        val left = tileX * LEGACY_TILE_SIZE
        val top = tileY * LEGACY_TILE_SIZE
        repeat(count) {
            val x = left + objectRandom.nextInt(32)
            val y = top + objectRandom.nextInt(32)
            val (targetX, targetY) = if (by == null) {
                left + LEGACY_TILE_SIZE / 2 - 100 + objectRandom.nextInt(200) to top + LEGACY_TILE_SIZE / 2 - 100 + objectRandom.nextInt(200)
            } else {
                by.positionX to by.positionY
            }
            soldiers += Soldier(
                id = nextActorId++,
                x = x,
                y = y,
                targetX = targetX,
                targetY = targetY,
                target = vehicleNear(targetX, targetY),
                shotKind = shotKindForHitType(hitType),
                power = if (hitType == HT_SROCK) 5 else 1,
                owner = null,
                random = objectRandom,
            )
        }
    }

    /** The projectile a legacy HT_* type stands for, as the man layer stores it. */
    private fun shotKindForHitType(hitType: Int): Int = when (hitType) {
        0 -> WEAPON_MAIN
        2 -> WEAPON_FLAME
        HT_SROCK -> SHOT_SMALL_ROCKET
        else -> WEAPON_CHAIN
    }

    /**
     * Legacy control_man(): soldiers walk and fire; one who stands on a building (hitting it for
     * 10), in water or in a pit, or who has no rounds left, is gone.
     */
    private fun stepSoldiers() {
        if (soldiers.isEmpty()) return
        for (soldier in soldiers.toList()) {
            if (soldier.removed) continue
            soldier.move()
            val x = soldier.x
            val y = soldier.y
            val tileX = Math.floorDiv(x, LEGACY_TILE_SIZE)
            val tileY = Math.floorDiv(y, LEGACY_TILE_SIZE)
            val onBuilding = structures.solidOpaqueAt(x, y)
            if (onBuilding) hitStructure(tileX, tileY, StructureLayer.SOLID, HitKind.NORMAL, 10, EffectKind.CHAIN)
            val tile = terrainGrid.at(tileX, tileY)
            val sub = { v: Int -> (v % LEGACY_TILE_SIZE + LEGACY_TILE_SIZE) % LEGACY_TILE_SIZE }
            if (soldier.outOfAmmo || onBuilding || tile.material == TerrainMaterial.WATER || inPit(tile, sub(x), sub(y))) {
                removeSoldier(soldier)
                continue
            }
            soldier.fire()?.let { pendingSoldierShots += soldier to it }
        }
        soldiers.removeAll { it.removed }
    }

    private fun drainAbombRequests() {
        for (tank in allTanks) {
            val request = tank.drainAbombRequest() ?: continue
            abombs += Abomb(nextActorId++, request.x, request.y, request.countdown, tank)
        }
    }

    /**
     * Legacy control_abomb(): every blast step wears 4 off the structures (hit_wall_big()) and
     * everything else (check_obj_hit_radius()) within its growing radius, and brightens and then
     * fades the palette.
     */
    private fun stepAbombs() {
        if (abombs.isEmpty()) {
            flash = 0
            return
        }
        for (bomb in abombs.toList()) {
            val blast = bomb.step() ?: continue
            if (blast.first) pendingEvents += GameEvent.Sound(com.tankarena.protocol.snapshot.SoundKind.NUKE, bomb.x, bomb.y)
            flash += blast.flash
            nukeStructures(bomb, blast.radius)
            nukeObjects(bomb, blast.radius)
        }
        abombs.removeAll { it.removed }
        if (abombs.none { it.exploding }) flash = 0
    }

    private fun nukeStructures(bomb: Abomb, radius: Int) {
        var y2 = bomb.y - radius
        while (y2 < bomb.y + radius) {
            var x2 = bomb.x - radius
            while (x2 < bomb.x + radius) {
                if (withinRadius(x2, y2, bomb.x, bomb.y, radius + 16)) {
                    blastStructureAt(x2, y2, Abomb.POWER, EffectKind.EXPLOSION, silent = true, by = bomb.owner)
                }
                x2 += LEGACY_TILE_SIZE
            }
            y2 += LEGACY_TILE_SIZE
        }
        repeat(5) {
            val angle = 2 * Math.PI * objectRandom.nextInt(360) / 360
            pendingEvents += GameEvent.Effect(
                EffectKind.EXPLOSION,
                bomb.x + (radius * Math.cos(angle)).toInt(),
                bomb.y + (radius * Math.sin(angle)).toInt(),
            )
        }
    }

    /** Legacy check_obj_hit_radius(..., HT_ABOMB, 4): vehicles (except in shelters), soldiers and bonuses. */
    private fun nukeObjects(bomb: Abomb, radius: Int) {
        for (tank in allTanks) {
            if (tank.armor <= 0 || tank.height < 1) continue
            if (!withinRadius(tank.positionX, tank.positionY, bomb.x, bomb.y, maxOf(radius, tank.spec.radius))) continue
            if (inShelter(tank)) continue
            tank.queueDamage(Abomb.POWER)
        }
        blastSmallObjects(bomb.x, bomb.y, radius, nuke = true, power = Abomb.POWER)
    }

    /** Legacy hit_tank(): a vehicle on a SHELTER tile is safe from A-bombs. */
    private fun inShelter(tank: ServerTankActor): Boolean {
        val tileX = tank.positionX / LEGACY_TILE_SIZE
        val tileY = tank.positionY / LEGACY_TILE_SIZE
        val picture = structures.solid.getOrNull(tileX + tileY * mapMetadata.widthTiles) ?: return false
        return com.tankarena.content.PictureCatalog.legacyRow(mapMetadata.world, picture)?.type == "SHELTER"
    }

    /** Legacy check_obj_hit_radius() for the small objects: soldiers, bonuses and (not for nukes) A-bombs. */
    private fun blastSmallObjects(x: Int, y: Int, radius: Int, nuke: Boolean = false, power: Int = 0, rocket: Boolean = false) {
        // Legacy determine_hit(): a bomber is only within reach low down, or of a rocket.
        for (b52 in b52s) {
            if (b52.alive && power > 0 && (rocket || b52.height < 8) &&
                withinRadius(b52.positionX, b52.positionY, x, y, maxOf(radius, ServerB52Actor.RADIUS))
            ) {
                hitB52(b52, power)
            }
        }
        // Train cars (r = 12) take the blast's power; mines in it go off too.
        for (car in trains) {
            if (car.alive && power > 0 && withinRadius(car.positionX, car.positionY, x, y, maxOf(radius, ServerTrainActor.RADIUS))) hitTrain(car, power)
        }
        for (mine in actorManager.allActors.value) {
            if (mine is ServerMineActor && !mine.isDead && withinRadius(mine.positionX, mine.positionY, x, y, maxOf(radius, MINE_RADIUS_PX))) {
                mine.detonateOn(null)
            }
        }
        for (soldier in soldiers) {
            if (!soldier.removed && withinRadius(soldier.x, soldier.y, x, y, maxOf(radius, SOLDIER_RADIUS_PX))) removeSoldier(soldier)
        }
        for (bonus in bonuses) {
            if (!bonus.removed && withinRadius(bonus.x, bonus.y, x, y, maxOf(radius, BONUS_RADIUS_PX))) destroyBonus(bonus)
        }
        if (!nuke) {
            for (bomb in abombs) {
                if (!bomb.exploding && withinRadius(bomb.x, bomb.y, x, y, maxOf(radius, ABOMB_RADIUS_PX))) bomb.removed = true
            }
        }
        soldiers.removeAll { it.removed }
        bonuses.removeAll { it.removed }
        abombs.removeAll { it.removed }
    }

    /**
     * Legacy line_obj_hit_point()/line_obj_hit_radius() for the small objects: a shot that
     * passes within a soldier's, bonus's or waiting A-bomb's radius (15 px for flames) hits it
     * and stops. Soldiers of one vehicle do not hit each other.
     */
    private fun checkSmallObjectHits() {
        for (projectile in actorManager.allActors.value) {
            if (projectile !is ServerProjectileActor) continue
            projectile.drainTrail()?.let { (x, y) -> pendingEvents += GameEvent.Effect(EffectKind.EXHAUST, x, y) }
        }
        if (soldiers.isEmpty() && bonuses.isEmpty() && abombs.isEmpty() && trains.isEmpty() && b52s.isEmpty()) return
        for (projectile in actorManager.allActors.value) {
            if (projectile !is ServerProjectileActor || projectile.isDead || !projectile.armed) continue
            val (x1, y1) = projectile.previousPosition
            val x2 = projectile.positionX
            val y2 = projectile.positionY
            val flame = projectile.weaponKind == WEAPON_FLAME
            val shooter = projectile.ownerSoldierRef
            val soldier = soldiers.firstOrNull {
                !it.removed && it !== shooter && !(shooter != null && shooter.owner === it.owner) &&
                    nearSegment(x1, y1, x2, y2, it.x, it.y, if (flame) FLAME_HIT_RADIUS_PX else SOLDIER_RADIUS_PX)
            }
            if (soldier != null) {
                removeSoldier(soldier)
                projectile.stopAt(soldier.x, soldier.y)
                continue
            }
            val bonus = bonuses.firstOrNull {
                !it.removed && nearSegment(x1, y1, x2, y2, it.x, it.y, if (flame) FLAME_HIT_RADIUS_PX else BONUS_RADIUS_PX)
            }
            if (bonus != null) {
                destroyBonus(bonus)
                projectile.stopAt(bonus.x, bonus.y)
                continue
            }
            // Legacy determine_hit(): turret rounds pass through trains.
            val car = if (projectile.ownerTurretRef != null) null else trains.firstOrNull {
                it.alive && nearSegment(x1, y1, x2, y2, it.positionX, it.positionY, if (flame) FLAME_HIT_RADIUS_PX else ServerTrainActor.RADIUS)
            }
            if (car != null) {
                hitTrain(car, projectile.damage)
                projectile.stopAt(x2, y2)
                continue
            }
            val bomber = b52s.firstOrNull {
                it.alive && it.height < 8 &&
                    nearSegment(x1, y1, x2, y2, it.positionX, it.positionY, if (flame) FLAME_HIT_RADIUS_PX else ServerB52Actor.RADIUS)
            }
            if (bomber != null) {
                hitB52(bomber, projectile.damage)
                projectile.stopAt(x2, y2)
                continue
            }
            val bomb = abombs.firstOrNull {
                !it.removed && !it.exploding &&
                    nearSegment(x1, y1, x2, y2, it.x, it.y, if (flame) FLAME_HIT_RADIUS_PX else ABOMB_RADIUS_PX)
            }
            if (bomb != null) {
                bomb.removed = true
                projectile.stopAt(bomb.x, bomb.y)
            }
        }
        soldiers.removeAll { it.removed }
        bonuses.removeAll { it.removed }
        abombs.removeAll { it.removed }
    }

    /** True when ([px], [py]) lies within [radius] of the segment from ([x1], [y1]) to ([x2], [y2]). */
    private fun nearSegment(x1: Int, y1: Int, x2: Int, y2: Int, px: Int, py: Int, radius: Int): Boolean {
        val dx = (x2 - x1).toDouble()
        val dy = (y2 - y1).toDouble()
        val lengthSq = dx * dx + dy * dy
        val t = if (lengthSq == 0.0) 0.0 else (((px - x1) * dx + (py - y1) * dy) / lengthSq).coerceIn(0.0, 1.0)
        val cx = x1 + t * dx - px
        val cy = y1 + t * dy - py
        return cx * cx + cy * cy <= radius.toDouble() * radius
    }

    /**
     * Legacy check_obj_collide() for vehicles on the ground: driving into a soldier kills him,
     * and a tank or car picks up the bonuses it touches.
     */
    private fun touchSmallObjects() {
        if (soldiers.isEmpty() && bonuses.isEmpty()) return
        for (tank in allTanks) {
            if (tank.armor <= 0 || kotlin.math.abs(tank.height) >= 2) continue
            for (soldier in soldiers) {
                if (!soldier.removed && withinRadius(tank.positionX, tank.positionY, soldier.x, soldier.y, tank.spec.radius + SOLDIER_RADIUS_PX)) {
                    removeSoldier(soldier)
                }
            }
            if (!tank.picksUpBonuses) continue
            for (bonus in bonuses) {
                if (bonus.removed || !withinRadius(tank.positionX, tank.positionY, bonus.x, bonus.y, tank.spec.radius + BONUS_RADIUS_PX)) continue
                val what = tank.pickUpBonus(bonus.type)
                bonus.removed = true
                pendingEvents += GameEvent.Sound(com.tankarena.protocol.snapshot.SoundKind.BONUS, tank.positionX, tank.positionY)
                tank.showMessage("PICKED UP", what)
            }
        }
        soldiers.removeAll { it.removed }
        bonuses.removeAll { it.removed }
    }

    /**
     * Legacy check_obj_overlap(): two vehicles at about the same height whose circles overlap
     * (a plane touching down on a tank, a tank spawning on another) are both destroyed, unless
     * one is still shielded by its start or bonus invulnerability.
     */
    private fun checkVehicleOverlaps() {
        for ((i, a) in allTanks.withIndex()) {
            for (b in allTanks.subList(i + 1, allTanks.size)) {
                if (kotlin.math.abs(a.height - b.height) >= 2) continue
                val reach = a.spec.radius + b.spec.radius
                val dx = a.positionX - b.positionX
                val dy = a.positionY - b.positionY
                if (dx * dx + dy * dy >= reach * reach) continue
                // The original tests the first vehicle's armor for both of them.
                if (a.armor <= 0) continue
                if (a.invulnerableTicks <= 0) a.queueDestroy()
                if (b.invulnerableTicks <= 0) b.queueDestroy()
            }
        }
    }

    /** Bonuses, soldiers and waiting A-bombs as wire states. */
    private fun smallObjectStates(): List<ActorState> {
        if (bonuses.isEmpty() && soldiers.isEmpty() && abombs.isEmpty()) return emptyList()
        val states = ArrayList<ActorState>(bonuses.size + soldiers.size + abombs.size)
        for (bonus in bonuses) states += BonusState(bonus.id, bonus.x, bonus.y, bonus.type)
        for (soldier in soldiers) states += ManState(soldier.id, soldier.x, soldier.y, soldier.direction, soldier.frame)
        for (bomb in abombs) {
            val tile = terrainGrid.at(bomb.x / LEGACY_TILE_SIZE, bomb.y / LEGACY_TILE_SIZE)
            states += AbombState(
                actorId = bomb.id,
                x = bomb.x,
                y = bomb.y,
                countdown = bomb.countdown,
                exploding = bomb.exploding,
                hidden = tile.material == TerrainMaterial.WATER,
            )
        }
        return states
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
            tank.setUnderground(runway = tile.material == TerrainMaterial.RUNWAY, water = tile.material == TerrainMaterial.WATER)

            // Legacy underground_effects(): HT_LAVA hits for 2 every 10 ticks while the tank stays
            // in lava; aircraft overhead are not touched.
            tank.stepLava(inLava = tile.material == TerrainMaterial.LAVA && tank.height < 2)
            if (tank.height > 1) continue

            val subX = (tank.positionX % LEGACY_TILE_SIZE + LEGACY_TILE_SIZE) % LEGACY_TILE_SIZE
            val subY = (tank.positionY % LEGACY_TILE_SIZE + LEGACY_TILE_SIZE) % LEGACY_TILE_SIZE
            if (inPit(tile, subX, subY)) {
                tank.startFalling()
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
        for (projectile in doomed) {
            val end = projectile.drainExplosion() ?: continue
            hitEffectFor(projectile.weaponKind, end.expired)?.let { pendingEvents += GameEvent.Effect(it, end.x, end.y) }
        }
        val expected = actorManager.allActors.value.size - doomed.size
        actorManager.remove(doomed)
        awaitActorCount(expected)
        for (projectile in doomed) {
            actorIds.remove(projectile)
        }
    }

    /**
     * Legacy `hit[type].explosion` (src/check/hitwall.c) for a bullet that hits something, and
     * control_bullets() for one that runs out of range: the main gun explodes either way, a chain-gun
     * round sparks or puffs, a flame burns where it hit and simply dies out otherwise.
     */
    private fun hitEffectFor(weaponKind: Int, expired: Boolean): EffectKind? = when (weaponKind) {
        WEAPON_CHAIN -> if (expired) EffectKind.PLUF else EffectKind.CHAIN
        WEAPON_FLAME -> if (expired) null else EffectKind.FLAME
        else -> EffectKind.EXPLOSION
    }

    /**
     * Legacy line_obj_hit_radius(..., 15, HT_FLAME, ...): a flame burns the first live tank or
     * turret within 15 px of it, other than the tank that fired it.
     */
    private fun triggerFlameContacts() {
        val flames = actorManager.allActors.value.filterIsInstance<ServerProjectileActor>()
            .filter { it.weaponKind == WEAPON_FLAME && !it.isDead && it.armed }
        if (flames.isEmpty()) return
        for (flame in flames) {
            // Legacy line_obj_hit_radius(..., 15, HT_FLAME, ...): every vehicle off the ground
            // within 15 px (or its own radius, if larger) of the flame's path this tick burns.
            val (fromX, fromY) = flame.previousPosition
            val burnt = allTanks.filter { tank ->
                tank !== flame.ownerRef && tank.armor > 0 && tank.height > 0 &&
                    nearSegment(fromX, fromY, flame.positionX, flame.positionY, tank.positionX, tank.positionY, maxOf(FLAME_HIT_RADIUS_PX, tank.spec.radius))
            }
            for (tank in burnt) tank.queueDamage(flame.damage)
            if (burnt.isNotEmpty()) flame.burn(burnt.first())
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
     * Legacy nearest_target(): the closest live tank within 15 px of ([x], [y]), other than
     * [owner]. Turrets have no hit radius in the original, so a rocket cannot lock onto one.
     */
    private fun rocketTargetAt(x: Int, y: Int, owner: ServerTankActor): RocketTarget? {
        var best: RocketTarget? = null
        var bestDistSq = ROCKET_TARGET_RADIUS_PX.toLong() * ROCKET_TARGET_RADIUS_PX
        for (actor in actorManager.allActors.value) {
            val (ax, ay, target) = when {
                actor is ServerTankActor && actor !== owner && actor.armor > 0 ->
                    Triple(actor.positionX, actor.positionY, RocketTarget { actor.takeIf { it.armor > 0 }?.let { it.positionX to it.positionY } })
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

    /** Legacy create_rocket(): the vehicle a rocket is launched at hears it coming. */
    private fun warnIncoming(x: Int, y: Int, owner: ServerTankActor?) {
        val target = allTanks
            .filter { it !== owner && it.armor > 0 && withinRadius(it.positionX, it.positionY, x, y, ROCKET_TARGET_RADIUS_PX - 1) }
            .minByOrNull { (it.positionX - x).toLong() * (it.positionX - x) + (it.positionY - y).toLong() * (it.positionY - y) }
            ?: return
        target.showMessage("INCOMING", "MISSILE!")
        pendingEvents += GameEvent.Sound(com.tankarena.protocol.snapshot.SoundKind.INCOMING, target.positionX, target.positionY)
    }

    private fun drainRocketExplosions() {
        val tanks = actorManager.allActors.value.filterIsInstance<ServerTankActor>()
        for (actor in actorManager.allActors.value) {
            if (actor !is ServerRocketActor) continue
            val explosion = actor.drainExplosion() ?: continue
            // Legacy control_rocket(): a rocket without a target hits the structure at the marker.
            if (actor.target?.position() == null) {
                blastStructureAt(explosion.x, explosion.y, actor.damage, EffectKind.LEXPLOSION, by = actor.ownerRef)
                pendingEvents += GameEvent.Explosion(explosion.x, explosion.y, ROCKET_BLAST_RADIUS_PX, ExplosionKind.ROCKET)
                continue
            }
            pendingEvents.addAll(AreaDamageResolver.resolve(
                tanks = tanks,
                x = explosion.x,
                y = explosion.y,
                radius = ROCKET_BLAST_RADIUS_PX,
                damage = actor.damage,
                kind = ExplosionKind.ROCKET,
                falloff = false,
                owner = actor.ownerRef,
                ownerImmune = true,
                unshieldable = true,
            ))
            blastSmallObjects(explosion.x, explosion.y, ROCKET_BLAST_RADIUS_PX, power = actor.damage, rocket = true)
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
        warnIncoming(request.targetX, request.targetY, owner)

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
            blastStructuresAround(explosion.x, explosion.y, actor.ownerRef)
            // Legacy create_mortar(): every 5 px growth step hits for 5.
            blastSmallObjects(explosion.x, explosion.y, explosion.radius, power = MORTAR_STEP_POWER * (explosion.radius / 5))
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
                travelTicksRemaining = request.travelTicks,
                dropHeight = request.dropHeight,
            ),
        )
        mortar.ownerRef = owner
        
        // Legacy create_bullet(): a mortar shell bangs, a plane's bomb just drops.
        if (request.dropHeight == 0) {
            pendingEvents += GameEvent.Sound(
                kind = com.tankarena.protocol.snapshot.SoundKind.MORTAR,
                x = request.originX,
                y = request.originY,
            )
        }
        
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
                falloff = false,
            ))
            // Legacy blowup_mine(): the mine also hits the structure it lies on.
            blastStructureAt(det.x, det.y, actor.damage, EffectKind.EXPLOSION)
            blastSmallObjects(det.x, det.y, actor.radius, power = actor.damage)
        }
    }

    private fun removeDeadMines() {
        val doomed = actorManager.allActors.value
            .filterIsInstance<ServerMineActor>()
            .filter { it.isDead && !it.detonationPending }
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
            for (request in tank.drainFireRequests()) spawns += projectileFromRequest(tank, request)
        }
        for (actor in actorManager.allActors.value) {
            if (actor is ServerTurretActor) {
                val request = actor.drainFireRequest() ?: continue
                spawns += projectileFromTurretRequest(actor, request)
            }
        }
        for ((soldier, shot) in pendingSoldierShots) spawns += projectileFromSoldier(soldier, shot)
        pendingSoldierShots.clear()
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
                safetyPx = request.safetyPx,
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

    /**
     * Legacy control_man(): create_bullet(o, m.type, ..., 5 * b_size, 0) — a soldier's round flies
     * at 200 px/s for 5 tiles, his flame like any flame.
     */
    private fun projectileFromSoldier(soldier: Soldier, shot: Soldier.Shot): ServerProjectileActor {
        val flame = soldier.shotKind == WEAPON_FLAME
        val speed = if (flame) 0.75f else 2f
        val range = if (flame) FLAME_RANGE_PX else 5 * LEGACY_TILE_SIZE
        val (fx, fy) = com.tankarena.core.LegacyDirections.unitVector(shot.direction)
        val projectile = ServerProjectileActor(
            ServerProjectileActor.State(
                body = PointBody(initialPosition = SceneOffset(shot.x.toFloat().sceneUnit, shot.y.toFloat().sceneUnit)),
                ownerActorId = soldier.id,
                ownerKind = ProjectileOwnerKind.MAN,
                damage = soldier.power,
                velocityX = fx * speed,
                velocityY = fy * speed,
                ttlTicks = (range / speed).roundToInt().coerceAtLeast(1),
                weaponKind = soldier.shotKind,
            ),
        )
        projectile.ownerSoldierRef = soldier
        pendingEvents += GameEvent.Sound(kind = soundKindForWeapon(soldier.shotKind), x = shot.x, y = shot.y)
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
                ttlTicks = request.ttlTicks,
                weaponKind = if (request.chain) WEAPON_CHAIN else WEAPON_MAIN,
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
            WEAPON_FLAME -> com.tankarena.protocol.snapshot.SoundKind.FLAME
            SHOT_SMALL_ROCKET -> com.tankarena.protocol.snapshot.SoundKind.SROCKET
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
        return WorldSnapshot(
            tick = currentTick,
            actors = states + smallObjectStates(),
            events = events,
            changedTiles = changedTileStates(),
            flash = flash,
        )
    }

    /** The tiles whose pictures changed (legacy rewrite_block()), for the client's terrain. */
    private fun changedTileStates(): List<TileState> = structures.changedTiles.map { index ->
        val tileX = index % mapMetadata.widthTiles
        val tileY = index / mapMetadata.widthTiles
        TileState(
            index = index,
            base = structures.base[index],
            solid = structures.solid[index],
            top = structures.top[index],
            solidStanding = structures.isStanding(tileX, tileY),
            topIntact = structures.isTopIntact(tileX, tileY),
        )
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
                    height = actor.height,
                    // Legacy write_tank(): ARMOR overlay while the shield key works or the start shield blinks on.
                    shielded = actor.shieldRingVisible,
                    chopper = actor.kind == VehicleKind.CHOPPER,
                    cloaked = actor.cloaked,
                    cloakFlash = actor.cloakFlash,
                    lights = actor.headlights(),
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
                vx = actor.velocityX.roundToInt(),
                vy = actor.velocityY.roundToInt(),
                ownerKind = actor.ownerKind,
                kind = when (actor.weaponKind) {
                    WEAPON_CHAIN -> ProjectileKind.CHAIN
                    WEAPON_FLAME -> ProjectileKind.FLAME
                    SHOT_SMALL_ROCKET -> ProjectileKind.SROCK
                    else -> ProjectileKind.BULLET
                },
                rangeLeft = actor.rangeLeft,
                direction = com.tankarena.core.LegacyDirections.fromAngle((actor.velocityX * 1000).roundToInt(), (actor.velocityY * 1000).roundToInt()),
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
                kind = if (actor.dropHeight > 0) ProjectileKind.PLANEBOMB else ProjectileKind.MORTAR,
                rangeLeft = (actor.travelTicksRemaining * kotlin.math.hypot(actor.velocityX, actor.velocityY)).toInt(),
                dropHeight = actor.dropHeight,
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
                picture = actor.picture,
                direction = actor.direction,
            )

            is ServerZeppelinActor -> ZeppelinState(
                actorId = id,
                x = actor.positionX,
                y = actor.positionY,
                alive = actor.alive,
                frame = actor.frame,
            )

            is ServerB52Actor -> B52State(
                actorId = id,
                x = actor.positionX,
                y = actor.positionY,
                armor = actor.armor,
                alive = actor.alive,
                bombCount = actor.bombCount,
                heading = actor.heading,
                height = actor.height,
                bombBay = actor.bomb,
                wreck = actor.phase == ServerB52Actor.Phase.WRECK,
                onWater = terrainGrid.at(actor.positionX / LEGACY_TILE_SIZE, actor.positionY / LEGACY_TILE_SIZE).material == TerrainMaterial.WATER,
                lightOn = actor.lightOn,
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
        /**
         * Test fixtures. They start without the legacy start shield ([startShields]) so a fixture can
         * hurt a tank on its first tick.
         */
        internal fun fromCanonicalMap(
            map: CanonicalMapDefinition,
            mode: MatchMode = MatchMode.SINGLE,
            startShields: Boolean = false,
            dual: DualSetup = DualSetup(),
        ): ServerMatchPrototype {
            // Round-trip through scene JSON so fixtures exercise the same load path as shipped scenes.
            val serializationManagerForBuild = SerializableMetadata.newSerializationManagerInstance(
                *tankArenaSerializableMetadata,
            )
            val sceneJson = CanonicalSceneBuilder.buildSceneJson(map, serializationManagerForBuild)
            return create(map.metadata, map.layers, serializationManagerForBuild.deserializeActors(sceneJson), mode, startShields, dual)
        }

        /**
         * Production bootstrap: a committed scene plus its sidecar. The sidecar's tile layers drive
         * terrain effects, pits, A* pathing and line-of-sight exactly as on the fixture path.
         */
        fun fromSceneJson(
            sceneJson: String,
            sidecar: MapSceneSidecar,
            mode: MatchMode = MatchMode.SINGLE,
            dual: DualSetup = DualSetup(),
        ): ServerMatchPrototype {
            val serializationManagerForBuild = SerializableMetadata.newSerializationManagerInstance(
                *tankArenaSerializableMetadata,
            )
            val actors = serializationManagerForBuild.deserializeActors(sceneJson)
            return create(sidecar.metadata, sidecar.tileLayers, actors, mode, startShields = true, dual = dual)
        }

        private fun create(
            metadata: MapMetadata,
            layers: TileLayers?,
            actors: List<Serializable<*>>,
            mode: MatchMode,
            startShields: Boolean,
            dual: DualSetup = DualSetup(),
        ): ServerMatchPrototype {
            val instance = ServerMatchPrototype(
                mapMetadata = metadata,
                // Structures block by their pixels through the StructureGrid; the scene's wall actors
                // only serve the editor.
                initialActors = MatchSetup.assignControl(actors.filter { it !is ServerWallActor }, mode, dual),
                terrainGrid = TerrainGridBuilder.build(layers, metadata),
                structures = StructureGrid(metadata.widthTiles, metadata.heightTiles, metadata.world, layers),
                mode = mode,
                startShields = startShields,
                layers = layers,
                dual = dual,
            )
            return instance
        }
    }
}

private typealias IdentityHashMap<K, V> = java.util.IdentityHashMap<K, V>

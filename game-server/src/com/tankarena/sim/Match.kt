package com.tankarena.sim

import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.MapMetadata
import com.tankarena.content.MapSceneSidecar
import com.tankarena.content.TileLayers
import com.tankarena.content.TerrainGridBuilder
import com.tankarena.protocol.snapshot.TerrainGrid
import com.tankarena.protocol.snapshot.TerrainMaterial
import com.tankarena.protocol.snapshot.TerrainTile
import com.tankarena.sim.legacy.CanonicalMapDefinition
import com.tankarena.sim.map.MapConversion
import com.tankarena.sim.map.MapDocument
import com.tankarena.input.PlayerIntentFrame
import com.tankarena.protocol.Team
import com.tankarena.protocol.snapshot.EffectKind
import com.tankarena.protocol.snapshot.ExplosionKind
import com.tankarena.protocol.snapshot.GameEvent
import com.tankarena.protocol.snapshot.HudState
import com.tankarena.protocol.snapshot.PlayerView
import com.tankarena.protocol.snapshot.WorldSnapshot

/** Legacy nearest_target(): a rocket locks onto anything within 15 px of the marker. */
internal const val ROCKET_TARGET_RADIUS_PX: Int = 15
/** Legacy create_mortar(): each growth step of the blast hits structures for 5. */
internal const val MORTAR_STEP_POWER: Int = 5
/** Legacy HT_FLAME hit radius. */
internal const val FLAME_HIT_RADIUS_PX: Int = 15
/** Legacy tank_write_marker(): the marker turns red beyond 400 px. */
internal const val LOCK_OUT_OF_RANGE_PX: Int = 400
// Legacy src/objects/train.c: quarter-circle rails of radius 49, wagons 31 px apart, at most
// 50 px/s, the engine's smoke 11 px ahead of its centre, and wagon 7 carrying an A-bomb.
internal const val RAILS_RADIUS: Int = 49
internal const val WAGON_DISTANCE: Int = 31
internal const val TRAIN_MAX_SPEED: Int = 50
internal val TRAIN_SMOKE_OFFSET: Pair<Int, Int> = 0 to -11
internal const val ABOMB_WAGON: Int = 7
/** Legacy b52.c: where the ten bombs leave the bay, the four engines, and the bombs' power. */
internal val B52_BOMBS: List<Pair<Int, Int>> = listOf(
    -27 to 17, -15 to 17, -21 to 23, -14 to 28, -27 to 30,
    16 to 17, 28 to 20, 15 to 24, 27 to 29, 9 to 32,
)
internal val B52_ENGINES: List<Pair<Int, Int>> = listOf(-16 to 9, -10 to 8, 9 to 8, 15 to 9)
internal const val B52_BOMB_POWER: Int = 20
/** Legacy `prodname[]` (src/objects/product.c). */
/** Legacy `max_mayhem_cash`, `max_mayhem_products` and `max_mayhem_time` (src/include/define.h). */
internal const val MAYHEM_CASH: Int = 40
internal const val MAYHEM_PRODUCTS: Int = 20
internal const val MAYHEM_TICKS: Long = 180L * 100
/** Legacy `o->r` of a product. */
internal const val PRODUCT_RADIUS_PX: Int = 4

internal val PRODUCT_NAMES: List<String> = listOf(
    "WASHING POWDER", "CHOCOLATE BAR", "APPLE", "WINE", "M&M'S", "TOILET PAPER", "ONION", "CIGARETTES",
    "COOKIES", "BEER", "COLA", "SAUSAGE", "BAMI", "CAN", "SPAGHETTI",
)
/** Legacy `vzep`. */
internal const val ZEPPELIN_SPEED: Float = 10f
/** Legacy create_mine(): r = 3. */
internal const val MINE_RADIUS_PX: Int = 3
/** Legacy HT_SROCK, as the man layer stores a soldier's weapon. */
internal const val HT_SROCK: Int = 10
/** Legacy create_bonus(): the random roll (out of 10000) below which each bonus type drops. */
internal val RANDOM_BONUS_THRESHOLDS = intArrayOf(300, 600, 700, 800, 850, 900, 950, 1000, 1050, 1100, 1150, 1200, 1225, 1250)
internal const val TANK_FOOTPRINT: Int = LEGACY_TILE_SIZE - 4
internal const val TRAIN_FOOTPRINT: Int = 32
internal const val ZEPPELIN_FOOTPRINT: Int = 32
internal const val B52_FOOTPRINT: Int = 32

/**
 * One match: the map's objects, buildings and players, advanced one 10 ms tick at a time by
 * [tick] in the order of the original's game loop. The rules live in extension files next to this
 * one, one per subsystem: MatchWeapons (shots), MatchStructures (buildings), MatchSmallObjects
 * (bonuses, soldiers, A-bombs), MatchTrains, MatchAircraft, MatchObjectRules (warps, locks,
 * destroyers, enforcers), MatchTerrain, MatchAi, MatchMission (goals and the end of a mission) and
 * MatchViews (what clients see).
 */
class Match private constructor(
    internal val mapMetadata: MapMetadata,
    internal val initialActors: List<SceneObject<*>>,
    internal val terrainGrid: TerrainGrid,
    /** The map's buildings: strengths, ruins and the pixel masks tanks and bullets collide with. */
    internal val structures: StructureGrid,
    val mode: MatchMode,
    /** Legacy make_tank(): human tanks start with the blinking start shield. */
    internal val startShields: Boolean,
    /** The map's tile layers, for the bonus and man layers. */
    internal val layers: TileLayers? = null,
    /** Player vs player: the players' vehicles, the race length and the round. */
    val dual: DualSetup = DualSetup(),
    /** Classic or Extended rules (ADR 0023). */
    val ruleset: Ruleset = Ruleset.CLASSIC,
    /** The original's command-line cheats. */
    val cheats: Cheats = Cheats(),
) {

    internal val worldWidthPixels: Int = mapMetadata.widthTiles * LEGACY_TILE_SIZE
    internal val worldHeightPixels: Int = mapMetadata.heightTiles * LEGACY_TILE_SIZE
    internal val objects = MatchObjectList(initialActors)
    internal val actorIds: MutableMap<SceneObject<*>, Long> = IdentityHashMap()
    internal val tanksByPlayerIndex: MutableMap<Int, Tank> = HashMap()
    /** Tanks are never spawned or removed after [initialize], so this list is stable for the match. */
    internal var allTanks: List<Tank> = emptyList()
    /** Player vs player only: pads, flags and races (legacy player.c, flag.c). */
    internal var dualRules: DualRules? = null
    internal var nextActorId: Long = 1L
    internal var currentTick: Long = 0L
    internal var isDisposed: Boolean = false
    internal var goalGood: Int = 0
    internal var goalBad: Int = 0

    /**
     * Legacy `game.supermarket_mayhem` (src/objects/product.c): a map with products is a shopping
     * trip — [mayhemProductsNeeded] products to buy with [mayhemCash] before [MAYHEM_TICKS] run out.
     */
    internal var mayhem: Boolean = false
    internal var mayhemCash: Int = MAYHEM_CASH
    internal var mayhemProductsNeeded: Int = MAYHEM_PRODUCTS
    /** Legacy `numprod[]`: products bought per type. */
    internal val productCounts: IntArray = IntArray(PRODUCT_NAMES.size)
    internal var missionStatus: MissionStatus = MissionStatus.IN_PROGRESS
    internal val pendingEvents: MutableList<GameEvent> = mutableListOf()

    /** Runtime-only objects (see MatchObjects.kt). */
    internal val bonuses: MutableList<Bonus> = mutableListOf()
    internal val soldiers: MutableList<Soldier> = mutableListOf()
    /** Legacy `parts` of type PT_FLAME: burning pieces of fuel dumps. */
    internal val flameParts: MutableList<FlamePart> = mutableListOf()
    internal val debrisRandom = kotlin.random.Random(mapMetadata.widthTiles * 131 + mapMetadata.heightTiles * 17)
    internal val abombs: MutableList<Abomb> = mutableListOf()
    internal val pendingSoldierShots: MutableList<Pair<Soldier, Soldier.Shot>> = mutableListOf()
    /** Legacy `nuke_total`: how far every colour is brightened while A-bombs go off. */
    internal var flash: Int = 0
    internal val objectRandom = kotlin.random.Random(mapMetadata.widthTiles * 7919 + mapMetadata.heightTiles * 104729)

    // Mission stats (T29)
    internal var missionKills: Int = 0
    internal var missionCaptures: Int = 0
    internal var missionScore: Int = 0
    internal var missionResultEmitted: Boolean = false
    internal var lossReason: String = "GOALS_LOST"
    /** True when the objects' goal contributions can reach 100% on their own (see [evaluateMission]). */
    internal var goalCounterReachable: Boolean = false

    val mission: MissionStatus get() = missionStatus
    val goalProgressGood: Int get() = goalGood
    val goalProgressBad: Int get() = goalBad

    enum class MissionStatus { IN_PROGRESS, WON, LOST }

    val worldWidth: Int get() = worldWidthPixels
    val worldHeight: Int get() = worldHeightPixels
    val missionCode: String get() = mapMetadata.missionCode

    fun initialize() {
        for (actor in objects.all) {
            actorIds.getOrPut(actor) { nextActorId++ }
            if (actor is Tank && actor.playerIndex >= 0) {
                tanksByPlayerIndex.putIfAbsent(actor.playerIndex, actor)
            }
        }
        for ((playerIndex, tank) in tanksByPlayerIndex) {
            players += playerIndex
            selectedIds[playerIndex] = tank.controllerId
        }
        allTanks = objects.all.filterIsInstance<Tank>()
        for (tank in allTanks) {
            tank.configureForMatch(
                maxX = worldWidthPixels - LEGACY_TILE_SIZE,
                maxY = worldHeightPixels - LEGACY_TILE_SIZE,
                dual = mode == MatchMode.DUAL,
                night = mapMetadata.night,
                cheats = cheats,
            )
            tank.surroundings = tankSurroundings
        }
        // Legacy prepare_product(): any product turns the map into supermarket mayhem.
        mayhem = objects.all.any { it is Product }
        for (tank in allTanks) {
            // Legacy make_tank(): human tanks start with a blinking shield (`shield.icount = 15`),
            // except in supermarket mayhem (prepare_tank(): `shield.icount = 0`).
            if (startShields && !tank.computer && !mayhem) tank.grantInvulnerability(START_SHIELD_HOLD_TICKS)
        }
        // Legacy prepare_player() / prepare_flag(): pads, races and flags only play in player vs
        // player; elsewhere the flags are removed.
        val flags = objects.all.filterIsInstance<Flag>()
        val unused = if (mode == MatchMode.DUAL) {
            DualRules(dual, mapMetadata.world, mapMetadata.widthTiles, mapMetadata.heightTiles, structures, objectRandom)
                .also { dualRules = it }
                .prepare(allTanks, flags)
        } else {
            flags
        }
        if (unused.isNotEmpty()) objects.remove(unused)
        linkTrains()
        linkGoals()
        prepareB52s()
        prepareLocks()
        // Legacy control_turret(): turrets placed off a building are gone from the first tick
        // (their goal contribution still counts, but they are not kills).
        removeFallenTurrets()
        drainTurretDestructions()
        createAllBonuses()
        missionKills = 0
        missionScore = 0
        val all = objects.all
        val reachable = all.filterIsInstance<Tank>().sumOf { it.goalContribution.coerceAtLeast(0) } +
            all.filterIsInstance<Turret>().sumOf { it.goalContribution.coerceAtLeast(0) } +
            all.filterIsInstance<Goal>().filter { it.who == 0 }.sumOf { it.contribution.coerceAtLeast(0) }
        goalCounterReachable = reachable >= 100
    }

    fun tick(): WorldSnapshot = tick(intents = emptyMap())

    /**
     * One tick of the 100 Hz timer. The game only moves on every third one ([STEP_TICKS]), like the
     * original's game_control(); in between the world stands still. The controls are read on a step,
     * and like the original's keyboard handler (src/game/controls.c) a key pressed at any tick since
     * the last step counts, so a short tap is not lost; so does an F-key tank selection.
     */
    fun tick(intents: Map<Int, PlayerIntentFrame>): WorldSnapshot {
        if (isDisposed) return buildSnapshot(drainEvents = false)
        for ((player, intent) in intents) {
            latchedIntents[player] = latchedIntents[player]?.let { held -> held latch intent } ?: intent
        }
        if (currentTick % STEP_TICKS == 0L) {
            step(latchedIntents.toMap())
            latchedIntents.clear()
        }
        currentTick += 1
        return buildSnapshot(drainEvents = true)
    }

    /** What the players pressed since the last step. */
    private val latchedIntents: MutableMap<Int, PlayerIntentFrame> = sortedMapOf()

    private infix fun PlayerIntentFrame.latch(next: PlayerIntentFrame) = PlayerIntentFrame(
        forward = forward || next.forward,
        reverse = reverse || next.reverse,
        turnLeft = turnLeft || next.turnLeft,
        turnRight = turnRight || next.turnRight,
        aimLeft = aimLeft || next.aimLeft,
        aimRight = aimRight || next.aimRight,
        firePrimary = firePrimary || next.firePrimary,
        fireSecondary = fireSecondary || next.fireSecondary,
        shield = shield || next.shield,
        cycleWeaponLeft = cycleWeaponLeft || next.cycleWeaponLeft,
        cycleWeaponRight = cycleWeaponRight || next.cycleWeaponRight,
        selectTankId = if (next.selectTankId > 0) next.selectTankId else selectTankId,
    )

    /** Legacy game_control(): one step of the game. */
    private fun step(intents: Map<Int, PlayerIntentFrame>) {
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
        // Legacy transportation(): conveyors and turntables carry things along.
        applyTransport()
        // Legacy control_bullets(): shots move and hit first; their damage counts at once.
        objects.stepWhere { it is Projectile }
        flushOutOfBoundsProjectiles()
        checkProjectileStructureHits()
        checkProjectileVehicleHits()
        triggerFlameContacts()
        checkSmallObjectHits()
        removeDeadProjectiles()
        objects.resolve(worldWidthPixels, worldHeightPixels)
        // Legacy control_parts(): burning pieces of fuel dumps.
        stepFlameParts()
        objects.resolve(worldWidthPixels, worldHeightPixels)
        // Legacy check_obj_overlap(): vehicles on top of each other.
        checkVehicleOverlaps()
        objects.resolve(worldWidthPixels, worldHeightPixels)
        // Legacy control_objects(): every object's own control. Shots fired now move on the next step.
        applyTerrainEffects()
        stepAiTanks()
        objects.stepWhere { it !is Projectile }
        applyUndergroundEffects()
        objects.resolve(worldWidthPixels, worldHeightPixels)
        flushOutOfBoundsRockets()
        flushOutOfBoundsMortars()
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
        drainAbombRequests()
        drainSoldierRequests()
        stepTurrets()
        stepSoldiers()
        stepAbombs()
        touchSmallObjects()
        stepWarps()
        stepLocks()
        stepDestroyers()
        stepEnforcers()
        stepTrains()
        stepZeppelins()
        stepB52s()
        collectProducts()
        checkMayhemTime()
        collectGoals()
        objects.resolve(worldWidthPixels, worldHeightPixels)
        drainTankLifecycleEvents()
        manageWrecks()
        drainTurretDestructions()
        handOverDeadPlayers()
        evaluateMission()
    }

    fun snapshot(): WorldSnapshot = buildSnapshot(drainEvents = false)

    /**
     * One legacy step for tests: [STEP_TICKS] ticks from a step tick on, [intents] held on its first.
     * Returns the step tick's snapshot, which carries the step's events.
     */
    internal fun stepForTest(intents: Map<Int, PlayerIntentFrame> = emptyMap()): WorldSnapshot {
        check(currentTick % STEP_TICKS == 0L) { "tick $currentTick is not the start of a step" }
        val stepped = tick(intents)
        // The keys count on the step tick only: held on, they would count on the next step too.
        repeat(STEP_TICKS - 1) { tick(emptyMap()) }
        return stepped
    }

    internal fun playerTankForTest(playerIndex: Int): Tank? = tanksByPlayerIndex[playerIndex]

    /** The players of this match (legacy `game.tk[]` slots). */
    internal val players: MutableSet<Int> = sortedSetOf()

    /** Legacy `game.id[]`: the F1–F10 id each player last picked, whether or not a vehicle has it. */
    internal val selectedIds: MutableMap<Int, Int> = mutableMapOf()

    /** Where each player's window last looked, for when there is no vehicle to follow. */
    private val lastCameras: MutableMap<Int, Pair<Int, Int>> = mutableMapOf()

    /**
     * Legacy write_screen() with `game.tk[tn] == NULL`: no vehicle to follow (the window shows static
     * noise), but the goal counters and the picked id still show.
     */
    private fun noVehicleView(playerIndex: Int, statusText: String): PlayerView {
        val (x, y) = lastCameras[playerIndex] ?: (worldWidthPixels / 2 to worldHeightPixels / 2)
        return PlayerView(
            playerId = playerIndex,
            controlledActorId = null,
            cameraCenterX = x,
            cameraCenterY = y,
            hud = HudState(
                missionProgress = goalGood,
                missionProgressBad = goalBad,
                missionCode = missionCode,
                statusText = statusText,
                score = missionScore,
                kills = missionKills,
                time = currentTick,
                tankId = selectedIds[playerIndex] ?: -1,
                alive = false,
                mayhem = mayhemHud(),
                radarHidden = true,
            ),
        )
    }

    internal fun tanksForTest(): List<Tank> = allTanks

    internal fun trainsForTest(): List<Train> = trains

    internal fun locksForTest(): List<Lock> = locks

    internal fun zeppelinsForTest(): List<Zeppelin> = objects.all.filterIsInstance<Zeppelin>()

    internal fun hitTrainForTest(car: Train, power: Int) = hitTrain(car, power)

    internal fun turretsForTest(): List<Turret> =
        objects.all.filterIsInstance<Turret>()

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
        val enemy = objects.all
            .filterIsInstance<Tank>()
            .firstOrNull { it.team == 1 && it.armor > 0 }
        enemy?.queueDamage(amount)
    }

    internal fun snapshotMines(): List<Mine> =
        objects.all.filterIsInstance<Mine>()

    internal fun snapshotRockets(): List<Rocket> =
        objects.all.filterIsInstance<Rocket>()

    internal fun snapshotMortars(): List<Mortar> =
        objects.all.filterIsInstance<Mortar>()

    /** A shell that has just landed at ([x], [y]); its blast starts on the next step. */
    internal fun landMortarForTest(x: Int, y: Int): Mortar {
        val mortar = Mortar(Mortar.State(body = PointBody(position = ScenePoint(x.toFloat(), y.toFloat()))))
        mortar.detonate()
        objects.add(listOf(mortar))
        actorIds.getOrPut(mortar) { nextActorId++ }
        return mortar
    }

    internal fun injectArmedMineForTest(x: Int, y: Int, damage: Int, radius: Int, heavy: Boolean = false): Mine {
        val mine = Mine(
            Mine.State(
                body = PointBody(position = ScenePoint(x.toFloat(), y.toFloat())),
                ownerActorId = 0L,
                damage = damage,
                radius = radius,
                activationTicksRemaining = 0,
                heavy = heavy,
            ),
        )
        objects.add(listOf(mine))
        actorIds.getOrPut(mine) { nextActorId++ }
        return mine
    }

    fun buildPlayerViews(): List<PlayerView> {
        val all = objects.all
        val tanks = all.filterIsInstance<Tank>()
        val turrets = all.filterIsInstance<Turret>()
        val goals = all.filterIsInstance<Goal>()
        val statusText = when (missionStatus) {
            MissionStatus.IN_PROGRESS -> ""
            MissionStatus.WON -> "MISSION_WON"
            MissionStatus.LOST -> "MISSION_LOST"
        }
        val views = mutableListOf<PlayerView>()
        for (playerIndex in players) {
            val tank = tanksByPlayerIndex[playerIndex]
            if (tank == null) {
                views += noVehicleView(playerIndex, statusText)
                continue
            }
            lastCameras[playerIndex] = tank.positionX to tank.positionY
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
                    mayhem = mayhemHud(),
                    // Legacy write_screen(): no radar in supermarket mayhem.
                    radarHidden = mayhem,
                ),
                viewerX = tank.positionX,
                viewerY = tank.positionY,
                radar = buildRadar(tank, tanks, turrets, goals),
                lockMarker = lockMarkerFor(tank),
            )
        }
        return views
    }

    fun dispose() {
        isDisposed = true
    }

    // ---- State of the rules in MatchObjectRules.kt: destroyers, locks, enforcers ----

    internal var destroyers: List<Destroyer> = emptyList()
    internal var locks: List<Lock> = emptyList()
    internal val lockOwners: MutableMap<Lock, SceneObject<*>?> = IdentityHashMap()
    internal val lockTargets: MutableMap<Lock, SceneObject<*>?> = IdentityHashMap()

    // ---- State of MatchTrains.kt ----

    internal var trains: List<Train> = emptyList()
    /** Turrets riding wagons (make_turret(..., wagon)). */
    internal val ridingTurrets: MutableMap<Turret, Train> = IdentityHashMap()
    internal val trainRandom = kotlin.random.Random(mapMetadata.widthTiles * 131 + mapMetadata.heightTiles * 17)

    // ---- State of MatchAircraft.kt ----

    internal var b52s: List<B52> = emptyList()
    internal val airRandom = kotlin.random.Random(mapMetadata.widthTiles * 523 + mapMetadata.heightTiles * 97)

    // ---- State of MatchAi.kt, and what the computer AI and the tanks may ask the match ----

    internal var aiObjectsCache: List<AiObject> = emptyList()
    internal var aiObjectsTick = -1L

    /** The world as control_computer_tank() reads it. */
    internal val aiWorld = object : ComputerTankWorld {
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

    /** What a tank needs to know about the world it drives in (legacy new_position()). */
    internal val tankSurroundings = object : TankSurroundings {
        override val structures: StructureGrid get() = this@Match.structures
        override val worldWidth: Int get() = worldWidthPixels
        override val worldHeight: Int get() = worldHeightPixels
        override val tick: Long get() = currentTick
        override fun tanks(): List<Tank> = allTanks.filter { !it.wreckGone }
        override fun baseType(tileX: Int, tileY: Int): String? {
            if (tileX !in 0 until mapMetadata.widthTiles || tileY !in 0 until mapMetadata.heightTiles) return null
            return com.tankarena.content.PictureCatalog.legacyRow(mapMetadata.world, structures.base[tileX + tileY * mapMetadata.widthTiles])?.type
        }
        override fun trainAt(x: Int, y: Int, radius: Int, height: Int): Boolean = trains.any { car ->
            car.alive && kotlin.math.abs(height - 1) < 2 &&
                (car.positionX - x) * (car.positionX - x) + (car.positionY - y) * (car.positionY - y) <
                (radius + Train.RADIUS) * (radius + Train.RADIUS)
        } || b52s.any { b52 ->
            // Legacy check_obj_collide(): a bomber (or its wreck) is an obstacle too.
            b52.alive && kotlin.math.abs(height - b52.height) < 2 &&
                (b52.positionX - x) * (b52.positionX - x) + (b52.positionY - y) * (b52.positionY - y) <
                (radius + B52.RADIUS) * (radius + B52.RADIUS)
        }
        override fun damageStructure(tileX: Int, tileY: Int, layer: StructureLayer, kind: HitKind, power: Int, by: Tank?) {
            hitStructure(tileX, tileY, layer, kind, power, explosion = EffectKind.RUIN, by = by)
        }
    }

    // ---- State of MatchStructures.kt ----

    internal val structureRandom = kotlin.random.Random(mapMetadata.widthTiles * 31 + mapMetadata.heightTiles)

    // ---- State of MatchWeapons.kt ----

    /** Rockets that rocket turrets (legacy `ti[1]`, HT_ROCKET) fired this tick. */
    internal val pendingTurretRockets = mutableListOf<Pair<Turret, Turret.FireRequest>>()

    internal fun Int.toProtocolTeam(): Team = when (this) {
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
            cheats: Cheats = Cheats(),
            ruleset: Ruleset = Ruleset.CLASSIC,
        ): Match {
            // Round-trip through scene JSON so fixtures exercise the same load path as shipped scenes.
            val sceneJson = CanonicalSceneBuilder.buildSceneJson(map)
            return create(map.metadata, map.layers, SceneFile.read(sceneJson), mode, startShields, dual, ruleset = ruleset, cheats = cheats)
        }

        /** A match on [map] (a shipped or imported map in the native format, ADR 0022), with fresh objects. */
        fun fromMap(
            map: MapDocument,
            mode: MatchMode = MatchMode.SINGLE,
            dual: DualSetup = DualSetup(),
            ruleset: Ruleset = Ruleset.CLASSIC,
            cheats: Cheats = Cheats(),
        ): Match {
            val sidecar = MapConversion.sidecarOf(map)
            return create(sidecar.metadata, sidecar.tileLayers, MapConversion.objectsOf(map), mode, startShields = true, dual = dual, ruleset = ruleset, cheats = cheats)
        }

        private fun create(
            metadata: MapMetadata,
            layers: TileLayers?,
            actors: List<SceneObject<*>>,
            mode: MatchMode,
            startShields: Boolean,
            dual: DualSetup = DualSetup(),
            ruleset: Ruleset = Ruleset.CLASSIC,
            cheats: Cheats = Cheats(),
        ): Match {
            val instance = Match(
                mapMetadata = metadata,
                initialActors = MatchSetup.assignControl(actors, mode, dual, ruleset.flags),
                terrainGrid = TerrainGridBuilder.build(layers, metadata),
                structures = StructureGrid(metadata.widthTiles, metadata.heightTiles, metadata.world, layers),
                mode = mode,
                startShields = startShields,
                layers = layers,
                dual = dual,
                ruleset = ruleset,
                cheats = cheats,
            )
            return instance
        }
    }
}

internal typealias IdentityHashMap<K, V> = java.util.IdentityHashMap<K, V>

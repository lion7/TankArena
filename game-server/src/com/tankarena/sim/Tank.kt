package com.tankarena.sim

import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.LegacySpriteResources
import com.tankarena.content.PictureMasks
import com.tankarena.core.LegacyDirections
import com.tankarena.input.PlayerIntentFrame
import com.tankarena.protocol.snapshot.EffectKind
import com.tankarena.protocol.snapshot.GameEvent
import com.tankarena.protocol.snapshot.TerrainMaterial
import com.tankarena.protocol.snapshot.TerrainTile
import com.tankarena.protocol.snapshot.SoundKind
import kotlin.math.abs
import com.tankarena.core.DetMath.hypot
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

/** Seconds per server tick; the legacy physics (src/tanks/control.c) works in px/s with `dt = 1/sec`. */
/** Shot speeds are kept in pixels per tick (a step moves them [STEP_TICKS] times as far). */
private const val TICK_SECONDS: Float = 0.01f
/** Legacy `weapon_left()/right()`: `tinit(15)` between two weapon changes. */
private const val WEAPON_CYCLE_COOLDOWN_TICKS: Int = 15
/** Legacy tank_fire(): an empty weapon clicks and blocks firing for 50 ticks. */
private const val EMPTY_WEAPON_DELAY_TICKS: Int = 50
/** Legacy create_bullet(): bullets leave at 200 px/s plus the tank's own speed, but never slower than 75. */
private const val BULLET_SPEED: Float = 200f
private const val MIN_BULLET_SPEED: Float = 75f
/** Legacy create_bullet() ranges: the main gun reaches 250 px (500 in player-vs-player), the chain gun 5 tiles. */
private const val MAIN_GUN_RANGE_PX: Int = 250
private const val MAIN_GUN_DUAL_RANGE_PX: Int = 500
private const val CHAIN_GUN_RANGE_PX: Int = 5 * LEGACY_TILE_SIZE
/** Legacy tank_fire() case 1: each chain-gun burst uses 5 rounds. */
private const val CHAIN_ROUNDS_PER_BURST: Int = 5
/** Legacy HT_FLAME: 75 px/s for 120 px; each burst burns 40 fuel. */
private const val FLAME_SPEED: Float = 75f
internal const val FLAME_RANGE_PX: Int = 120
private const val FLAME_FUEL_COST: Int = 40
/** Legacy create_mortar(): a blast growing from 5 to 60 px that hits for 5 per step, i.e. up to 60 at the centre. */
private const val MORTAR_DAMAGE: Int = 60
/** Legacy create_bullet(HT_MORTAR): 75 px/s for 120 px, over walls. */
private const val MORTAR_SPEED: Float = 0.75f
// Legacy tank_marker_control(): the marker moves every 3 ticks, starting at 5 px per step and
// speeding up to 20, and must stay within 450 px of the tank outside player-vs-player.
private const val LOCK_STEP_TICKS: Int = 3
private const val LOCK_START_SPEED: Int = 5
private const val LOCK_MAX_SPEED: Int = 20
internal const val LOCK_MAX_RANGE_PX: Int = 450
/** Legacy `lock.fired = tinit(50)`: the camera stays on the marker this long after launch. */
private const val LOCK_CAMERA_HOLD_TICKS: Int = 50
// Legacy control_tank(): HT_LAVA hits for 2 every 10 ticks.
private const val LAVA_DAMAGE: Int = 2
private const val LAVA_INTERVAL_TICKS: Int = 10
internal const val RESPAWN_DELAY_TICKS: Int = 300
/** Legacy `shield.power`: starts at 3000, drains 60 per tick while held, recharges 10 per tick up to 6000. */
internal const val SHIELD_START_POWER: Int = 3000
private const val SHIELD_MAX_POWER: Int = 6000
/** Legacy shield_effects(): the pool changes by 60 (drain) or 10 (recharge) per tick that has passed. */
private const val SHIELD_DRAIN_PER_TICK: Int = 60
private const val SHIELD_RECHARGE_PER_TICK: Int = 10
/**
 * Legacy make_tank()/prepare_tank(): `shield.invun = tinit(200)` with `shield.icount = 15`; once
 * the timer runs out, shield_effects() counts `icount` down every `tinit(5)`. The tank cannot be hurt
 * while `icount` is not 0: on legacy steps 201 + 14 × 6 = 285 ticks.
 */
internal const val SHIELD_BLINKS: Int = 15
internal const val SHIELD_BLINK_TICKS: Int = 5
internal const val START_SHIELD_HOLD_TICKS: Int = 200
internal val START_SHIELD_TICKS: Int = stepRounded(START_SHIELD_HOLD_TICKS) + (SHIELD_BLINKS - 1) * stepRounded(SHIELD_BLINK_TICKS)
/** Legacy pickup_bonus() case 12: `shield.invun = tinit(2000)`, `shield.icount = 15`. */
internal const val INVULNERABILITY_BONUS_HOLD_TICKS: Int = 2000
/** Legacy new_position()/check_obj_collide(): crash damage at most every 10 ticks; a heavy rammer hits for 2. */
private const val CRASH_INTERVAL_TICKS: Int = 10
private const val RAM_DAMAGE: Int = 2
/** Legacy control_player(): a tank counts as in its base within 7 px of its start pad. */
private const val BASE_RADIUS_PX: Int = 7
/** Legacy new_velocity(): gravity in px/s² and the depth (`lowest`) at which a pit destroys a tank. */
private const val PIT_GRAVITY: Float = 100f
private const val PIT_BOTTOM_HEIGHT: Float = -4f
/** Legacy `contr.disable = tinit(100)`: a destroyed tank keeps exploding this long before it is a wreck. */
private const val DYING_TICKS: Int = 100
/** Legacy new_velocity(): aircraft fly at most this high unless the vehicle table says otherwise. */
private const val MAX_FLIGHT_HEIGHT: Float = 24f
/** Legacy new_velocity(): gravity for planes (gliding) and for everything else, in height units/s². */
private const val PLANE_GRAVITY: Float = 10f
/** Legacy new_velocity(): a gliding plane turns its sink rate into speed (`vx -= 20 * vz * dt`). */
private const val GLIDE_FACTOR: Float = 20f
/** Legacy new_velocity(): `motion.boost` (the speed-up weapon) multiplies thrust by 1.5 for 1000 ticks. */
private const val BOOST_FACTOR: Float = 1.5f
private const val BOOST_TICKS: Int = 1000
/** Legacy new_position(): `vz += |vx| / 5` off a big ramp, `/ 10` off a small one. */
private const val BIG_RAMP_LIFT: Float = 5f
private const val SMALL_RAMP_LIFT: Float = 10f
/** Legacy tank_add_impulse(): `2000 * power / mass` px/s; the mortar kicks back with power 50. */
private const val IMPULSE_FACTOR: Float = 2000f
private const val MORTAR_RECOIL_POWER: Int = 50
/** Legacy create_bullet(HT_SROCK): a small rocket only starts hitting things 30 px out. */
internal const val SROCK_SAFETY_PX: Int = 30
/** Legacy create_bullet(HT_PLANEBOMB): a bomb falls 10 px forward per unit of height, at 75 px/s. */
private const val PLANE_BOMB_PX_PER_HEIGHT: Int = 10
/** Legacy tank_fire() case 9: one smoke screen puffs 100 clouds, one every 10..20 ticks. */
private const val SMOKE_SCREEN_CLOUDS: Int = 100
/** Legacy tank_effects(): the smoke screen leaves from two points behind the hull. */
private val SMOKE_SCREEN_POINTS: List<Pair<Int, Int>> = listOf(-12 to 11, 12 to 11)
/**
 * Legacy tank_fire() case 10 and invisible_effects(): a cloak lasts 2000 ticks, then 300 more
 * with a faster blink; meanwhile the driver sees the vehicle blink and nobody else sees it.
 */
private const val CLOAK_FIRST_TICKS: Int = 2000
private const val CLOAK_LAST_TICKS: Int = 300
/** Legacy tank_fire_locked() case 7/8: soldiers jump out 20 px from the vehicle towards the marker. */
private val SOLDIER_DROP_OFFSET: Pair<Int, Int> = 0 to -20
/** Legacy pickup_bonus(): the amounts each bonus type gives. */
private const val ARMOR_BONUS: Int = 10

/** Legacy create_message(): a message stays 29 steps of 10 ticks (msg_control()). */
internal const val MESSAGE_STEPS: Int = 29
internal const val MESSAGE_STEP_TICKS: Int = 10
/** Legacy tank_fire(): switching the light blocks the trigger for 50 ticks. */
private const val LIGHT_SWITCH_TICKS: Int = 50
/** Legacy `desc[]` in src/tanks/fire.c: what weapon_left()/right() announce. */
private val WEAPON_NAMES: List<String> = listOf(
    "MAIN CANNON", "CHAIN GUN", "FLAME THROWER", "MINES", "ROCKET", "MORTAR", "A-BOMB",
    "MEN WITH CG", "MEN WITH FL", "SMOKE SCREEN", "INVISIBILITY", "EXTRA SPEED", "LIGHT",
)

/** Legacy WRECK_LIFE_TIME: a wreck smokes (ROOK every 500..800 ticks) for 30 s. */
/** Legacy WRECK_LIFE_TIME (src/tanks/control.c): a wreck burns this long, then blows up. */
internal const val WRECK_LIFE_TICKS: Int = 3000
private const val WRECK_SMOKE_MIN_TICKS: Int = 500
private const val WRECK_SMOKE_SPREAD_TICKS: Int = 300

const val WEAPON_MAIN: Int = 0
const val WEAPON_CHAIN: Int = 1
const val WEAPON_FLAME: Int = 2
const val WEAPON_MINE: Int = 3
const val WEAPON_ROCKET: Int = 4
const val WEAPON_MORTAR: Int = 5
const val WEAPON_ABOMB: Int = 6
const val WEAPON_GUNNERS: Int = 7
const val WEAPON_FLAMERS: Int = 8
const val WEAPON_SMOKE: Int = 9
const val WEAPON_CLOAK: Int = 10
const val WEAPON_SPEEDUP: Int = 11
/** Legacy `num_weapon`: the headlight switch, an extra weapon slot on night maps only. */
const val WEAPON_LIGHT: Int = 12
/**
 * Projectile kind (not a weapon number) for the legacy HT_SROCK main gun of aircraft and
 * launchers: a small rocket that hits like a main-gun shell.
 */
const val SHOT_SMALL_ROCKET: Int = 100
/** Legacy `num_weapon`. */
const val WEAPON_COUNT: Int = 12

/** Legacy `props.max_fuel` for almost every vehicle in `src/data/tanks.c`; fuel is measured in pixels driven. */
const val LEGACY_MAX_FUEL: Int = 30000

internal const val SERVER_TANK_FOOTPRINT: Int = LEGACY_TILE_SIZE - 4
internal const val SERVER_TANK_HALF: Int = SERVER_TANK_FOOTPRINT / 2

class Tank(state: State) :
    Steppable,
    Resolvable,
    SceneObject<Tank>, Placed {

    override val body: BoxBody = state.body

    // Structures and other vehicles are handled by the legacy movement rules in advancePosition().

    var bodyDirection: Int = state.bodyDirection
        private set
    var turretDirection: Int = state.turretDirection
        private set
    /** Human player driving this tank, or -1. Mutable so control can move between friendly tanks. */
    var playerIndex: Int = state.playerIndex
        internal set
    /** Legacy `contr.id` (the F1–F10 tank id); -1 when the map did not assign one. */
    val controllerId: Int = state.controllerId
    /** The map file's address of this vehicle, when an object (a goal) points at it; else 0. */
    val ref: Int = state.ref
    /** Legacy `contr.computer`: when false the tank only moves while a player controls it. */
    val computer: Boolean = state.computer
    /** Legacy `o->gc`: goal-counter percentage applied when this tank is destroyed. */
    var goalContribution: Int = state.goalContribution
        internal set
    /** Legacy `computer.attack_tanks`: 0 means this computer tank never goes after tanks (e.g. cars). */
    val aiAttackTanks: Int = state.aiAttackTanks
    /** Legacy `computer.activation_rad`; -1 keeps the tank permanently active. */
    val aiActivationRadius: Int = state.aiActivationRadius
    /** Legacy `computer.activator`: 0 = woken by good tanks, 1 = by evil tanks. */
    val aiActivator: Int = state.aiActivator
    val aiStayActive: Boolean = state.aiStayActive
    var aiActive: Boolean = state.aiActive
        internal set
    /** Legacy player start pad type (`type & 1` = player, `type >> 1` = vehicle kind); -1 for map tanks. */
    val startPadType: Int = state.startPadType
    /** Legacy `pl.weap`: the ammo this tank's start pad hands out, when the pad has its own loadout. */
    val padAmmo: List<Int>? = state.padAmmo
    val tankType: Int = state.tankType
    val spec: VehicleSpec = LegacyVehicles.spec(tankType)
    val turretMode: TurretMode = spec.turretMode
    /** Legacy `computer.attack_at`: fires at targets this close (make_tank(): 0 for cars, else 150). */
    val aiAttackAt: Int = state.aiAttackAt.takeIf { it >= 0 } ?: if (spec.kind == VehicleKind.CAR) 0 else 150
    /** Legacy `computer.attack_goals`: how much it prefers goal objects (make_tank(): 60). */
    val aiAttackGoals: Int = state.aiAttackGoals
    /** Legacy `computer.wave_delay`: how long an aircraft flies on after a pass (ticks). */
    val aiWaveDelay: Int = state.aiWaveDelay.takeIf { it >= 0 } ?: spec.waveDelay
    /**
     * Legacy `computer.fire_delay`: how long a computer-driven vehicle waits between shots, whatever
     * the weapon (tank_fire(); make_tank() starts it at the first weapon's `weap.speed`).
     */
    val aiFireDelay: Int = state.aiFireDelay.takeIf { it >= 0 } ?: spec.refireTicks.getOrElse(spec.initialWeapon) { 50 }
    private val authoredFireDelay: Int = state.aiFireDelay
    /** Legacy `way.type` (WAY_INACTIVE, WAY_ACTIVE, WAY_CIRCULAR), `way.current` and `way.point[10]`. */
    internal val wayPoints: IntArray = IntArray(2 * WAYPOINTS) { i -> state.wayPoints.getOrElse(i) { -1 } }
    /** Legacy prepare_tank(): the route starts at the first filled slot, and without one it is off. */
    private val firstWaypoint: Int = (0 until WAYPOINTS).firstOrNull { wayPoints[2 * it] != -1 } ?: -1
    internal var wayType: Int = if (firstWaypoint < 0) ComputerTankAi.WAY_INACTIVE else state.wayType
    internal var wayCurrent: Int = if (firstWaypoint < 0) state.wayCurrent else firstWaypoint
    /** The legacy `computer` timers and target of control_computer_tank() (src/tanks/computer.c). */
    internal val brain = ComputerTankAi.State()
    /** Legacy `shield.armor`; maps store each tank's own value, the vehicle table its maximum. */
    var armor: Int = state.armor
    /** Legacy `shield.power`: the pool the shield key draws from. */
    var shield: Int = state.shield
        private set
    /** Legacy `shield.icount`: blinks left of the start or bonus shield; the tank cannot be hurt while not 0. */
    private var shieldBlinks: Int = 0
    /** Legacy `shield.invun`: ticks until the next blink. */
    private var shieldBlinkTicks: Int = 0

    /** Ticks the start or bonus shield still lasts (0: none). */
    val invulnerableTicks: Int
        get() = if (shieldBlinks == 0) 0 else shieldBlinkTicks + (shieldBlinks - 1) * stepRounded(SHIELD_BLINK_TICKS)

    init {
        if (state.invulnerableTicks > 0) grantInvulnerability(state.invulnerableTicks)
    }
    /** True while the shield key is held and the pool is not empty (legacy `contr.shield && shield.power`). */
    var shieldActive: Boolean = false
        private set
    var fuel: Int = state.fuel
    var lives: Int = state.lives
        internal set
    val team: Int = state.team
    /** Legacy `weap.nf`: one refire timer shared by all weapons. */
    var primaryCooldownTicks: Int = state.primaryCooldownTicks
        private set
    /** Legacy `weap.nw`: ammo per weapon number. */
    private val ammo: IntArray = state.ammo.toWeaponArray()
    /** Legacy `weap.power`: hit power per weapon number, as stored with the tank in the map. */
    private val power: IntArray = state.power.toWeaponArray()
    private val spawnAmmo: List<Int> = state.ammo
    var currentWeapon: Int = state.currentWeapon
        private set
    private var weaponCycleCooldownTicks: Int = 0
    var respawnInTicks: Int = state.respawnInTicks
        private set

    val maxArmor: Int = if (state.maxArmor > 0) state.maxArmor else spec.armor.coerceAtLeast(state.armor).coerceAtLeast(1)
    val maxFuel: Int = if (state.maxFuel > 0) state.maxFuel else state.fuel.coerceAtLeast(0)
    internal val spawnX: Int = if (state.spawnX >= 0) state.spawnX
        else (body.position.x + body.size.width / 2f).toInt()
    internal val spawnY: Int = if (state.spawnY >= 0) state.spawnY
        else (body.position.y + body.size.height / 2f).toInt()
    private val spawnBodyDirection: Int = state.spawnBodyDirection.takeIf { it >= 0 } ?: state.bodyDirection
    private val spawnTurretDirection: Int = state.spawnTurretDirection.takeIf { it >= 0 } ?: state.turretDirection
    /** Legacy control_race(): a race tank restarts at the last checkpoint it passed. */
    private var restartX: Int = spawnX
    private var restartY: Int = spawnY
    private var restartDirection: Int = spawnBodyDirection
    /** Legacy flag hunts and races: destroyed vehicles come back without using up lives. */
    internal var infiniteLives: Boolean = false
    /** Legacy `und.flags`: flags this vehicle has picked up (the HUD shows a flag icon). */
    internal var flagsPicked: Int = 0

    /** Restart at ([x], [y]) facing [direction] after being destroyed. */
    internal fun moveSpawn(x: Int, y: Int, direction: Int) {
        restartX = x
        restartY = y
        restartDirection = direction
    }

    /** Restart at the pad again. */
    internal fun resetSpawn() = moveSpawn(spawnX, spawnY, spawnBodyDirection)

    /** Sub-pixel position (legacy `o->fx`, `o->fy`); [positionX]/[positionY] are its rounded values. */
    private var exactX: Float = body.position.x + body.size.width / 2f
    private var exactY: Float = body.position.y + body.size.height / 2f
    var positionX: Int = exactX.roundToInt()
        private set
    var positionY: Int = exactY.roundToInt()
        private set
    /** Velocity in px/s (legacy `motion.vx`, `motion.vy`). */
    var velocityX: Float = state.velocityX
        private set
    var velocityY: Float = state.velocityY
        private set

    /** Rocket lock-on marker (legacy `tnk.lock`): active while the fire key is held with rockets selected. */
    var lockActive: Boolean = false
        private set
    var lockX: Int = 0
        private set
    var lockY: Int = 0
        private set
    private var lockVelocityX: Int = 0
    private var lockVelocityY: Int = 0
    private var lockStepCooldownTicks: Int = 0
    /** Ticks the camera keeps showing the marker; legacy `lock.fired`. */
    var lockCameraTicks: Int = 0
        private set
    private var lockMaxX: Int = Int.MAX_VALUE
    private var lockMaxY: Int = Int.MAX_VALUE
    /** Legacy `game.mode == DUAL`: longer main-gun range and no marker range limit. */
    private var dualMode: Boolean = false
    private var lavaCooldownTicks: Int = 0
    /** Legacy `weap.reload`: set when a tank of another side dies; the next base visit refills this tank. */
    private var refillAllowed: Boolean = false
    val kind: VehicleKind = spec.kind
    /** Legacy make_tank(): planes start on the ground, every other vehicle at its `props.h`. */
    private val spawnHeight: Float = if (kind == VehicleKind.PLANE) 1f else spec.height.toFloat()
    /**
     * Legacy `o->fh`: 1 on the ground; a tank that drives into a pit sinks to -4 and is destroyed
     * there; helicopters hover at their `props.h` and planes climb up to theirs.
     */
    private var exactHeight: Float = spawnHeight
    /** Legacy `motion.vz`, in height units per second. */
    private var verticalSpeed: Float = 0f
    /** Legacy `o->h`, rounded from the exact height; below 1 while the tank falls into a pit. */
    val height: Int get() = exactHeight.roundToInt()

    internal fun setHeightForTest(height: Int) {
        exactHeight = height.toFloat()
    }

    internal fun setVelocityForTest(x: Float, y: Float) {
        velocityX = x
        velocityY = y
    }
    val falling: Boolean get() = exactHeight < 1f
    /** True for vehicles that fly (or a ground vehicle that left the ground); they are drawn with a shadow. */
    val airborne: Boolean get() = height > 1
    /** Legacy `und.nw == RUNWAY`: the only ground a plane may touch down on. */
    private var onRunway: Boolean = false
    /** Legacy `und.nw == WATER`: most weapons cannot be fired from the water. */
    private var onWater: Boolean = false
    /** Legacy `motion.boost`: ticks of the speed-up weapon left. */
    var boostTicks: Int = 0
        private set
    /** Legacy `und.smokes`: smoke-screen clouds still to come. */
    private var smokeScreenLeft: Int = 0
    private var smokeScreenCooldownTicks: Int = 0
    /** Legacy `invis.count` (2, 1, 0) with its countdown and the driver's blink. */
    private var cloakCount: Int = 0
    private var cloakCountdownTicks: Int = 0
    var cloakFlash: Boolean = true
        private set
    private var cloakFlashCooldownTicks: Int = 0
    val cloaked: Boolean get() = cloakCount != 0
    /** Legacy `weap.extragun`: the extra-gun bonus adds one chain gun. */
    var extraGun: Boolean = false
        private set
    /** Legacy `msg`: the pilot's two lines and the steps (29 → 0) they stay on screen. */
    var messageLine1: String = ""
        private set
    var messageLine2: String = ""
        private set
    var messageSteps: Int = 0
        private set
    private var messageStepTicks: Int = 0
    /** Legacy `map.night`: the light switch is a weapon and vehicles light the dark. */
    private var night: Boolean = false
    /** Legacy `last_min.light_on`. */
    var lightOn: Boolean = true
        private set

    /** Legacy create_message(). */
    fun showMessage(line1: String, line2: String = "") {
        messageLine1 = line1.take(29)
        messageLine2 = line2.take(29)
        messageSteps = MESSAGE_STEPS
        messageStepTicks = 0
    }

    /** Legacy msg_control(). */
    private fun stepMessage() {
        if (messageSteps == 0) return
        messageStepTicks = messageStepTicks.afterStep()
        if (messageStepTicks == 0) {
            messageSteps -= 1
            messageStepTicks = MESSAGE_STEP_TICKS
        }
    }

    /**
     * Legacy light_tank(): the headlight cones of this vehicle (none while cloaked, switched off or
     * destroyed). They sit on the turret of a turret vehicle and point where it points; for the
     * other weapons than main gun, flamer and mortar they point along the hull.
     */
    fun headlights(): List<com.tankarena.protocol.snapshot.LightCone> {
        if (!night || !lightOn || cloaked || armor <= 0 || spec.lights.isEmpty()) return emptyList()
        var x = positionX
        var y = positionY
        val d1 = if (turretMode == TurretMode.TURRET) {
            val (tx, ty) = rotate(bodyDirection, spec.turretOffset)
            x += tx
            y += ty
            turretDirection
        } else {
            bodyDirection
        }
        val d2 = if (currentWeapon == WEAPON_MAIN || currentWeapon == WEAPON_FLAME || currentWeapon == WEAPON_MORTAR) d1 else bodyDirection
        return spec.lights.map { light ->
            val (lx, ly) = rotate(d1, light.offset)
            com.tankarena.protocol.snapshot.LightCone(light.size, x + lx, y + ly, d2.mod(16))
        }
    }

    /** Where this computer tank aims locked weapons (legacy `computer.target_x/y`); set by the match. */
    internal var aiTargetX: Int = -1
    internal var aiTargetY: Int = -1
    private var dyingTicks: Int = 0
    private var smokeCooldownTicks: Int = 0

    private var pendingIntent: PlayerIntentFrame = PlayerIntentFrame()
    private var hullTurnCooldownTicks: Int = 0

    /** Legacy `tdone(motion.ndir)`: a turn pressed now happens this tick. */
    internal val steeringReady: Boolean get() = hullTurnCooldownTicks <= STEP_TICKS

    /** Legacy `motion.v`: speed along the hull, negative when reversing (px/s). */
    internal val forwardSpeed: Float
        get() {
            val (fx, fy) = LegacyDirections.unitVector(bodyDirection)
            return velocityX * fx + velocityY * fy
        }
    private var turretTurnCooldownTicks: Int = 0
    private var terrainSpeedMultiplier: Float = 1.0f
    private var pendingResolveX: Int = 0
    private var pendingResolveY: Int = 0
    private var pendingDamageThisTick: Int = 0
    private var pendingRocketProofDamage: Int = 0
    private var pendingDestroy: Boolean = false

    private val pendingFireRequests = mutableListOf<FireRequest>()
    /** Legacy `weap.activegun`: the main-gun barrel that fires next. */
    private var activeGun: Int = 0
    private var pendingMineRequest: MineRequest? = null
    private var pendingRocketRequest: RocketRequest? = null
    private var pendingMortarRequest: MortarRequest? = null
    private var pendingAbombRequest: AbombRequest? = null
    private val pendingSoldierRequests = mutableListOf<SoldierRequest>()
    private var destroyedThisTick: Boolean = false
    private var spawnedThisTick: Boolean = false
    private var damageEventAmount: Int = 0
    private val pendingEffects = mutableListOf<GameEvent>()
    private val random = Random(tankType * 7919 + spawnX * 31 + spawnY)

    /**
     * A bullet or flame leaving the tank. Speeds are in px per tick; [rangePx] is how far it flies
     * (legacy `b->dist`).
     */
    data class FireRequest(
        val originX: Int,
        val originY: Int,
        val direction: Int,
        val speed: Float,
        val rangePx: Int,
        val damage: Int,
        val weaponKind: Int = WEAPON_MAIN,
        /** Legacy `b->safety`: pixels flown before the shot can hit anything. */
        val safetyPx: Int = 0,
    ) {
        val velocityX: Float get() = LegacyDirections.unitVector(direction).first * speed
        val velocityY: Float get() = LegacyDirections.unitVector(direction).second * speed
        val ttlTicks: Int get() = (rangePx / speed).roundToInt().coerceAtLeast(1)
    }

    data class MineRequest(
        val originX: Int,
        val originY: Int,
        val damage: Int,
        val radius: Int,
    )

    data class RocketRequest(
        val originX: Int,
        val originY: Int,
        val targetX: Int,
        val targetY: Int,
        val damage: Int,
    )

    data class MortarRequest(
        val originX: Int,
        val originY: Int,
        val velocityX: Float,
        val velocityY: Float,
        val damage: Int,
        val maxRadius: Int,
        val travelTicks: Int = MORTAR_TRAVEL_TICKS,
        /** Legacy HT_PLANEBOMB: the height a plane dropped it from; 0 for a mortar shell. */
        val dropHeight: Int = 0,
    )

    /** Legacy create_abomb(): an A-bomb at ([x], [y]) that goes off after [countdown] × 60 ticks. */
    data class AbombRequest(val x: Int, val y: Int, val countdown: Int)

    /** Legacy make_man(): a soldier jumping out at ([x], [y]) and walking to the marker. */
    data class SoldierRequest(
        val x: Int,
        val y: Int,
        val targetX: Int,
        val targetY: Int,
        /** Gunners fire chain-gun rounds, the others flames. */
        val gunner: Boolean,
        val power: Int,
    )

    fun applyIntent(intent: PlayerIntentFrame) {
        pendingIntent = intent
    }

    fun queueDamage(amount: Int) {
        if (amount <= 0) return
        if (isWreck) wreckBlowUp = true
        pendingDamageThisTick += amount
    }

    /** Damage the shield key cannot stop, though the start shield still does (legacy HT_ROCKET). */
    fun queueUnshieldableDamage(amount: Int) {
        if (amount <= 0) return
        if (isWreck) wreckBlowUp = true
        pendingRocketProofDamage += amount
    }

    /** Legacy HT_DESTROY (pits, crashes): kills through every shield. */
    fun queueDestroy() {
        if (isWreck) wreckBlowUp = true
        pendingDestroy = true
    }

    /**
     * Legacy control_wreck(): ticks until the wreck blows up, from when its dying explosions end;
     * -1 while the vehicle is not a wreck.
     */
    private var wreckLifeTicks: Int = -1

    /** A burnt-out vehicle (legacy `funct.hit = hit_wreck`): any hit blows it up. */
    val isWreck: Boolean get() = armor <= 0 && wreckLifeTicks >= 0 && !wreckGone

    /** The wreck should blow up (its time ran out, or something hit it); the match decides. */
    internal var wreckBlowUp: Boolean = false

    /** The wreck has blown up and is gone; a player's vehicle waits for its respawn unseen. */
    var wreckGone: Boolean = false
        private set

    internal fun removeWreck() {
        wreckGone = true
        wreckBlowUp = false
    }

    /** What a respawn leaves behind: the old wreck, as a vehicle of its own (legacy control_player()). */
    private var leftWreck: Tank? = null

    internal fun drainLeftWreck(): Tank? = leftWreck.also { leftWreck = null }

    /** Makes this a wreck with [lifeTicks] to go. */
    internal fun becomeWreck(lifeTicks: Int) {
        armor = 0
        dyingTicks = 0
        wreckLifeTicks = lifeTicks
        smokeCooldownTicks = WRECK_SMOKE_MIN_TICKS + random.nextInt(WRECK_SMOKE_SPREAD_TICKS)
    }

    fun grantShield(amount: Int) {
        if (amount <= 0) return
        shield = (shield + amount).coerceAtMost(SHIELD_MAX_POWER)
    }

    /** Legacy `shield.invun = tinit(holdTicks)`, `shield.icount = 15`: steady for [holdTicks], then 15 blinks. */
    fun grantInvulnerability(holdTicks: Int) {
        if (holdTicks <= 0) return
        shieldBlinkTicks = holdTicks
        shieldBlinks = SHIELD_BLINKS
    }

    /**
     * Legacy write_tank(): the ARMOR ring shows while the shield key works, and while
     * `shield.icount` is odd — steadily until the last 15 blinks of 5 ticks, then blinking.
     */
    val shieldRingVisible: Boolean
        get() = shieldActive || shieldBlinks and 1 == 1

    /** Legacy pickup_bonus(): only ground vehicles pick bonuses up. */
    val picksUpBonuses: Boolean get() = kind == VehicleKind.TANK || kind == VehicleKind.CAR

    /**
     * Legacy pickup_bonus() (src/objects/bonus.c): what each of the 14 bonus types gives; returns
     * the second line of the pickup message.
     */
    fun pickUpBonus(type: Int): String = when (type) {
        0 -> { ammo[WEAPON_MAIN] += 10; "10 GRENADES" }
        1 -> if (!extraGun) {
            extraGun = true
            "EXTRA GUN"
        } else {
            ammo[WEAPON_CHAIN] += 500
            "500 ROUNDS"
        }
        2 -> { fuel = spec.maxFuel; "FUEL" }
        3 -> { ammo[WEAPON_MINE] += 5; "5 MINES" }
        4 -> { ammo[WEAPON_ROCKET] += 3; "3 ROCKETS" }
        5 -> { ammo[WEAPON_MORTAR] += 1; "A MORTAR SHELL" }
        6 -> { ammo[WEAPON_ABOMB] += 1; "NUCLEAR BOMB" }
        7 -> { ammo[WEAPON_GUNNERS] += 5; "5 GUNNERS" }
        8 -> { ammo[WEAPON_FLAMERS] += 5; "5 FLAMERS" }
        9 -> { ammo[WEAPON_SMOKE] += 1; "SMOKE SCREEN" }
        10 -> { ammo[WEAPON_CLOAK] += 1; "A CLOAKING DEVICE" }
        11 -> { ammo[WEAPON_SPEEDUP] += 1; "SPEEDUP" }
        12 -> { grantInvulnerability(INVULNERABILITY_BONUS_HOLD_TICKS); "INVUNERABILITY" }
        13 -> { armor = (armor + ARMOR_BONUS).coerceAtMost(spec.armor); "ARMOR!" }
        else -> "JUNK!"
    }

    /** Ammo left for a legacy weapon number. */
    fun ammoFor(weapon: Int): Int = ammo.getOrElse(weapon) { 0 }

    /** All ammo counts, indexed by legacy weapon number. */
    val ammoCounts: List<Int> get() = ammo.toList()

    /** Hit power of a legacy weapon number for this tank. */
    fun powerFor(weapon: Int): Int = power.getOrElse(weapon) { 0 }

    /** Map limits for the rocket marker and the player-vs-player rules (legacy `game.mode == DUAL`). */
    internal fun configureForMatch(maxX: Int, maxY: Int, dual: Boolean, night: Boolean = false, cheats: Cheats = Cheats()) {
        lockMaxX = maxX
        lockMaxY = maxY
        dualMode = dual
        this.night = night
        this.cheats = cheats
    }

    private var cheats: Cheats = Cheats()

    /** Legacy `und.nw`: the ground under the vehicle at its last step (null at first: NOTHING). */
    private var underground: TerrainTile? = null
    /** Legacy `und.neffect`: ticks until the next splash, oil splash or dust cloud. */
    private var undergroundEffectTicks: Int = 0

    /**
     * Legacy get_underground() and underground_effects() (src/tanks/control.c:331-383), after the
     * vehicle has moved: low vehicles splash into and out of water and bubble into and out of lava;
     * lava burns [LAVA_DAMAGE] every [LAVA_INTERVAL_TICKS]; otherwise, moving or flying, water
     * splashes, oil spatters and bare desert ground raises dust from time to time.
     */
    internal fun stepUnderground(tile: TerrainTile, desert: Boolean) {
        val old = underground
        underground = tile
        val low = height < 2
        fun around(spread: Int) = (positionX - spread / 2 + random.nextInt(spread)) to (positionY - spread / 2 + random.nextInt(spread))
        fun crossed(material: TerrainMaterial) = (old?.material == material) != (tile.material == material)
        if (low && crossed(TerrainMaterial.WATER)) {
            repeat(5) { around(16).let { (x, y) -> pendingEffects += GameEvent.Effect(EffectKind.SPLASH, x, y) } }
            pendingEffects += GameEvent.Sound(SoundKind.SPLASH, positionX, positionY)
        }
        if (low && crossed(TerrainMaterial.LAVA)) {
            repeat(5) { around(16).let { (x, y) -> pendingEffects += GameEvent.Effect(EffectKind.LAVA, x, y) } }
        }
        lavaCooldownTicks = lavaCooldownTicks.afterStep()
        undergroundEffectTicks = undergroundEffectTicks.afterStep()
        if (low && tile.material == TerrainMaterial.LAVA && lavaCooldownTicks == 0) {
            queueDamage(LAVA_DAMAGE)
            around(8).let { (x, y) -> pendingEffects += GameEvent.Effect(EffectKind.LAVA, x, y) }
            lavaCooldownTicks = LAVA_INTERVAL_TICKS
            return
        }
        // Legacy `motion.v` is a whole number: a vehicle slower than 1 px/s stands still.
        if ((height <= 1 && speed < 1f) || undergroundEffectTicks > 0) return
        when {
            tile.material == TerrainMaterial.WATER -> {
                around(16).let { (x, y) -> pendingEffects += GameEvent.Effect(EffectKind.SPLASH, x, y) }
                undergroundEffectTicks = 30 + random.nextInt(30)
            }
            tile.material == TerrainMaterial.OIL -> {
                around(8).let { (x, y) -> pendingEffects += GameEvent.Effect(EffectKind.OIL_SPLASH, x, y) }
                undergroundEffectTicks = 30 + random.nextInt(20)
            }
            tile.bare && desert -> {
                around(16).let { (x, y) -> pendingEffects += GameEvent.Effect(EffectKind.DUST, x, y) }
                undergroundEffectTicks = 10 + random.nextInt(10)
            }
        }
    }

    /** Legacy new_velocity() with `in_pit()`: start sinking into the pit under the tank. */
    internal fun startFalling() {
        if (falling || armor <= 0) return
        exactHeight = 0.99f
        verticalSpeed = 0f
    }

    internal fun allowBaseRefill() {
        refillAllowed = true
    }

    /**
     * Legacy tank_refill(): a player-vs-player tank parked on its own start pad (within 7 px) after
     * an opponent died gets its pad's ammo (or else its vehicle's), full fuel and armor back, once
     * per death.
     */
    private fun stepBaseRefill() {
        if (!refillAllowed || startPadType < 0 || armor <= 0) return
        val dx = positionX - spawnX
        val dy = positionY - spawnY
        if (dx * dx + dy * dy > BASE_RADIUS_PX * BASE_RADIUS_PX) return
        (padAmmo ?: spec.ammo).forEachIndexed { weapon, count -> if (weapon < WEAPON_COUNT && ammo[weapon] < count) ammo[weapon] = count }
        fuel = spec.maxFuel
        armor = spec.armor
        refillAllowed = false
    }

    /** Direction the main gun, flamer and mortar fire in: the turret only for `MAIN_TURRET` vehicles. */
    val aimDirection: Int
        get() = if (turretMode == TurretMode.TURRET) turretDirection else bodyDirection

    /** Speed in px/s (legacy `motion.v`). */
    val speed: Float get() = hypot(velocityX, velocityY)

    /** Set terrain speed multiplier from the terrain grid. Called each tick by Match. */
    fun setTerrainSpeedMultiplier(multiplier: Float) {
        terrainSpeedMultiplier = multiplier.coerceAtLeast(0f)
    }

    /** Enforce a specific weapon from an enforcer actor. */


    /** The exact position (legacy `fx`, `fy`). */
    internal val exactPosition: Pair<Float, Float> get() = exactX to exactY

    /** Legacy transportation(): carried along by a conveyor or turntable; the hull keeps its heading. */
    internal fun carryTo(x: Float, y: Float) {
        exactX = x
        exactY = y
        positionX = x.roundToInt()
        positionY = y.roundToInt()
        syncBodyFromPosition()
    }

    /** Teleport the tank to a new position (used by warp actors). */
    fun teleportTo(x: Int, y: Int) {
        exactX = x.toFloat()
        exactY = y.toFloat()
        positionX = x
        positionY = y
        lastOverlap = Int.MAX_VALUE
        syncBodyFromPosition()
    }

    override fun update(deltaTimeInMilliseconds: Int) {
        stepMessage()
        if (falling && armor > 0) {
            stepFall()
            pendingIntent = PlayerIntentFrame()
            return
        }
        if (armor <= 0) {
            if (airborne) {
                // Legacy new_velocity(): a destroyed aircraft loses its lift and drops, exploding
                // all the way down; it becomes a wreck where it hits the ground.
                stepVelocity(thrust = false)
                advancePosition()
                stepAltitude()
                dyingTicks = dyingTicks.coerceAtLeast(STEP_TICKS + 1)
            } else {
                velocityX = 0f
                velocityY = 0f
            }
            stepWreck()
            if (respawnInTicks > 0) {
                respawnInTicks = respawnInTicks.afterStep()
                if (respawnInTicks == 0) respawn()
            }
            lockActive = false
            lockCameraTicks = 0
            shieldActive = false
            pendingIntent = PlayerIntentFrame()
            return
        }
        stepShields()
        stepCloak()
        stepSmoke()
        stepSmokeScreen()
        stepBaseRefill()
        boostTicks = boostTicks.afterStep()
        lockCameraTicks = lockCameraTicks.afterStep()
        if (lockActive) {
            // Legacy control_tank(): while the marker is out the drive keys steer it, so the tank
            // neither turns nor accelerates and cannot switch weapons.
            stepVelocity(thrust = false)
            advancePosition()
            stepAltitude()
            stepCooldowns()
            stepRocketLock()
        } else {
            stepWeaponCycle()
            stepHullTurn()
            stepTurretTurn()
            stepVelocity(thrust = true)
            advancePosition()
            stepAltitude()
            stepFire()
        }
        pendingIntent = PlayerIntentFrame()
    }

    private fun respawn() {
        // Legacy control_player() makes a new vehicle and leaves the old one where it lies.
        if (isWreck) {
            leftWreck = Tank(
                save().copy(
                    playerIndex = -1,
                    controllerId = -1,
                    ref = 0,
                    lives = 0,
                    goalContribution = 0,
                    startPadType = -1,
                    padAmmo = null,
                ),
            ).also { it.becomeWreck(wreckLifeTicks) }
        }
        wreckLifeTicks = -1
        wreckGone = false
        wreckBlowUp = false
        exactX = restartX.toFloat()
        exactY = restartY.toFloat()
        positionX = restartX
        positionY = restartY
        lastOverlap = Int.MAX_VALUE
        bodyDirection = restartDirection
        turretDirection = if (restartX == spawnX && restartY == spawnY) spawnTurretDirection else restartDirection
        flagsPicked = 0
        armor = maxArmor
        fuel = maxFuel
        shield = SHIELD_START_POWER
        if (computer) shieldBlinks = 0 else grantInvulnerability(START_SHIELD_HOLD_TICKS)
        velocityX = 0f
        velocityY = 0f
        primaryCooldownTicks = 0
        respawnInTicks = 0
        dyingTicks = 0
        exactHeight = spawnHeight
        verticalSpeed = 0f
        boostTicks = 0
        smokeScreenLeft = 0
        cloakCount = 0
        cloakFlash = true
        extraGun = false
        spawnAmmo.forEachIndexed { index, count -> if (index < WEAPON_COUNT) ammo[index] = count }
        lockActive = false
        lockCameraTicks = 0
        spawnedThisTick = true
        syncBodyFromPosition()
    }

    fun drainLifecycleEvents(actorId: Long): List<GameEvent> {
        if (!destroyedThisTick && !spawnedThisTick && damageEventAmount == 0 && pendingEffects.isEmpty()) return emptyList()
        val events = mutableListOf<GameEvent>()
        events += pendingEffects
        pendingEffects.clear()
        if (damageEventAmount > 0) {
            events += GameEvent.DamageTaken(actorId, damageEventAmount)
            damageEventAmount = 0
        }
        if (destroyedThisTick) {
            events += GameEvent.TankDestroyed(actorId)
            events += GameEvent.Sound(kind = SoundKind.EXPLODE, x = positionX, y = positionY)
            destroyedThisTick = false
        }
        if (spawnedThisTick) {
            events += GameEvent.TankSpawned(actorId)
            spawnedThisTick = false
        }
        return events
    }

    fun drainFireRequests(): List<FireRequest> {
        if (pendingFireRequests.isEmpty()) return emptyList()
        val requests = pendingFireRequests.toList()
        pendingFireRequests.clear()
        return requests
    }

    fun drainMineRequest(): MineRequest? {
        val request = pendingMineRequest
        pendingMineRequest = null
        return request
    }

    fun drainRocketRequest(): RocketRequest? {
        val request = pendingRocketRequest
        pendingRocketRequest = null
        return request
    }

    fun drainMortarRequest(): MortarRequest? {
        val request = pendingMortarRequest
        pendingMortarRequest = null
        return request
    }

    fun drainAbombRequest(): AbombRequest? {
        val request = pendingAbombRequest
        pendingAbombRequest = null
        return request
    }

    fun drainSoldierRequests(): List<SoldierRequest> {
        if (pendingSoldierRequests.isEmpty()) return emptyList()
        return pendingSoldierRequests.toList().also { pendingSoldierRequests.clear() }
    }


    override fun applyPendingResolutions(worldWidthPixels: Int, worldHeightPixels: Int) {
        if (pendingResolveX == 0 && pendingResolveY == 0 && pendingDamageThisTick == 0 && pendingRocketProofDamage == 0 && !pendingDestroy) return
        if (pendingResolveX != 0 || pendingResolveY != 0) {
            positionX = wrapped((positionX + pendingResolveX).toFloat(), worldWidthPixels).roundToInt()
            positionY = wrapped((positionY + pendingResolveY).toFloat(), worldHeightPixels).roundToInt()
            exactX = positionX.toFloat()
            exactY = positionY.toFloat()
        }
        // Legacy hit_tank(): the start shield stops everything while on or above the ground, the
        // shield key everything but rockets.
        var incoming = 0
        // Legacy hit_tank() with -GOD: good vehicles take nothing but HT_DESTROY.
        val god = cheats.god && team == 0
        if (!god && (invulnerableTicks <= 0 || height < 1)) {
            incoming += pendingRocketProofDamage
            if (!shieldActive) incoming += pendingDamageThisTick
        }
        if (pendingDestroy) incoming = armor
        if (incoming > 0 && armor > 0) {
            val dealt = incoming.coerceAtMost(armor)
            armor = (armor - incoming).coerceAtLeast(0)
            if (armor == 0) {
                destroyedThisTick = true
                // Legacy hit_tank(): the atomic tank goes off like an A-bomb.
                if (tankType == ATOMIC_TANK) pendingAbombRequest = AbombRequest(positionX, positionY, countdown = 0)
                dyingTicks = DYING_TICKS
                smokeCooldownTicks = WRECK_SMOKE_MIN_TICKS + random.nextInt(WRECK_SMOKE_SPREAD_TICKS)
                if (!infiniteLives) lives = (lives - 1).coerceAtLeast(0)
                if (lives > 0) respawnInTicks = RESPAWN_DELAY_TICKS
                velocityX = 0f
                velocityY = 0f
            } else if (dealt > 0) {
                damageEventAmount += dealt
            }
        }
        pendingResolveX = 0
        pendingResolveY = 0
        pendingDamageThisTick = 0
        pendingRocketProofDamage = 0
        pendingDestroy = false
        syncBodyFromPosition()
    }

    override fun save(): State = State(
        body = body,
        bodyDirection = bodyDirection,
        turretDirection = turretDirection,
        playerIndex = playerIndex,
        controllerId = controllerId,
        ref = ref,
        computer = computer,
        goalContribution = goalContribution,
        startPadType = startPadType,
        padAmmo = padAmmo,
        aiAttackTanks = aiAttackTanks,
        aiActivationRadius = aiActivationRadius,
        aiActivator = aiActivator,
        aiStayActive = aiStayActive,
        aiActive = aiActive,
        aiAttackAt = aiAttackAt,
        aiAttackGoals = aiAttackGoals,
        aiWaveDelay = aiWaveDelay,
        aiFireDelay = authoredFireDelay,
        wayType = wayType,
        wayCurrent = wayCurrent,
        wayPoints = wayPoints.toList(),
        tankType = tankType,
        armor = armor,
        shield = shield,
        invulnerableTicks = invulnerableTicks,
        fuel = fuel,
        lives = lives,
        team = team,
        velocityX = velocityX,
        velocityY = velocityY,
        primaryCooldownTicks = primaryCooldownTicks,
        ammo = ammo.toList(),
        power = power.toList(),
        currentWeapon = currentWeapon,
        respawnInTicks = respawnInTicks,
        maxArmor = maxArmor,
        maxFuel = maxFuel,
        spawnX = spawnX,
        spawnY = spawnY,
        spawnBodyDirection = spawnBodyDirection,
        spawnTurretDirection = spawnTurretDirection,
    )

    /** Legacy left_right(): one 1/16 turn every `motion.rotation` ticks. */
    private fun stepHullTurn() {
        hullTurnCooldownTicks = hullTurnCooldownTicks.afterStep()
        if (hullTurnCooldownTicks > 0) return
        when {
            pendingIntent.turnLeft && !pendingIntent.turnRight -> {
                bodyDirection = LegacyDirections.stepLeft(bodyDirection)
                hullTurnCooldownTicks = spec.motion.rotation
                resetOverlap()
            }

            pendingIntent.turnRight && !pendingIntent.turnLeft -> {
                bodyDirection = LegacyDirections.stepRight(bodyDirection)
                hullTurnCooldownTicks = spec.motion.rotation
                resetOverlap()
            }
        }
    }

    private fun stepTurretTurn() {
        when (turretMode) {
            // Legacy aim_left()/aim_right() only act on MAIN_TURRET guns.
            TurretMode.STATIC -> {
                turretDirection = bodyDirection
                return
            }
            // Legacy tank_effects(): MAIN_ROTATE tops spin one step per tick.
            TurretMode.ROTATE -> {
                turretDirection = LegacyDirections.stepRight(turretDirection)
                return
            }
            TurretMode.TURRET -> Unit
        }
        turretTurnCooldownTicks = turretTurnCooldownTicks.afterStep()
        if (turretTurnCooldownTicks > 0) return
        when {
            pendingIntent.aimLeft && !pendingIntent.aimRight -> {
                turretDirection = LegacyDirections.stepLeft(turretDirection)
                turretTurnCooldownTicks = spec.motion.rotation
            }

            pendingIntent.aimRight && !pendingIntent.aimLeft -> {
                turretDirection = LegacyDirections.stepRight(turretDirection)
                turretTurnCooldownTicks = spec.motion.rotation
            }
        }
    }

    /**
     * Legacy new_velocity(): thrust `acc / mass` along the hull while a drive key is held, then
     * friction `(ff_v·|v| + ff_0) / mass` along and `(fs_v·|v| + fs_0) / mass` across the hull.
     */
    private fun stepVelocity(thrust: Boolean) {
        val motion = spec.motion
        val (fx, fy) = LegacyDirections.unitVector(bodyDirection)
        var along = velocityX * fx + velocityY * fy
        var across = velocityX * -fy + velocityY * fx
        val driving = pendingIntent.forward != pendingIntent.reverse
        val onGround = height == 1
        val flies = kind == VehicleKind.CHOPPER || kind == VehicleKind.PLANE
        if (thrust && driving && fuel > 0 && (flies || onGround)) {
            var dv = STEP_SECONDS * motion.acc / motion.mass
            if (boostTicks > 0) dv *= BOOST_FACTOR
            // Legacy: the ground only slows what touches it.
            if (height <= 1) dv *= terrainSpeedMultiplier
            // A plane has no reverse: either key opens the throttle.
            along += if (pendingIntent.forward || kind == VehicleKind.PLANE) dv else -dv
        } else if (kind == VehicleKind.PLANE && height > 1 && verticalSpeed < 0f && along > 0f) {
            // A gliding plane trades height for speed.
            along = (along - GLIDE_FACTOR * verticalSpeed * STEP_SECONDS).coerceAtLeast(0f)
        }
        // Rolling resistance (`ff_0`) only on the ground and for helicopters; sideways grip only
        // for aircraft and on the ground, while a vehicle in the air just drifts.
        val alongFriction = (motion.ffV * abs(along) + if (kind == VehicleKind.CHOPPER || onGround) motion.ff0 else 0f) *
            STEP_SECONDS / motion.mass
        along = decay(along, alongFriction)
        val acrossForce = if (flies || onGround) motion.fsV * abs(across) + motion.fs0 else motion.ffV * abs(across)
        across = decay(across, acrossForce * STEP_SECONDS / motion.mass)
        velocityX = along * fx + across * -fy
        velocityY = along * fy + across * fx
    }

    /**
     * Legacy new_position() (src/tanks/control.c): try the move this step's velocity asks for.
     * Vehicles not driven by the computer burn one unit of fuel per pixel of the move, even when the
     * move is then refused; a vehicle leaving the map comes back in on the other side. The move is
     * refused when the vehicle's picture would overlap standing structures by more pixels than it
     * does now (it then bounces, [staticCollide]), or when it would come within the two vehicles'
     * radii of another vehicle ([collideWithVehicle]).
     */
    private fun advancePosition() {
        val dx = velocityX * STEP_SECONDS
        val dy = velocityY * STEP_SECONDS
        var nextX = exactX + dx
        var nextY = exactY + dy
        if (!computer && !cheats.god && (dx != 0f || dy != 0f)) {
            fuelDebt += hypot(dx, dy)
            val whole = fuelDebt.toInt()
            if (whole > 0) {
                fuel = (fuel - whole).coerceAtLeast(0)
                fuelDebt -= whole
            }
        }
        val surroundings = surroundings
        if (surroundings != null) {
            nextX = wrapped(nextX, surroundings.worldWidth)
            nextY = wrapped(nextY, surroundings.worldHeight)
        }
        if (surroundings != null && (dx != 0f || dy != 0f)) {
            val roundedX = nextX.roundToInt()
            val roundedY = nextY.roundToInt()
            if (lastOverlap == Int.MAX_VALUE) resetOverlap()
            val hitTile = IntArray(2)
            val overlap = structureOverlap(roundedX, roundedY, hitTile)
            if (overlap > lastOverlap) {
                staticCollide()
                // Legacy new_position(): a plane that touches a building crashes.
                if (kind == VehicleKind.PLANE) queueDestroy()
                // Legacy new_position(): a computer tank that hits a building other than its
                // target panics for a moment.
                if (computer && playerIndex < 0) {
                    val target = ComputerTankAi.targetTile(this)
                    if (target != null && target != (hitTile[0] to hitTile[1])) ComputerTankAi.crashed(this, surroundings.tick)
                }
                crashInto(hitTile[0], hitTile[1])
                return
            }
            lastOverlap = overlap
            // Legacy check_obj_collide(): a train is an obstacle a vehicle bounces off.
            if (surroundings.trainAt(roundedX, roundedY, spec.radius, height)) {
                staticCollide()
                return
            }
            val other = surroundings.tanks().firstOrNull { other ->
                other !== this && abs(other.height - height) < 2 &&
                    inRadius(other.positionX - roundedX, other.positionY - roundedY, spec.radius + other.spec.radius)
            }
            if (other != null) {
                // Legacy check_obj_collide(): bumping into a vehicle that is not its target (or
                // during an attack pass) makes a computer tank panic too.
                if (computer && playerIndex < 0 &&
                    (ComputerTankAi.targetRef(this) !== other || ComputerTankAi.inAttackWave(this, surroundings.tick))
                ) {
                    ComputerTankAi.crashed(this, surroundings.tick)
                }
                collideWithVehicle(other)
                return
            }
            launchOffRamp(surroundings, roundedX, roundedY)
        }
        exactX = nextX
        exactY = nextY
        positionX = exactX.roundToInt()
        positionY = exactY.roundToInt()
        syncBodyFromPosition()
    }

    private var fuelDebt: Float = 0f

    /**
     * Legacy new_position(): `if (x < -15) x += map.sx * b_size; if (x > map.sx * b_size - 15) ...`
     * on the picture's corner, which is 1 and size + 1 for the centre.
     */
    private fun wrapped(centre: Float, size: Int): Float = when {
        centre < 1f -> centre + size
        centre > size + 1f -> centre - size
        else -> centre
    }

    /**
     * Legacy on_big_ramp()/on_small_ramp() in new_position(): a tank or car on the ground that
     * rolls off a ramp tile over its high edge is thrown up at a fifth (big ramp) or a tenth
     * (small ramp) of its speed along the hull, and flies until gravity brings it down.
     */
    private fun launchOffRamp(surroundings: TankSurroundings, nextX: Int, nextY: Int) {
        if (height != 1 || (kind != VehicleKind.TANK && kind != VehicleKind.CAR)) return
        val tileX = Math.floorDiv(positionX, LEGACY_TILE_SIZE)
        val tileY = Math.floorDiv(positionY, LEGACY_TILE_SIZE)
        val nextTileX = Math.floorDiv(nextX, LEGACY_TILE_SIZE)
        val nextTileY = Math.floorDiv(nextY, LEGACY_TILE_SIZE)
        val type = surroundings.baseType(tileX, tileY) ?: return
        val ramp = type.removePrefix("B_").removePrefix("S_")
        if (!ramp.startsWith("RAMP_")) return
        val offTheEdge = when (ramp) {
            "RAMP_L" -> nextTileX == tileX - 1
            "RAMP_R" -> nextTileX == tileX + 1
            "RAMP_U" -> nextTileY == tileY - 1
            "RAMP_D" -> nextTileY == tileY + 1
            else -> false
        }
        if (!offTheEdge) return
        val (fx, fy) = LegacyDirections.unitVector(bodyDirection)
        val along = abs(velocityX * fx + velocityY * fy)
        verticalSpeed += along / if (type.startsWith("B_")) BIG_RAMP_LIFT else SMALL_RAMP_LIFT
    }

    /** The structures and other vehicles this tank moves among; set by the match. */
    internal var surroundings: TankSurroundings? = null
    /** Legacy `motion.overlap`: pixels the picture overlaps structures by at the current spot. */
    private var lastOverlap: Int = Int.MAX_VALUE
    /** Legacy `motion.nhit`: crash damage is dealt at most every 10 ticks. */
    private var crashCooldownTicks: Int = 0

    /** The body picture's mask, looked up by name only when the type or direction changed. */
    private var maskKey: Int = -1
    private var maskCache: PictureMasks.Mask? = null

    private fun bodyMask(): PictureMasks.Mask? {
        val key = tankType * 16 + bodyDirection.mod(16)
        if (key != maskKey) {
            maskCache = PictureMasks.mask(LegacySpriteResources.nameForTankBody(tankType, bodyDirection.mod(16)))
            maskKey = key
        }
        return maskCache
    }

    /** Legacy check_obj_hit_point() for a vehicle: is world point ([x], [y]) on its body picture? */
    internal fun bodyCovers(x: Int, y: Int): Boolean {
        val left = positionX - LEGACY_TILE_SIZE / 2
        val top = positionY - LEGACY_TILE_SIZE / 2
        if (x < left || y < top || x > left + PictureMasks.SIZE || y > top + PictureMasks.SIZE) return false
        return bodyMask()?.isOpaque(x - left, y - top) == true
    }

    /**
     * Legacy overlap() for this tank's body picture centred on ([x], [y]): on the ground the
     * buildings themselves, at height 2..7 only their tall top-layer parts, and nothing higher up.
     */
    private fun structureOverlap(x: Int, y: Int, firstHit: IntArray? = null): Int =
        overlapWithMask(bodyMask(), x, y, firstHit)

    /**
     * Legacy overlap(o, tnk.tank->pic[[direction]], x, y, ...) for the computer driver: the overlap
     * this vehicle would have facing [direction] centred on ([x], [y]); [firstHit] gets the tile.
     */
    internal fun overlapAt(direction: Int, x: Int, y: Int, firstHit: IntArray? = null): Int =
        overlapWithMask(PictureMasks.mask(LegacySpriteResources.nameForTankBody(tankType, direction.mod(16))), x, y, firstHit)

    private fun overlapWithMask(mask: PictureMasks.Mask?, x: Int, y: Int, firstHit: IntArray?): Int {
        val grid = surroundings?.structures ?: return 0
        mask ?: return 0
        val left = x - LEGACY_TILE_SIZE / 2
        val top = y - LEGACY_TILE_SIZE / 2
        return when {
            height < 2 -> grid.overlap(mask, left, top, firstHit)
            height < 8 -> grid.overlapTop(mask, left, top, firstHit)
            else -> 0
        }
    }

    /** Legacy left_right(): turning is always allowed and only resets the overlap baseline. */
    private fun resetOverlap() {
        lastOverlap = structureOverlap(positionX, positionY)
    }

    /**
     * Legacy static_collide(): running into a structure bounces the vehicle back with `crash` of
     * its speed, with a crash sound above 10 px/s.
     */
    private fun staticCollide() {
        velocityX *= -spec.motion.crash
        velocityY *= -spec.motion.crash
        if (speed > 10f) pendingEffects += GameEvent.Sound(kind = SoundKind.CRASH, x = positionX, y = positionY)
    }

    /**
     * Legacy new_position(): a vehicle with `general.crash` damages the structure it rams (crash
     * strength, `|v| / 25 + crash`), at most every 10 ticks.
     */
    private fun crashInto(tileX: Int, tileY: Int) {
        val surroundings = surroundings ?: return
        if (spec.crashDamage <= 0 || crashCooldownTicks > 0 || height >= 2) return
        if (surroundings.structures.strength(tileX, tileY, StructureLayer.SOLID, HitKind.CRASH) == 0) return
        val power = (speed / 25f).toInt() + spec.crashDamage
        val tileCentreX = tileX * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2
        val tileCentreY = tileY * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2
        pendingEffects += GameEvent.Effect(
            EffectKind.RUIN,
            (tileCentreX + positionX) / 2 - 4 + random.nextInt(8),
            (tileCentreY + positionY) / 2 - 4 + random.nextInt(8),
        )
        surroundings.damageStructure(tileX, tileY, StructureLayer.SOLID, HitKind.CRASH, power, by = this)
        crashCooldownTicks = CRASH_INTERVAL_TICKS
    }

    /**
     * Legacy tanks_collide(): this vehicle ran into [other]; the relative velocity is shared by
     * the mass ratio and this vehicle's `crash` factor. A heavy rammer (`general.crash > 4`) also
     * hits the other for 2 every 10 ticks. A wreck simply stops the vehicle.
     */
    private fun collideWithVehicle(other: Tank) {
        if (other.armor <= 0) {
            velocityX = 0f
            velocityY = 0f
            return
        }
        if (crashCooldownTicks == 0 && spec.crashDamage > 4) {
            other.queueDamage(RAM_DAMAGE)
            crashCooldownTicks = CRASH_INTERVAL_TICKS
        }
        val relativeX = velocityX - other.velocityX
        val relativeY = velocityY - other.velocityY
        val loss = spec.motion.crash
        val massRatio = spec.motion.mass / other.spec.motion.mass
        velocityX -= relativeX * (1 - loss) / massRatio
        velocityY -= relativeY * (1 - loss) / massRatio
        other.push(relativeX * loss * massRatio, relativeY * loss * massRatio)
    }

    internal fun push(dvx: Float, dvy: Float) {
        velocityX += dvx
        velocityY += dvy
    }

    private fun inRadius(dx: Int, dy: Int, radius: Int): Boolean = dx * dx + dy * dy < radius * radius

    private fun stepCooldowns() {
        primaryCooldownTicks = primaryCooldownTicks.afterStep()
        crashCooldownTicks = crashCooldownTicks.afterStep()
    }

    /**
     * Legacy tank_fire(): one refire timer for all weapons (`weap.speed[w]` ticks, for the computer
     * `computer.fire_delay`). Human tanks spend ammo and click when a weapon is empty; computer
     * tanks fire without ammo.
     */
    private fun stepFire() {
        stepCooldowns()
        if (!pendingIntent.firePrimary || armor <= 0 || primaryCooldownTicks > 0 || height < 1) return
        val weapon = currentWeapon
        if (onWater && height < 2 && !canFireFromWater(weapon)) return
        if (weapon == WEAPON_LIGHT) {
            // Legacy tank_fire(): the light switch needs no ammo.
            primaryCooldownTicks = LIGHT_SWITCH_TICKS
            pendingEffects += GameEvent.Sound(kind = SoundKind.LIGHT, x = positionX, y = positionY)
            lightOn = !lightOn
            return
        }
        if (!computer) {
            if (weapon == WEAPON_FLAME) {
                if (!spec.hasFlamer && ammo[WEAPON_FLAME] <= 0) return
                if (!cheats.loadsOfWeapons) {
                    if (fuel <= FLAME_FUEL_COST) return
                    fuel -= FLAME_FUEL_COST
                }
            } else {
                if (ammoFor(weapon) <= 0) {
                    pendingEffects += GameEvent.Sound(kind = SoundKind.EMPTY, x = positionX, y = positionY)
                    primaryCooldownTicks = EMPTY_WEAPON_DELAY_TICKS
                    return
                }
                if (!cheats.loadsOfWeapons) {
                    ammo[weapon] = (ammo[weapon] - if (weapon == WEAPON_CHAIN) CHAIN_ROUNDS_PER_BURST else 1).coerceAtLeast(0)
                }
            }
        }
        fireWeapon(weapon)
    }

    /**
     * Legacy control_enforcer() (src/objects/enforce.c): an enforcer selects [weapon] for this
     * computer tank and, when [fire], makes it fire that weapon at once with one round; unless
     * [keep] the tank switches back to its own weapon.
     */
    internal fun enforce(weapon: Int, fire: Boolean, keep: Boolean) {
        val previous = currentWeapon
        currentWeapon = weapon
        if (fire && armor > 0 && height >= 1 && !(onWater && height < 2 && !canFireFromWater(weapon))) {
            if (weapon in 0 until WEAPON_COUNT) ammo[weapon] = 1
            fireWeapon(weapon)
        }
        if (!keep) currentWeapon = previous
    }

    private fun fireWeapon(weapon: Int) {
        // Legacy tank_fire(): `weap.speed[w]` for players, `computer.fire_delay` for the computer.
        primaryCooldownTicks = if (computer) aiFireDelay else spec.refireTicks.getOrElse(weapon) { 50 }
        when (weapon) {
            WEAPON_MAIN -> fireMainCannon()
            WEAPON_CHAIN -> fireChainGun()
            WEAPON_FLAME -> fireFlamer()
            WEAPON_MINE -> deployMine()
            WEAPON_ROCKET, WEAPON_GUNNERS, WEAPON_FLAMERS -> fireRocket()
            WEAPON_MORTAR -> fireMortar()
            WEAPON_ABOMB -> pendingAbombRequest = AbombRequest(positionX, positionY, countdown = 3)
            WEAPON_SMOKE -> {
                pendingEffects += GameEvent.Sound(kind = SoundKind.SMOKESCR, x = positionX, y = positionY)
                smokeScreenLeft += SMOKE_SCREEN_CLOUDS
            }
            WEAPON_CLOAK -> {
                pendingEffects += GameEvent.Sound(kind = SoundKind.INVISIBLE, x = positionX, y = positionY)
                cloakCount = 2
                cloakCountdownTicks = CLOAK_FIRST_TICKS
                cloakFlashCooldownTicks = 0
            }
            WEAPON_SPEEDUP -> {
                pendingEffects += GameEvent.Sound(kind = SoundKind.SPEEDUP, x = positionX, y = positionY)
                boostTicks = BOOST_TICKS
            }
        }
    }

    /**
     * Legacy tank_fire(): in the water a hull-gun vehicle can only cloak or speed up, and a
     * turret vehicle can also fire its main gun, flamer, rockets and mortar.
     */
    private fun canFireFromWater(weapon: Int): Boolean = when (turretMode) {
        TurretMode.TURRET -> weapon in setOf(WEAPON_MAIN, WEAPON_FLAME, WEAPON_ROCKET, WEAPON_MORTAR, WEAPON_CLOAK, WEAPON_SPEEDUP)
        else -> weapon == WEAPON_CLOAK || weapon == WEAPON_SPEEDUP
    }

    /** A point on the hull: [offset] (north-facing, from the centre) turned with the hull. */
    private fun onHull(offset: Pair<Int, Int>): Pair<Int, Int> {
        val (x, y) = rotate(bodyDirection, offset)
        return positionX + x to positionY + y
    }

    /** A point on the turret, which sits at `turret.loc` on the hull and points along [aimDirection]. */
    private fun onTurret(offset: Pair<Int, Int>): Pair<Int, Int> {
        val (tx, ty) = rotate(bodyDirection, spec.turretOffset)
        val (x, y) = rotate(aimDirection, offset)
        return positionX + tx + x to positionY + ty + y
    }

    /** Legacy tank_fire(): the main gun, flamer and mortar fire from the main-gun barrels in turn. */
    private fun nextMainBarrel(): Pair<Int, Int> {
        val guns = spec.mainGuns.ifEmpty { listOf(0 to -12) }
        val barrel = onTurret(guns[activeGun % guns.size])
        activeGun = (activeGun + 1) % guns.size
        return barrel
    }

    /**
     * Legacy tank_fire() case 5: a mortar shell, which kicks the vehicle back (impulse 50), or
     * from a plane a bomb that falls 10 px forward per unit of height.
     */
    private fun fireMortar() {
        val (fx, fy) = LegacyDirections.unitVector(aimDirection)
        val (ox, oy) = nextMainBarrel()
        if (kind == VehicleKind.PLANE) {
            val fallPx = PLANE_BOMB_PX_PER_HEIGHT * height
            pendingMortarRequest = MortarRequest(
                originX = ox,
                originY = oy,
                velocityX = fx * MORTAR_SPEED,
                velocityY = fy * MORTAR_SPEED,
                damage = MORTAR_DAMAGE,
                maxRadius = MORTAR_MAX_RADIUS_PX,
                travelTicks = (fallPx / MORTAR_SPEED).roundToInt().coerceAtLeast(1),
                dropHeight = height,
            )
            return
        }
        addImpulse(-fx, -fy, MORTAR_RECOIL_POWER)
        pendingMortarRequest = MortarRequest(
            originX = ox,
            originY = oy,
            velocityX = fx * MORTAR_SPEED,
            velocityY = fy * MORTAR_SPEED,
            damage = MORTAR_DAMAGE,
            maxRadius = MORTAR_MAX_RADIUS_PX,
        )
        pendingEffects += GameEvent.Effect(EffectKind.MORTAR_FIRE, ox, oy)
    }

    /** Legacy tank_add_impulse(): a push of `2000 * power / mass` px/s along ([dirX], [dirY]). */
    internal fun addImpulse(dirX: Float, dirY: Float, power: Int) {
        val impulse = IMPULSE_FACTOR * power / spec.motion.mass
        velocityX += dirX * impulse
        velocityY += dirY * impulse
    }

    /**
     * Legacy tank_fire() case 4: a human player gets a lock-on marker at the tank, steered with
     * the drive keys until fire is released (see [stepRocketLock]); a computer tank launches
     * straight at its target.
     */
    private fun fireRocket() {
        if (computer) {
            // Legacy tank_fire(): a computer tank fires straight at its target.
            fireLocked(if (aiTargetX >= 0) aiTargetX else positionX, if (aiTargetY >= 0) aiTargetY else positionY)
            return
        }
        lockActive = true
        lockX = positionX
        lockY = positionY
        lockVelocityX = 0
        lockVelocityY = 0
        lockStepCooldownTicks = 0
        lockCameraTicks = LOCK_CAMERA_HOLD_TICKS
    }

    /**
     * Legacy tank_fire_locked(): what a released marker launches — a rocket, or a soldier who
     * jumps out 20 px towards the marker and walks there.
     */
    private fun fireLocked(targetX: Int, targetY: Int) {
        when (currentWeapon) {
            WEAPON_GUNNERS, WEAPON_FLAMERS -> {
                val direction = LegacyDirections.fromAngle(targetX - positionX, targetY - positionY)
                val (dx, dy) = rotate(direction, SOLDIER_DROP_OFFSET)
                pendingSoldierRequests += SoldierRequest(
                    x = positionX + dx,
                    y = positionY + dy,
                    targetX = targetX,
                    targetY = targetY,
                    gunner = currentWeapon == WEAPON_GUNNERS,
                    power = powerFor(currentWeapon),
                )
            }
            else -> launchRocket(targetX, targetY)
        }
    }

    /** Legacy tank_marker_control(). */
    private fun stepRocketLock() {
        lockCameraTicks = LOCK_CAMERA_HOLD_TICKS
        if (!pendingIntent.firePrimary) {
            lockActive = false
            fireLocked(lockX, lockY)
            return
        }
        lockStepCooldownTicks = lockStepCooldownTicks.afterStep()
        if (lockStepCooldownTicks > 0) return
        lockVelocityY = markerVelocity(lockVelocityY, negative = pendingIntent.forward, positive = pendingIntent.reverse)
        lockVelocityX = markerVelocity(lockVelocityX, negative = pendingIntent.turnLeft, positive = pendingIntent.turnRight)
        val nextX = (lockX + lockVelocityX).coerceIn(0, lockMaxX)
        val nextY = (lockY + lockVelocityY).coerceIn(0, lockMaxY)
        val dx = (nextX - positionX).toLong()
        val dy = (nextY - positionY).toLong()
        if (dualMode || cheats.noLockLimit || dx * dx + dy * dy < LOCK_MAX_RANGE_PX.toLong() * LOCK_MAX_RANGE_PX) {
            lockX = nextX
            lockY = nextY
        } else {
            lockVelocityX = 0
            lockVelocityY = 0
        }
        lockStepCooldownTicks = LOCK_STEP_TICKS
    }

    /** One axis of the legacy marker: jump to 5 px per step in the pressed direction, then speed up to 20. */
    private fun markerVelocity(current: Int, negative: Boolean, positive: Boolean): Int = when {
        negative -> if (current >= 0) -LOCK_START_SPEED else (current - 1).coerceAtLeast(-LOCK_MAX_SPEED)
        positive -> if (current <= 0) LOCK_START_SPEED else (current + 1).coerceAtMost(LOCK_MAX_SPEED)
        else -> 0
    }

    private fun launchRocket(targetX: Int, targetY: Int) {
        val (ox, oy) = onTurret(spec.rocketOffset)
        pendingRocketRequest = RocketRequest(
            originX = ox,
            originY = oy,
            targetX = targetX,
            targetY = targetY,
            damage = powerFor(WEAPON_ROCKET),
        )
    }

    private fun deployMine() {
        pendingMineRequest = MineRequest(
            originX = positionX,
            originY = positionY,
            damage = powerFor(WEAPON_MINE),
            radius = LIGHT_MINE_RADIUS_PX,
        )
    }

    /**
     * Legacy tank_fire() case 0: a shell from the next main-gun barrel, or for vehicles whose
     * main gun is HT_SROCK a small rocket that only starts hitting things 30 px out.
     */
    private fun fireMainCannon() {
        val range = if (dualMode) MAIN_GUN_DUAL_RANGE_PX else MAIN_GUN_RANGE_PX
        val (ox, oy) = nextMainBarrel()
        if (spec.mainGunHitType == "HT_SROCK") {
            pendingFireRequests += bulletRequest(ox, oy, aimDirection, range, powerFor(WEAPON_MAIN), SHOT_SMALL_ROCKET)
                .copy(safetyPx = SROCK_SAFETY_PX)
            return
        }
        pendingFireRequests += bulletRequest(ox, oy, aimDirection, range, powerFor(WEAPON_MAIN), WEAPON_MAIN)
        pendingEffects += GameEvent.Effect(EffectKind.FIRE, ox, oy)
    }

    private fun fireChainGun() {
        // Legacy tank_fire() case 1: every hull-mounted chain gun fires one round per burst.
        // The extra-gun bonus adds a gun at the next slot of `chain_gun[]`, which is unused (0, 0)
        // in every vehicle of the table.
        val guns = spec.chainGuns.ifEmpty { listOf(0 to -12) }
        for (gun in if (extraGun && guns.size < 4) guns + (0 to 0) else guns) {
            val (ox, oy) = onHull(gun)
            pendingFireRequests += bulletRequest(ox, oy, bodyDirection, CHAIN_GUN_RANGE_PX, powerFor(WEAPON_CHAIN), WEAPON_CHAIN)
            pendingEffects += GameEvent.Effect(EffectKind.SFIRE, ox, oy)
        }
    }

    /** Legacy HT_FLAME: a slow flame that ignores the tank's own speed. */
    private fun fireFlamer() {
        val (ox, oy) = nextMainBarrel()
        pendingFireRequests += FireRequest(
            originX = ox,
            originY = oy,
            direction = aimDirection,
            speed = FLAME_SPEED * TICK_SECONDS,
            rangePx = FLAME_RANGE_PX,
            damage = powerFor(WEAPON_FLAME),
            weaponKind = WEAPON_FLAME,
        )
    }

    /**
     * Legacy create_bullet(): 200 px/s plus the tank's own velocity (but at least 75 px/s), with
     * the range scaled by the same factor.
     */
    private fun bulletRequest(ox: Int, oy: Int, direction: Int, range: Int, damage: Int, weapon: Int): FireRequest {
        val (fx, fy) = LegacyDirections.unitVector(direction)
        val bulletSpeed = hypot(velocityX + fx * BULLET_SPEED, velocityY + fy * BULLET_SPEED).coerceAtLeast(MIN_BULLET_SPEED)
        return FireRequest(
            originX = ox,
            originY = oy,
            direction = direction,
            speed = bulletSpeed * TICK_SECONDS,
            rangePx = (range * bulletSpeed / BULLET_SPEED).roundToInt(),
            damage = damage,
            weaponKind = weapon,
        )
    }

    /** Legacy shield_effects(): the start shield blinks away and the shield key draws on its pool. */
    private fun stepShields() {
        if (shieldBlinks != 0) {
            shieldBlinkTicks = shieldBlinkTicks.afterStep()
            if (shieldBlinkTicks == 0) {
                shieldBlinks -= 1
                shieldBlinkTicks = SHIELD_BLINK_TICKS
            }
        }
        shieldActive = pendingIntent.shield && shield > 0
        shield = if (pendingIntent.shield) {
            (shield - SHIELD_DRAIN_PER_TICK * STEP_TICKS).coerceAtLeast(0)
        } else {
            (shield + SHIELD_RECHARGE_PER_TICK * STEP_TICKS).coerceAtMost(SHIELD_MAX_POWER)
        }
    }

    /**
     * Legacy new_velocity(): gravity pulls the tank down at 100 px/s² (`vz -= dt * 100`); at the
     * pit's bottom (`h == -4`) it is destroyed (HT_DESTROY). A falling tank cannot drive or fire.
     */
    private fun stepFall() {
        velocityX = 0f
        velocityY = 0f
        verticalSpeed -= PIT_GRAVITY * STEP_SECONDS
        exactHeight = (exactHeight + verticalSpeed * STEP_SECONDS).coerceAtLeast(PIT_BOTTOM_HEIGHT)
        if (exactHeight <= PIT_BOTTOM_HEIGHT) queueDestroy()
    }

    /**
     * Legacy new_velocity(), the vertical part: gravity pulls everything that is off the ground
     * down (planes gently, 10/s², the rest at 100/s²); a plane that moves gains lift from its
     * speed (`vz += lift * vx * dt`) and is destroyed when it touches anything but a runway; a
     * helicopter simply hovers at its height. Flying without fuel is fatal.
     */
    private fun stepAltitude() {
        val flies = kind == VehicleKind.CHOPPER || kind == VehicleKind.PLANE
        if (!flies && exactHeight == 1f && verticalSpeed == 0f) return
        val highest = if (spec.height > 1) spec.height.toFloat() else MAX_FLIGHT_HEIGHT
        exactHeight += verticalSpeed * STEP_SECONDS
        if (exactHeight < 1f) {
            exactHeight = 1f
            verticalSpeed = 0f
        }
        if (exactHeight > highest) {
            exactHeight = highest
            verticalSpeed = 0f
        }
        if (height > 1) verticalSpeed -= STEP_SECONDS * if (kind == VehicleKind.PLANE) PLANE_GRAVITY else PIT_GRAVITY
        if (armor > 0 && fuel > 0) {
            when (kind) {
                VehicleKind.PLANE -> if (speed > 5f) {
                    val (fx, fy) = LegacyDirections.unitVector(bodyDirection)
                    verticalSpeed += spec.motion.lift * (velocityX * fx + velocityY * fy) * STEP_SECONDS
                    if (height <= 1 && !onRunway) queueDestroy()
                }
                VehicleKind.CHOPPER -> verticalSpeed = 0f
                else -> Unit
            }
        }
        if (armor > 0 && fuel <= 0 && height > 1) queueDestroy()
    }

    /** What the vehicle is on, set by the match before every tick (legacy get_underground()). */
    internal fun setUnderground(runway: Boolean, water: Boolean) {
        onRunway = runway
        onWater = water
    }

    /** Legacy invisible_effects(). */
    private fun stepCloak() {
        if (cloakCount == 0) {
            cloakFlash = true
            return
        }
        cloakCountdownTicks = cloakCountdownTicks.afterStep()
        if (cloakCountdownTicks == 0) {
            cloakCount -= 1
            cloakCountdownTicks = CLOAK_LAST_TICKS
        }
        cloakFlashCooldownTicks = cloakFlashCooldownTicks.afterStep()
        if (cloakFlashCooldownTicks == 0) {
            cloakFlash = !cloakFlash
            cloakFlashCooldownTicks = when {
                cloakCount == 2 && cloakFlash -> 100
                cloakCount == 2 -> 10
                else -> 5
            }
        }
    }

    /** Legacy tank_effects(): a smoke screen puffs clouds behind the hull, one every 10..20 ticks. */
    private fun stepSmokeScreen() {
        smokeScreenCooldownTicks = smokeScreenCooldownTicks.afterStep()
        if (smokeScreenLeft <= 0 || smokeScreenCooldownTicks > 0) return
        val (x, y) = onHull(SMOKE_SCREEN_POINTS[random.nextInt(SMOKE_SCREEN_POINTS.size)])
        pendingEffects += GameEvent.Effect(EffectKind.SMOKE_SCREEN, x, y)
        smokeScreenCooldownTicks = 10 + random.nextInt(10)
        smokeScreenLeft -= 1
    }

    /** Legacy tank_effects(): moving vehicles that smoke puff more often once badly damaged. */
    private fun stepSmoke() {
        smokeCooldownTicks = smokeCooldownTicks.afterStep()
        if (smokeCooldownTicks > 0) return
        // Legacy tank_effects(): no smoke on water (`und.nw != WATER`).
        if (!spec.smokes || onWater || speed < 1f || spec.smokePoints.isEmpty()) return
        val (sx, sy) = onHull(spec.smokePoints[random.nextInt(spec.smokePoints.size)])
        pendingEffects += GameEvent.Effect(EffectKind.SMOKE, sx, sy)
        smokeCooldownTicks = (if (armor <= maxArmor / 3) 5 else 30) + random.nextInt(10)
    }

    /** Legacy control_tank()/control_wreck(): explosions while dying, then occasional smoke from the wreck. */
    private fun stepWreck() {
        // A tank that fell into a pit lies at the bottom without burning.
        if (height < 1) return
        if (wreckGone) return
        if (dyingTicks > 0) {
            // Legacy control_tank(): an explosion on every step until `contr.disable` runs out.
            dyingTicks = dyingTicks.afterStep()
            pendingEffects += GameEvent.Effect(
                EffectKind.EXPLOSION,
                positionX - 8 + random.nextInt(16),
                positionY - 8 + random.nextInt(16),
            )
            return
        }
        if (wreckLifeTicks < 0) {
            // Legacy control_tank(): once on the ground and done exploding, it is a wreck.
            if (!airborne) wreckLifeTicks = WRECK_LIFE_TICKS
            return
        }
        wreckLifeTicks = wreckLifeTicks.afterStep()
        if (wreckLifeTicks == 0) wreckBlowUp = true
        smokeCooldownTicks = smokeCooldownTicks.afterStep()
        if (smokeCooldownTicks > 0) return
        pendingEffects += GameEvent.Effect(EffectKind.ROOK, positionX - 4 + random.nextInt(8), positionY - 4 + random.nextInt(8))
        smokeCooldownTicks = WRECK_SMOKE_MIN_TICKS + random.nextInt(WRECK_SMOKE_SPREAD_TICKS)
    }

    /**
     * Legacy weapon_left()/weapon_right(): step to the next weapon with ammo, the flamethrower
     * counting as available when the vehicle has one.
     */
    private fun stepWeaponCycle() {
        weaponCycleCooldownTicks = weaponCycleCooldownTicks.afterStep()
        if (weaponCycleCooldownTicks > 0) return
        val step = when {
            pendingIntent.cycleWeaponRight && !pendingIntent.cycleWeaponLeft -> 1
            pendingIntent.cycleWeaponLeft && !pendingIntent.cycleWeaponRight -> -1
            else -> return
        }
        // Legacy weapon_left()/right(): night maps add the light switch as a 13th slot.
        val slots = if (night) WEAPON_COUNT + 1 else WEAPON_COUNT
        var weapon = currentWeapon
        repeat(slots) {
            weapon = (weapon + step).mod(slots)
            if (isAvailable(weapon)) {
                currentWeapon = weapon
                weaponCycleCooldownTicks = WEAPON_CYCLE_COOLDOWN_TICKS
                showMessage(WEAPON_NAMES[weapon], "SELECTED!")
                return
            }
        }
    }

    private fun isAvailable(weapon: Int): Boolean =
        ammoFor(weapon) != 0 || (weapon == WEAPON_FLAME && spec.hasFlamer) || (weapon == WEAPON_LIGHT && night)

    /** Pushes this tank out of an overlap; returns true when they overlapped. */
    private fun resolveAabbVsAabb(
        otherCx: Int,
        otherCy: Int,
        otherHalfW: Int,
        otherHalfH: Int,
        selfFraction: Float,
    ): Boolean {
        val dx = positionX - otherCx
        val dy = positionY - otherCy
        val combinedHalfW = SERVER_TANK_HALF + otherHalfW
        val combinedHalfH = SERVER_TANK_HALF + otherHalfH
        val overlapX = combinedHalfW - abs(dx)
        val overlapY = combinedHalfH - abs(dy)
        if (overlapX <= 0 || overlapY <= 0) return false
        if (overlapX < overlapY) {
            val sign = if (dx >= 0) 1 else -1
            val push = (overlapX * selfFraction).toInt().coerceAtLeast(1) * sign
            pendingResolveX += push
            velocityX = if (sign > 0) velocityX.coerceAtLeast(0f) else velocityX.coerceAtMost(0f)
        } else {
            val sign = if (dy >= 0) 1 else -1
            val push = (overlapY * selfFraction).toInt().coerceAtLeast(1) * sign
            pendingResolveY += push
            velocityY = if (sign > 0) velocityY.coerceAtLeast(0f) else velocityY.coerceAtMost(0f)
        }
        return true
    }

    private fun syncBodyFromPosition() {
        val topLeft = ScenePoint(
            (positionX - SERVER_TANK_HALF).toFloat(),
            (positionY - SERVER_TANK_HALF).toFloat(),
        )
        body.position = topLeft
    }

    @kotlinx.serialization.Serializable
    data class State(
        @SerialName("body") val body: BoxBody = BoxBody(),
        @SerialName("bodyDirection") val bodyDirection: Int = 0,
        @SerialName("turretDirection") val turretDirection: Int = 0,
        @SerialName("playerIndex") val playerIndex: Int = -1,
        @SerialName("controllerId") val controllerId: Int = -1,
        @SerialName("ref") val ref: Int = 0,
        @SerialName("computer") val computer: Boolean = true,
        @SerialName("goalContribution") val goalContribution: Int = 0,
        @SerialName("startPadType") val startPadType: Int = -1,
        /** Legacy `pl.weap.nw` of a start pad with its own loadout; null: the vehicle's defaults. */
        @SerialName("padAmmo") val padAmmo: List<Int>? = null,
        @SerialName("aiAttackTanks") val aiAttackTanks: Int = 100,
        @SerialName("aiActivationRadius") val aiActivationRadius: Int = -1,
        @SerialName("aiActivator") val aiActivator: Int = 0,
        @SerialName("aiStayActive") val aiStayActive: Boolean = false,
        @SerialName("aiActive") val aiActive: Boolean = false,
        /** -1: the vehicle's default (make_tank()). */
        @SerialName("aiAttackAt") val aiAttackAt: Int = -1,
        @SerialName("aiAttackGoals") val aiAttackGoals: Int = 60,
        /** -1: the vehicle table's `computer.wave_delay`. */
        @SerialName("aiWaveDelay") val aiWaveDelay: Int = -1,
        @SerialName("aiFireDelay") val aiFireDelay: Int = -1,
        @SerialName("wayType") val wayType: Int = 0,
        @SerialName("wayCurrent") val wayCurrent: Int = 0,
        /** Ten x, y pairs (top-left corners, as the original stores them); x = -1 is an empty slot. */
        @SerialName("wayPoints") val wayPoints: List<Int> = emptyList(),
        @SerialName("tankType") val tankType: Int = 0,
        @SerialName("armor") val armor: Int = DEFAULT_TANK_ARMOR,
        @SerialName("shield") val shield: Int = SHIELD_START_POWER,
        @SerialName("invulnerableTicks") val invulnerableTicks: Int = 0,
        @SerialName("fuel") val fuel: Int = LEGACY_MAX_FUEL,
        @SerialName("lives") val lives: Int = 1,
        @SerialName("team") val team: Int = 0,
        @SerialName("velocityX") val velocityX: Float = 0f,
        @SerialName("velocityY") val velocityY: Float = 0f,
        @SerialName("primaryCooldownTicks") val primaryCooldownTicks: Int = 0,
        /** Legacy `weap.nw`; the default is the STANDARD tank's loadout from src/data/tanks.c. */
        @SerialName("ammo") val ammo: List<Int> = DEFAULT_AMMO,
        /** Legacy `weap.power`. */
        @SerialName("power") val power: List<Int> = DEFAULT_POWER,
        @SerialName("currentWeapon") val currentWeapon: Int = WEAPON_MAIN,
        @SerialName("respawnInTicks") val respawnInTicks: Int = 0,
        @SerialName("maxArmor") val maxArmor: Int = 0,
        @SerialName("maxFuel") val maxFuel: Int = 0,
        @SerialName("spawnX") val spawnX: Int = -1,
        @SerialName("spawnY") val spawnY: Int = -1,
        @SerialName("spawnBodyDirection") val spawnBodyDirection: Int = -1,
        @SerialName("spawnTurretDirection") val spawnTurretDirection: Int = -1,
    ) : SceneObject.State<Tank> {
        override fun restore(): Tank = Tank(this)
        override fun serialize(): String = Json.encodeToString(this)
    }

    companion object {
        /** Legacy `way.point[10]`. */
        const val WAYPOINTS = 10
        /** Legacy STANDARD tank `props.armor`. */
        const val DEFAULT_TANK_ARMOR: Int = 30
        /** Legacy `tank[21]`, the ATOMIC TANK. */
        const val ATOMIC_TANK: Int = 21
        val DEFAULT_AMMO: List<Int> = listOf(30, 1500, 0, 7, 3, 3, 2, 5, 5, 1, 1, 1)
        val DEFAULT_POWER: List<Int> = listOf(10, 1, 1, 7, 10, 0, 0, 1, 1, 0, 0, 0)
    }
}

private fun List<Int>.toWeaponArray(): IntArray = IntArray(WEAPON_COUNT) { getOrElse(it) { 0 } }

private fun decay(value: Float, amount: Float): Float = when {
    value > 0f -> (value - amount).coerceAtLeast(0f)
    value < 0f -> (value + amount).coerceAtMost(0f)
    else -> 0f
}

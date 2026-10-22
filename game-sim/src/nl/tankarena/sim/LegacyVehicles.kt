package nl.tankarena.sim

import nl.tankarena.core.DetMath
/**
 * Legacy `tankinfotype.turret.main_static` (original/src/include/types.h): how a vehicle's main gun aims.
 *
 * Only [TURRET] guns follow the aim keys. [STATIC] guns are fixed to the hull, and [ROTATE] is a
 * purely visual auto-spinning top (a helicopter rotor): both fire along the hull
 * (`tank_fire()` in original/src/tanks/fire.c uses `motion.dir` unless the gun is `MAIN_TURRET`).
 */
enum class TurretMode { STATIC, TURRET, ROTATE }

/** Legacy `tankinfotype.type`. */
enum class VehicleKind { CAR, TANK, CHOPPER, PLANE }

/**
 * Legacy `tankinfotype.motion`, used by `new_velocity()` in original/src/tanks/control.c. Forces are in
 * pixel-mass units per second: thrust `acc / mass` px/s², longitudinal friction
 * `(ffV * |v| + ff0) / mass` and sideways friction `(fsV * |v| + fs0) / mass`.
 * [rotation] is the delay in ticks between two 1/16 turns; [change] the pixels driven between
 * two track animation frames.
 */
data class VehicleMotion(
    val mass: Float,
    val acc: Float,
    val ff0: Float,
    val ffV: Float,
    val fs0: Float,
    val fsV: Float,
    val crash: Float,
    val lift: Float,
    val rotation: Int,
    val change: Int,
)

/** One row of `tank[]` in original/src/data/tanks.c. Weapon lists are indexed by legacy weapon number (0..11). */
data class VehicleSpec(
    val name: String,
    val kind: VehicleKind,
    val motion: VehicleMotion,
    val turretOffset: Pair<Int, Int>,
    val turretMode: TurretMode,
    val autoRotation: Int,
    val height: Int,
    val radius: Int,
    val armor: Int,
    val maxFuel: Int,
    val crashDamage: Int,
    val smokes: Boolean,
    val mainGunHitType: String,
    /** Legacy `weap.flame`: the flamethrower is always available and burns fuel instead of ammo. */
    val hasFlamer: Boolean,
    val initialWeapon: Int,
    /** Default ammo per weapon (`weap.num`); maps usually override it per tank. */
    val ammo: List<Int>,
    /** Ticks between two shots per weapon (`weap.speed`). */
    val refireTicks: List<Int>,
    /** Default hit power per weapon (`weap.power`); maps store their own per tank. */
    val power: List<Int>,
    val waveDelay: Int,
    /**
     * Offsets of the main-gun barrels (`main_gun[]`), fired in turn; like every offset here they are
     * relative to the vehicle centre for a north-facing vehicle and turn with it (see [rotate]).
     * The main gun sits on the turret, which is at [turretOffset] on the hull.
     */
    val mainGuns: List<Pair<Int, Int>> = listOf(0 to -12),
    /** Hull-mounted chain guns (`chain_gun[]`); a burst fires one round from each. */
    val chainGuns: List<Pair<Int, Int>> = listOf(0 to -12),
    /** Exhaust points (`smoke[]`) that puff smoke while the vehicle moves. */
    val smokePoints: List<Pair<Int, Int>> = emptyList(),
    /** Rocket launcher (`rocket[]`), on the turret. */
    val rocketOffset: Pair<Int, Int> = 0 to 0,
    /** Headlights (`light[]`) for the night maps, on the turret of a turret vehicle. */
    val lights: List<VehicleLight> = emptyList(),
)

/** One legacy headlight: its cone size (0 large, 1 medium, 2 small) and where it sits. */
data class VehicleLight(val size: Int, val offset: Pair<Int, Int>)

/**
 * Legacy loc_x()/loc_y() (original/src/check/math.c): turn an offset laid out for a north-facing vehicle
 * to [direction] (16 steps, clockwise), rounding like the original.
 */
fun rotate(direction: Int, offset: Pair<Int, Int>): Pair<Int, Int> {
    val angle = Math.toRadians(direction.mod(16) * 22.5)
    val (x, y) = offset
    return Math.round(DetMath.cos(angle) * x - DetMath.sin(angle) * y).toInt() to
        Math.round(DetMath.sin(angle) * x + DetMath.cos(angle) * y).toInt()
}

/** Per-vehicle data from `tank[]` in original/src/data/tanks.c, indexed by the legacy vehicle number that scenes store as `tankType`. */
object LegacyVehicles {
    fun spec(tankType: Int): VehicleSpec =
        GeneratedLegacyVehicles.vehicles.getOrElse(tankType) { GeneratedLegacyVehicles.vehicles[0] }

    fun turretMode(tankType: Int): TurretMode = spec(tankType).turretMode

    /**
     * Legacy prepare_tank(): start with the vehicle's initial weapon, or else the first weapon
     * with ammo, or else the flamethrower if the vehicle has one.
     */
    fun startingWeapon(spec: VehicleSpec, ammo: List<Int>): Int {
        val initial = spec.initialWeapon
        if (initial == WEAPON_FLAME || ammo.getOrElse(initial) { 0 } != 0) return initial
        val loaded = (0 until WEAPON_COUNT).firstOrNull { ammo.getOrElse(it) { 0 } != 0 }
        return loaded ?: if (spec.hasFlamer) WEAPON_FLAME else WEAPON_MAIN
    }
}

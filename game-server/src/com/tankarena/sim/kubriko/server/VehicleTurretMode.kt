package com.tankarena.sim.kubriko.server

/**
 * Legacy `tankinfotype.turret.main_static` (src/include/types.h): how a vehicle's main gun aims.
 *
 * Only [TURRET] guns follow the aim keys. [STATIC] guns are fixed to the hull, and [ROTATE] is a
 * purely visual auto-spinning top (a helicopter rotor): both fire along the hull
 * (`tank_fire()` in src/tanks/fire.c uses `motion.dir` unless the gun is `MAIN_TURRET`).
 */
enum class TurretMode { STATIC, TURRET, ROTATE }

/**
 * Per-vehicle data from `tank[]` in src/data/tanks.c, indexed by the legacy vehicle number
 * that the scenes store as `tankType`. This is the start of the full vehicle table (B7).
 */
internal object LegacyVehicles {
    private val TURRET_MODES: List<TurretMode> = listOf(
        TurretMode.TURRET, // 0 STANDARD
        TurretMode.STATIC, // 1 DEVASTATOR
        TurretMode.STATIC, // 2 MOUSE
        TurretMode.TURRET, // 3 CROCODILE
        TurretMode.STATIC, // 4 THUNDERBOLT
        TurretMode.ROTATE, // 5 CHOPPER
        TurretMode.STATIC, // 6 RACE TANK
        TurretMode.STATIC, // 7 LOSERS TANK
        TurretMode.TURRET, // 8 LIZZARD
        TurretMode.STATIC, // 9 COMPUTER DRONE
        TurretMode.TURRET, // 10 LAUNCHER
        TurretMode.TURRET, // 11 SIDE-ARM
        TurretMode.STATIC, // 12 GREEN PLANE
        TurretMode.STATIC, // 13 JET
        TurretMode.STATIC, // 14 CAR
        TurretMode.STATIC, // 15 BOB'S BUS
        TurretMode.ROTATE, // 16 BATTLE CHOPPER
        TurretMode.TURRET, // 17 BUG LAUNCHER
        TurretMode.STATIC, // 18 TRUCK
        TurretMode.STATIC, // 19 FLAMER
        TurretMode.STATIC, // 20 BULLDOZER
        TurretMode.STATIC, // 21 ATOMIC TANK
        TurretMode.TURRET, // 22 SHOPPING CAR
    )

    fun turretMode(tankType: Int): TurretMode = TURRET_MODES.getOrElse(tankType) { TurretMode.TURRET }
}

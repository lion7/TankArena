package nl.tankarena.sim

/**
 * Legacy select_tanks() (original/src/game/seltank.c): the eight vehicles player-vs-player rounds choose
 * from, and which of them a map allows. Used by the selection screen and by network games, which
 * have no selection yet.
 */
object DualVehicles {
    /** The eight vehicles, four across and two down; the last is the losers tank. */
    const val CHOICES = 8

    /** Legacy `MAPC_*` (original/src/include/define.h). */
    private const val MAPC_TANK = 1
    private const val MAPC_CHOP = 2
    private const val MAPC_PLANE = 4
    private const val MAPC_RACE = 8

    /**
     * Which of the eight vehicles a map allows. [losersTank] is the game option (legacy `lose`):
     * the losers tank is there for a player 1 or 3 rounds behind.
     */
    fun allowed(mapContents: Int, wins: List<Int>, losersTank: Int = 0): List<Boolean> = List(CHOICES) { tank ->
        when (tank) {
            in 0..5 -> when (LegacyVehicles.spec(tank).kind) {
                VehicleKind.TANK -> mapContents and MAPC_TANK != 0
                VehicleKind.CHOPPER -> mapContents and MAPC_CHOP != 0
                VehicleKind.PLANE -> mapContents and MAPC_PLANE != 0
                else -> false
            }
            6 -> mapContents and MAPC_RACE != 0
            // The losers tank: only with the option on and one player far enough behind.
            else -> mapContents and MAPC_TANK != 0 && losersTank > 0 && kotlin.math.abs(wins[0] - wins[1]) >= losersTank * 2 - 1
        }
    }

    /** A vehicle the map does not allow is swapped for the first one it does. */
    fun autoSelect(chosen: List<Int>, allowed: List<Boolean>): List<Int> = chosen.map { tank ->
        if (tank in 0 until CHOICES && allowed[tank]) tank else (0 until 7).firstOrNull { allowed[it] } ?: tank
    }

    /** A round where nobody chose: both sides drive the map's first allowed vehicle. */
    fun defaultSetup(mapContents: Int, raceLaps: Int = DualSetup.DEFAULT_RACE_LAPS): DualSetup =
        DualSetup(tankTypes = autoSelect(listOf(0, 0), allowed(mapContents, listOf(0, 0))), raceLaps = raceLaps)
}

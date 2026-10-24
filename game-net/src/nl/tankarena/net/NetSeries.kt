package nl.tankarena.net

import nl.tankarena.sim.DualSetup
import nl.tankarena.sim.DualVehicles
import nl.tankarena.sim.MatchMode
import nl.tankarena.sim.Ruleset
import nl.tankarena.sim.map.MapDocument

/**
 * The rounds of a network game, kept by the host (its screens or app-server). Against each other it
 * goes as legacy dual_game() does: before every round the players pick their vehicles, and the
 * match is won by winning more than half of [rounds] ([ENDLESS_ROUNDS]: never). Together against the
 * computer there is one round, the mission.
 */
class NetSeries(
    private val server: GameServer,
    val baseName: String,
    val map: MapDocument,
    val mode: MatchMode,
    private val rounds: Int = 3,
    private val raceLaps: Int = DualSetup.DEFAULT_RACE_LAPS,
    /** Legacy `lose`: 0 off, 1 the losers tank one round behind, 2 three rounds behind. */
    private val losersTank: Int = 0,
    /** The name recordings get; null not recorded. */
    private val recordAs: String? = null,
    /** The rounds each side won on the maps the same players played before this one. */
    private val seriesBefore: List<Int> = listOf(0, 0),
    /**
     * The vehicle each player drove last, by player id: a player who does not pick keeps it, and
     * the choice starts on it. Shared by the maps of one game.
     */
    private val lastVehicles: MutableMap<Int, Int> = HashMap(),
) {
    val versus: Boolean get() = mode == MatchMode.DUAL

    /** The round being chosen for or played, from 1. */
    var round: Int = 1
        private set

    /** Rounds won per side. */
    var wins: List<Int> = listOf(0, 0)
        private set

    /** The rounds each side has won over every map, this one's included: the losers tank goes by these. */
    val seriesWins: List<Int> get() = wins.zip(seriesBefore) { a, b -> a + b }

    /** The vehicle player [seat] drove last, or null. */
    fun lastVehicle(seat: Int): Int? = lastVehicles[seat]

    private var choiceDeadline = 0L
    private var allowed: List<Boolean> = emptyList()

    /** Legacy `map_contents`: which vehicles the map has room for. */
    private val mapContents: Int get() = map.header.contents

    /** Against each other: the vehicle choice for the next round opens, for [CHOICE_SECONDS]. */
    fun openChoice(now: Long = System.currentTimeMillis()) {
        allowed = DualVehicles.allowed(mapContents, seriesWins, losersTank)
        choiceDeadline = now + CHOICE_SECONDS * 1_000L
        server.openChoice(round, wins, allowed, CHOICE_SECONDS, seriesWins, lastVehicles.toMap())
    }

    /** The vehicles the round being chosen for allows. */
    val allowedVehicles: List<Boolean> get() = allowed

    /** Seconds left to pick. */
    fun secondsLeft(now: Long = System.currentTimeMillis()): Int = ((choiceDeadline - now + 999) / 1_000).toInt().coerceAtLeast(0)

    /** Everyone in the game has picked, or the time is up. */
    fun choiceDone(now: Long = System.currentTimeMillis()): Boolean =
        now >= choiceDeadline || server.choices.value.keys.containsAll(server.seatsInGame())

    /** Starts the round; every player who did not pick drives what they drove before, or the first allowed. */
    fun startRound(): HostedMatch {
        val players = server.closeLobby()
        val dual = if (versus) {
            val picked = List(players) { seat -> server.choices.value[seat] ?: lastVehicles[seat] ?: 0 }
            val vehicles = DualVehicles.autoSelect(picked, allowed.ifEmpty { DualVehicles.allowed(mapContents, seriesWins, losersTank) })
            vehicles.forEachIndexed { seat, vehicle -> lastVehicles[seat] = vehicle }
            DualSetup(tankTypes = vehicles, raceLaps = raceLaps, round = round, wins = wins)
        } else {
            DualSetup()
        }
        return server.startMatch(baseName, map, mode, dual, Ruleset.EXTENDED, recordAs)
    }

    /**
     * The round ended: against each other [winner] is the side that won (null: nobody, the round
     * was left), together [missionWon] how the mission went. Tells everyone; returns the result.
     */
    fun roundEnded(winner: Int?, missionWon: Boolean?): ServerMessage.RoundOver {
        if (winner != null) wins = wins.mapIndexed { side, n -> if (side == winner) n + 1 else n }
        val over = !versus || winner == null || (rounds != ENDLESS_ROUNDS && wins.any { it > rounds / 2 })
        val result = ServerMessage.RoundOver(round, winner, wins, missionWon, over, seriesWins)
        server.series = seriesWins
        server.roundOver(result)
        round++
        return result
    }

    companion object {
        /** Legacy `rnds = '~'`: the match goes on until the players stop. */
        const val ENDLESS_ROUNDS = 0

        /** How long the players have to pick their vehicles. */
        const val CHOICE_SECONDS = 30
    }
}

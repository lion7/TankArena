package nl.tankarena.app

import nl.tankarena.net.GameClient
import nl.tankarena.net.GameServer
import nl.tankarena.net.NetSeries
import nl.tankarena.net.RelayHost
import nl.tankarena.net.ServerMessage
import nl.tankarena.sim.VersusScores
import nl.tankarena.ui.compose.GameMode
import nl.tankarena.ui.compose.menu.MissionEntry

/** A network game this machine is in (ADR 0024), from the lobby on. */
sealed interface NetworkSession {
    /** This machine's players. */
    val localPlayers: Int

    /** Leaves the game: the host stops it for everyone, a guest goes. */
    fun close()

    /**
     * This machine hosts [mission] in [mode]; others join at [port]. Back in the lobby the host may
     * pick another map, and the same players play on.
     */
    class Hosting(
        val server: GameServer,
        val port: Int,
        var mission: MissionEntry,
        var mode: GameMode,
        override val localPlayers: Int,
    ) : NetworkSession {
        /** The rounds on the current map, once the host started them. */
        var series: NetSeries? = null

        /** Against each other: the record of this game's series, per map and over every map. */
        val scores = VersusScores()

        /** The rounds each side has won over every map of this game; the losers tank goes by these. */
        val versusSeries: List<Int> get() = scores.rounds

        /** The current map's match has been counted (decided, or left after a round). */
        private var matchCounted = false

        /** The vehicle each player drove last, by player id, over every map of this game. */
        val lastVehicles: MutableMap<Int, Int> = HashMap()

        /**
         * Back to the lobby with everyone still connected: the current map's rounds count for the
         * series, and the next start begins a new match.
         */
        fun backToLobby() {
            // A match left after a round counts as played.
            series?.let { if (it.versus && !matchCounted && it.round > 1) scores.matchEnded(mission.baseName, null) }
            series = null
            publishScores()
            server.reopenLobby()
        }

        /** A new match on the current map begins. */
        fun matchStarted(series: NetSeries) {
            this.series = series
            matchCounted = false
        }

        /** Against each other: a round on the current map ended as [result] says. */
        fun roundPlayed(result: ServerMessage.RoundOver) {
            val winner = result.winner ?: return
            scores.round(mission.baseName, winner)
            if (result.matchOver) {
                scores.matchEnded(mission.baseName, result.wins.indices.maxBy { result.wins[it] })
                matchCounted = true
            }
        }

        private fun publishScores() {
            server.series = versusSeries
            server.mapScore = scores.of(mission.baseName)
        }

        /** The host picked another map (or way of playing) in the lobby. */
        fun changeMap(mission: MissionEntry, mode: GameMode) {
            // The series is against each other; another way of playing starts a new one.
            if (mode != this.mode) scores.clear()
            this.mission = mission
            this.mode = mode
            server.series = versusSeries
            server.mapScore = scores.of(mission.baseName)
            server.mapName = mission.baseName
        }

        /** The game on the internet relay, when one is set (ADR 0028). */
        var relay: RelayHost? = null

        override fun close() {
            relay?.stop()
            server.stop()
        }
    }

    /** This machine joined the game at [address]. */
    class Joined(val client: GameClient, val address: String, override val localPlayers: Int) : NetworkSession {
        override fun close() = client.close()
    }
}

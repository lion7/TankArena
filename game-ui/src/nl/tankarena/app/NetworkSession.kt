package nl.tankarena.app

import nl.tankarena.net.GameClient
import nl.tankarena.net.GameServer
import nl.tankarena.net.NetSeries
import nl.tankarena.net.RelayHost
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

        /** Against each other: the rounds each side has won over every map of this game. */
        var versusSeries: List<Int> = listOf(0, 0)

        /** The vehicle each player drove last, by player id, over every map of this game. */
        val lastVehicles: MutableMap<Int, Int> = HashMap()

        /**
         * Back to the lobby with everyone still connected: the current map's rounds count for the
         * series, and the next start begins a new match.
         */
        fun backToLobby() {
            series?.let { if (it.versus) versusSeries = it.seriesWins }
            series = null
            server.series = versusSeries
            server.reopenLobby()
        }

        /** The host picked another map (or way of playing) in the lobby. */
        fun changeMap(mission: MissionEntry, mode: GameMode) {
            // The series is against each other; another way of playing starts a new one.
            if (mode != this.mode) versusSeries = listOf(0, 0)
            this.mission = mission
            this.mode = mode
            server.series = versusSeries
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

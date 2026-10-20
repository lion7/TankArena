package nl.tankarena.app

import nl.tankarena.net.GameClient
import nl.tankarena.net.GameServer
import nl.tankarena.ui.compose.GameMode
import nl.tankarena.ui.compose.menu.MissionEntry

/** A network game this machine is in (ADR 0024), from the lobby on. */
sealed interface NetworkSession {
    /** This machine's players. */
    val localPlayers: Int

    /** Leaves the game: the host stops it for everyone, a guest goes. */
    fun close()

    /** This machine hosts [mission] in [mode]; others join at [port]. */
    class Hosting(
        val server: GameServer,
        val port: Int,
        val mission: MissionEntry,
        val mode: GameMode,
        override val localPlayers: Int,
    ) : NetworkSession {
        override fun close() = server.stop()
    }

    /** This machine joined the game at [address]. */
    class Joined(val client: GameClient, val address: String, override val localPlayers: Int) : NetworkSession {
        override fun close() = client.close()
    }
}

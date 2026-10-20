package nl.tankarena.net

import kotlinx.coroutines.runBlocking
import nl.tankarena.protocol.InputFrame
import nl.tankarena.protocol.snapshot.ServerFrame
import nl.tankarena.protocol.snapshot.TankState
import nl.tankarena.sim.EXTENDED_RULES_VERSION
import nl.tankarena.sim.MatchMode
import nl.tankarena.sim.Ruleset
import nl.tankarena.sim.map.ShippedMaps
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** A host and a joining machine over a real WebSocket on this machine. */
class NetworkGameTest {
    private val servers = mutableListOf<GameServer>()
    private val clients = mutableListOf<GameClient>()

    @AfterTest
    fun cleanUp() {
        clients.forEach { it.close() }
        servers.forEach { it.stop() }
    }

    private fun server(hostPlayers: Int = 1): Pair<GameServer, Int> {
        val server = GameServer("host", hostPlayers, EXTENDED_RULES_VERSION, port = 0).also { servers += it }
        return server to server.start()
    }

    private fun client(players: Int = 1, rules: Int = EXTENDED_RULES_VERSION) =
        GameClient("guest", players, rules).also { clients += it }

    private fun waitFor(what: String, condition: () -> Boolean) {
        val until = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < until) { "timed out waiting for $what" }
            Thread.sleep(5)
        }
    }

    @Test
    fun `a guest joins, the match starts, and the guest drives its own tank`() {
        val (server, port) = server()
        val guest = client()
        assertTrue(runBlocking { guest.join("127.0.0.1", port) })
        val lobby = assertIs<ClientState.InLobby>(guest.state.value)
        assertEquals(listOf(1), lobby.seats)
        waitFor("the lobby") { server.members.value.size == 2 }
        waitFor("the guest's lobby") { (guest.state.value as? ClientState.InLobby)?.members?.size == 2 }

        val map = ShippedMaps.loadAll().first { it.baseName == "flagisle" }.document
        val hosted = server.startMatch("flagisle", map, MatchMode.DUAL, ruleset = Ruleset.EXTENDED)
        waitFor("the start") { guest.state.value is ClientState.Playing }
        val remote = (guest.state.value as ClientState.Playing).match
        assertEquals(listOf(1), remote.seats)
        assertEquals("flagisle", remote.baseName)
        assertEquals(map.header, remote.map.header)

        val start = remote.currentServerFrame()
        val tankId = start.playerViews.single().controlledActorId!!
        fun ServerFrame.guestTank() = world.actors.filterIsInstance<TankState>().single { it.actorId == tankId }
        val startTank = start.guestTank()

        var lastShown = start
        var events = 0
        repeat(600) { tick ->
            hosted.submitInput(InputFrame(playerId = 0, inputSequence = tick.toLong()))
            hosted.tick()
            remote.submitInput(InputFrame(playerId = 1, inputSequence = tick.toLong(), forward = true, turnLeft = tick < 60))
            lastShown = remote.tick()
            events += lastShown.world.events.size
            Thread.sleep(2)
        }
        val moved = lastShown.guestTank()
        assertNotEquals(startTank.x to startTank.y, moved.x to moved.y, "the guest's tank drove")
        assertEquals(listOf(1), lastShown.playerViews.map { it.playerId }, "the guest only gets its own view")
        assertTrue(events > 0, "events reach the guest")
        // The host's own player did not move.
        val hostTankId = hosted.currentServerFrame().playerViews.first { it.playerId == 0 }.controlledActorId!!
        assertEquals(
            start.world.actors.filterIsInstance<TankState>().single { it.actorId == hostTankId }.let { it.x to it.y },
            hosted.currentServerFrame().world.actors.filterIsInstance<TankState>().single { it.actorId == hostTankId }.let { it.x to it.y },
        )

        hosted.dispose()
        waitFor("the end") { guest.state.value is ClientState.Out }
        assertEquals("The host left the game.", remote.endedReason)
    }

    @Test
    fun `a machine with other rules is refused`() {
        val (_, port) = server()
        val guest = client(rules = EXTENDED_RULES_VERSION + 1)
        assertFalse(runBlocking { guest.join("127.0.0.1", port) })
        val out = assertIs<ClientState.Out>(guest.state.value)
        assertTrue("other rules" in out.reason, out.reason)
    }

    @Test
    fun `a full game refuses more players`() {
        val (server, port) = server(hostPlayers = 3)
        val guest = client(players = 2)
        assertFalse(runBlocking { guest.join("127.0.0.1", port) })
        assertEquals("The game is full.", (guest.state.value as ClientState.Out).reason)
        assertEquals(1, server.members.value.size)
    }

    @Test
    fun `messages survive the wire`() {
        val hello = ClientMessage.Hello(NET_VERSION, 9, 6, "me", 2)
        assertEquals(hello, NetCodec.decodeClient(NetCodec.encode(hello)))
        val lobby = ServerMessage.Lobby(listOf(LobbyMember("host", listOf(0)), LobbyMember("me", listOf(1, 2))))
        assertEquals(lobby, NetCodec.decodeServer(NetCodec.encode(lobby)))
    }
}

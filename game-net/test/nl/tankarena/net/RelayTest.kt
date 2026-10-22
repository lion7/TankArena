package nl.tankarena.net

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import kotlinx.coroutines.runBlocking
import nl.tankarena.protocol.InputFrame
import nl.tankarena.protocol.snapshot.PROTOCOL_VERSION
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A relay, a host behind it and a guest joining by code, all on this machine. */
class RelayTest {
    private val relay = RelayServer(port = 0, tunnelTimeoutMillis = 2_000)
    private val relayPort = relay.start()
    private val relayAddress = "127.0.0.1:$relayPort"
    private val closers = mutableListOf<() -> Unit>()

    @AfterTest
    fun cleanUp() {
        closers.reversed().forEach { it() }
        relay.stop()
    }

    private fun waitFor(what: String, condition: () -> Boolean) {
        val until = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < until) { "timed out waiting for $what" }
            Thread.sleep(5)
        }
    }

    private fun host(public: Boolean): Pair<GameServer, RelayHost> {
        val server = GameServer("host behind a router", 1, EXTENDED_RULES_VERSION, port = 0, announceTo = null)
        server.mapName = "flagisle"
        server.start()
        val published = RelayHost(server, relayAddress, public).also { it.start() }
        closers += { published.stop(); server.stop() }
        waitFor("the code") { published.code.value != null }
        return server to published
    }

    private fun guest(): GameClient = GameClient("guest", 1, EXTENDED_RULES_VERSION).also { closers += it::close }

    @Test
    fun `a guest joins by code through the relay and plays`() {
        val (server, published) = host(public = false)
        val code = published.code.value!!
        assertEquals(1, relay.gameCount)
        val guest = guest()
        assertTrue(runBlocking { guest.joinByCode(relayAddress, GameCodes.show(code).lowercase()) })
        waitFor("the lobby") { server.members.value.size == 2 }

        val map = ShippedMaps.loadAll().first { it.baseName == "flagisle" }.document
        val hosted = server.startMatch("flagisle", map, MatchMode.DUAL, ruleset = Ruleset.EXTENDED)
        waitFor("the start") { guest.state.value is ClientState.Playing }
        val remote = (guest.state.value as ClientState.Playing).match
        val tankId = remote.currentServerFrame().playerViews.single().controlledActorId!!
        fun position() = remote.currentServerFrame().world.actors.filterIsInstance<TankState>().single { it.actorId == tankId }.let { it.x to it.y }
        val start = position()
        repeat(400) { tick ->
            hosted.tick()
            remote.submitInput(InputFrame(playerId = 1, inputSequence = tick.toLong(), forward = true))
            remote.tick()
            Thread.sleep(2)
        }
        assertNotEquals(start, position(), "the guest's tank drove, through the relay")
    }

    @Test
    fun `public games are listed, private ones are not`() {
        host(public = true)
        val (_, private) = host(public = false)
        val listed = runBlocking { RelayDirectory.games(relayAddress, PROTOCOL_VERSION, EXTENDED_RULES_VERSION) }
        assertEquals(1, listed.size)
        assertEquals("host behind a router", listed.single().name)
        assertEquals("flagisle", listed.single().map)
        assertTrue(listed.none { it.code == private.code.value })
        assertTrue(runBlocking { RelayDirectory.games(relayAddress, PROTOCOL_VERSION, EXTENDED_RULES_VERSION + 1) }.isEmpty())
    }

    @Test
    fun `a wrong code is refused, and guessing is limited`() {
        host(public = false)
        repeat(5) {
            val guest = guest()
            assertFalse(runBlocking { guest.joinByCode(relayAddress, "AAA-AAA") })
            assertTrue("no game with the code AAA-AAA" in (guest.state.value as ClientState.Out).reason, (guest.state.value as ClientState.Out).reason)
        }
        val sixth = guest()
        assertFalse(runBlocking { sixth.joinByCode(relayAddress, "AAA-AAA") })
        assertEquals("Too many wrong codes; wait a minute.", assertIs<ClientState.Out>(sixth.state.value).reason)
    }

    @Test
    fun `behind a proxy, guessing is limited per forwarded address`() {
        val proxied = RelayServer(port = 0, behindProxy = true)
        val port = proxied.start()
        val http = HttpClient { install(WebSockets) }
        try {
            /** What the relay answers a guess of a code that does not exist, coming from [address]. */
            fun guess(address: String): String = runBlocking {
                var reason = ""
                http.webSocket("ws://127.0.0.1:$port${RelayPaths.JOIN}AAAAAA", request = { header("X-Forwarded-For", address) }) {
                    val refused = NetCodec.decodeServer((incoming.receive() as Frame.Binary).readBytes())
                    reason = assertIs<ServerMessage.Refused>(refused).reason
                }
                reason
            }
            repeat(5) { guess("203.0.113.1") }
            assertEquals("Too many wrong codes; wait a minute.", guess("203.0.113.1"))
            assertTrue("no game with the code" in guess("203.0.113.2"))
        } finally {
            http.close()
            proxied.stop()
        }
    }

    @Test
    fun `codes and relay addresses are read as typed`() {
        assertEquals("K7Q4MX", GameCodes.normalize(" k7q-4mx "))
        assertNull(GameCodes.normalize("K7Q-2M0"), "0 is never in a code")
        assertNull(GameCodes.normalize("192.168.1.2"))
        assertEquals("K7Q-4MX", GameCodes.show("K7Q4MX"))
        assertEquals("ws://relay.example.org:$RELAY_PORT", relayUrl("relay.example.org"))
        assertEquals("ws://relay.example.org:9000", relayUrl("relay.example.org:9000"))
        assertEquals("wss://relay.example.org", relayUrl("wss://relay.example.org/"))
    }
}

package nl.tankarena.net

import io.ktor.http.ContentType
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.origin
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketDeflateExtension
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.ListSerializer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

/**
 * The relay and lobby (ADR 0028): hosts publish their games over a control connection and get a
 * code; a guest joining by code gets a tunnel that the host opens to the relay, and the relay copies
 * the game's messages both ways without reading them. Public games are listed at [RelayPaths.GAMES].
 * Everything is in memory.
 */
class RelayServer(
    private val port: Int = RELAY_PORT,
    private val tunnelTimeoutMillis: Long = 10_000,
    private val log: (String) -> Unit = {},
) {
    private class Hosted(val control: DefaultWebSocketSession, @Volatile var game: RelayGame, @Volatile var public: Boolean) {
        @Volatile
        var open = true
        val tunnels = AtomicInteger()
    }

    private class Pending(val code: String) {
        val hostEnd = CompletableDeferred<DefaultWebSocketSession>()
        val done = CompletableDeferred<Unit>()
    }

    private val games = ConcurrentHashMap<String, Hosted>()
    private val pending = ConcurrentHashMap<String, Pending>()
    private val wrongGuesses = ConcurrentHashMap<String, ArrayDeque<Long>>()
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    /** Starts listening; returns the port (useful when [port] is 0, any free one). */
    fun start(): Int {
        val engine = embeddedServer(CIO, port = port) {
            install(WebSockets) {
                pingPeriod = 5.seconds
                timeout = 15.seconds
                maxFrameSize = Long.MAX_VALUE
                extensions { install(WebSocketDeflateExtension) }
            }
            routing {
                get(RelayPaths.GAMES) {
                    val listed = games.values.filter { it.public && it.open }.map { it.game }
                    call.respondText(relayJson.encodeToString(ListSerializer(RelayGame.serializer()), listed), ContentType.Application.Json)
                }
                webSocket(RelayPaths.HOST) { host(this) }
                webSocket("${RelayPaths.JOIN}{code}") { join(this, call.parameters["code"].orEmpty(), call.request.origin.remoteHost) }
                webSocket("${RelayPaths.TUNNEL}{id}") { tunnel(this, call.parameters["id"].orEmpty()) }
            }
        }
        engine.start(wait = false)
        server = engine
        return runBlocking { engine.engine.resolvedConnectors().first().port }
    }

    fun stop() {
        for (hosted in games.values) runCatching { runBlocking { hosted.control.sendRelay(RelayMessage.Closed("The relay is shutting down.")) } }
        server?.stop(gracePeriodMillis = 200, timeoutMillis = 1_000)
        server = null
    }

    /** Games published now (for logs and tests). */
    val gameCount: Int get() = games.size

    private suspend fun host(control: DefaultWebSocketSession) {
        val register = (control.incoming.receiveCatching().getOrNull() as? Frame.Text)
            ?.let { runCatching { relayJson.decodeFromString(RelayMessage.serializer(), it.readText()) }.getOrNull() } as? RelayMessage.Register
        if (register == null) {
            control.close()
            return
        }
        var code = GameCodes.new()
        while (games.putIfAbsent(code, Hosted(control, register.game.copy(code = code), register.public)) != null) code = GameCodes.new()
        val hosted = games.getValue(code)
        control.sendRelay(RelayMessage.Registered(code))
        log("Game ${GameCodes.show(code)} published by ${register.game.name} (${register.game.map}, ${if (register.public) "public" else "private"})")
        try {
            for (frame in control.incoming) {
                if (frame !is Frame.Text) continue
                when (val message = runCatching { relayJson.decodeFromString(RelayMessage.serializer(), frame.readText()) }.getOrNull()) {
                    is RelayMessage.Update -> {
                        hosted.game = hosted.game.copy(players = message.players)
                        hosted.public = message.public
                        hosted.open = message.open
                    }
                    else -> Unit
                }
            }
        } finally {
            games.remove(code)
            log("Game ${GameCodes.show(code)} ended")
        }
    }

    private suspend fun join(guest: DefaultWebSocketSession, typed: String, from: String) {
        if (tooManyGuesses(from)) return guest.refuse("Too many wrong codes; wait a minute.")
        val code = GameCodes.normalize(typed)
        val hosted = code?.let { games[it] }
        if (code == null || hosted == null) {
            wrongGuesses.computeIfAbsent(from) { ArrayDeque() }.let { synchronized(it) { it.addLast(System.currentTimeMillis()) } }
            return guest.refuse("There is no game with the code ${GameCodes.show(typed.uppercase())}.")
        }
        if (hosted.tunnels.incrementAndGet() > MAX_NET_PLAYERS - 1) {
            hosted.tunnels.decrementAndGet()
            return guest.refuse("The game is full.")
        }
        val id = UUID.randomUUID().toString()
        val tunnel = Pending(code)
        pending[id] = tunnel
        try {
            hosted.control.sendRelay(RelayMessage.Incoming(id))
            val hostEnd = withTimeoutOrNull(tunnelTimeoutMillis) { tunnel.hostEnd.await() }
            if (hostEnd == null) return guest.refuse("The host did not answer.")
            log("A player joined game ${GameCodes.show(code)}")
            pipe(guest, hostEnd)
        } finally {
            pending.remove(id)
            tunnel.done.complete(Unit)
            hosted.tunnels.decrementAndGet()
        }
    }

    /** The host's end of a tunnel: handed to the waiting guest, kept open until the guest's side ends. */
    private suspend fun tunnel(hostEnd: DefaultWebSocketSession, id: String) {
        val tunnel = pending[id] ?: return hostEnd.close()
        tunnel.hostEnd.complete(hostEnd)
        tunnel.done.await()
    }

    /** Copies the game's messages both ways until either side closes. */
    private suspend fun pipe(guest: DefaultWebSocketSession, host: DefaultWebSocketSession) = coroutineScope {
        val toHost = launch {
            for (frame in guest.incoming) if (frame is Frame.Binary) host.send(Frame.Binary(true, frame.readBytes()))
        }
        val toGuest = launch {
            for (frame in host.incoming) if (frame is Frame.Binary) guest.send(Frame.Binary(true, frame.readBytes()))
        }
        // Whichever side ends first ends the tunnel.
        kotlinx.coroutines.selects.select {
            toHost.onJoin {}
            toGuest.onJoin {}
        }
        toHost.cancel()
        toGuest.cancel()
        runCatching { guest.close() }
        runCatching { host.close() }
    }

    private fun tooManyGuesses(from: String): Boolean {
        val times = wrongGuesses[from] ?: return false
        val now = System.currentTimeMillis()
        return synchronized(times) {
            while (times.isNotEmpty() && now - times.first() > GUESS_WINDOW_MILLIS) times.removeFirst()
            times.size >= MAX_WRONG_GUESSES
        }
    }

    /** Tells a guest why not, as a game would ([ServerMessage.Refused]), and closes. */
    private suspend fun DefaultWebSocketSession.refuse(reason: String) {
        runCatching {
            send(Frame.Binary(true, NetCodec.encode(ServerMessage.Refused(reason))))
            // Out before the close, or the guest only sees the connection go.
            flush()
        }
        close()
    }

    private companion object {
        const val MAX_WRONG_GUESSES = 5
        const val GUESS_WINDOW_MILLIS = 60_000L
    }
}

internal suspend fun DefaultWebSocketSession.sendRelay(message: RelayMessage) =
    send(Frame.Text(relayJson.encodeToString(RelayMessage.serializer(), message)))

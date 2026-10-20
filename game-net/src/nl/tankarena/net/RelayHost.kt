package nl.tankarena.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketDeflateExtension
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * A hosted game published on a relay (ADR 0028): it keeps the control connection, shows the game's
 * [code] once the relay gave one, and for every guest that joins by code opens a tunnel to the
 * relay and serves the guest on it like any other ([GameServer.accept]).
 */
class RelayHost(private val server: GameServer, relay: String, public: Boolean = false) {
    private val base = relayUrl(relay)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = HttpClient(CIO) {
        install(WebSockets) {
            pingIntervalMillis = 5_000
            maxFrameSize = Long.MAX_VALUE
            extensions { install(WebSocketDeflateExtension) }
        }
    }
    private val codeFlow = MutableStateFlow<String?>(null)
    private val errorFlow = MutableStateFlow<String?>(null)

    /** The game's code on the relay, once it has one. */
    val code: StateFlow<String?> = codeFlow.asStateFlow()

    /** Why the game is not (or no longer) on the relay. */
    val error: StateFlow<String?> = errorFlow.asStateFlow()

    /** Listed in the relay's public list, or only reached by its code. */
    @Volatile
    var public: Boolean = public

    fun start() {
        scope.launch {
            try {
                http.webSocket(urlString = base + RelayPaths.HOST) {
                    val game = server.relayGame() ?: return@webSocket
                    sendRelay(RelayMessage.Register(game, public))
                    // The lobby's state, whenever it changes (checked twice a second).
                    launch {
                        var last: RelayMessage.Update? = null
                        while (true) {
                            val now = RelayMessage.Update(server.playerCount(), public, server.lobbyOpen)
                            if (now != last) sendRelay(now)
                            last = now
                            delay(UPDATE_MILLIS)
                        }
                    }
                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        when (val message = runCatching { relayJson.decodeFromString(RelayMessage.serializer(), frame.readText()) }.getOrNull()) {
                            is RelayMessage.Registered -> codeFlow.value = message.code
                            is RelayMessage.Incoming -> scope.launch { openTunnel(message.tunnel) }
                            is RelayMessage.Closed -> errorFlow.value = message.reason
                            else -> Unit
                        }
                    }
                }
                if (errorFlow.value == null) errorFlow.value = "The relay closed the connection."
            } catch (e: Exception) {
                errorFlow.value = "Cannot reach the relay at $base: ${e.message ?: e::class.simpleName}"
            }
            codeFlow.value = null
        }
    }

    private suspend fun openTunnel(id: String) {
        runCatching {
            http.webSocket(urlString = base + RelayPaths.TUNNEL + id) { server.accept(this) }
        }
    }

    fun stop() {
        scope.cancel()
        http.close()
    }

    private companion object {
        const val UPDATE_MILLIS = 500L
    }
}

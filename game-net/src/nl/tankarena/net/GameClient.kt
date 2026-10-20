package nl.tankarena.net

import io.ktor.websocket.WebSocketDeflateExtension
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import nl.tankarena.protocol.snapshot.PROTOCOL_VERSION
import kotlin.time.Duration.Companion.seconds

/** Where a joining machine is: in the lobby, in the match, or out. */
sealed interface ClientState {
    data object Connecting : ClientState

    /** In the lobby: [seats] are this machine's players' ids for now. */
    data class InLobby(val hostName: String, val seats: List<Int>, val members: List<LobbyMember>) : ClientState

    /** The match runs; show [match]. */
    data class Playing(val match: RemoteMatchClient) : ClientState

    /** Not in the game (any more): refused, the host left, or the connection broke; [reason] for the player. */
    data class Out(val reason: String) : ClientState
}

/**
 * A machine joining a game on another one (ADR 0024): it connects, waits in the lobby, and once the
 * host starts, shows the match through [RemoteMatchClient]. [state] says where it is.
 */
class GameClient(
    private val name: String,
    private val localPlayers: Int,
    private val rulesVersion: Int,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = HttpClient(CIO) {
        install(WebSockets) {
            pingIntervalMillis = 5_000
            maxFrameSize = Long.MAX_VALUE
            extensions { install(WebSocketDeflateExtension) }
        }
    }
    private val toServer = Channel<ClientMessage>(Channel.UNLIMITED)
    private val stateFlow = MutableStateFlow<ClientState>(ClientState.Connecting)

    /** The frames rebuilt from whole frames and deltas, as they arrive. */
    private val frames = DeltaDecoder()

    val state: StateFlow<ClientState> = stateFlow.asStateFlow()

    /**
     * Connects to the game at [host] and waits for the answer: true when in the lobby, false when
     * refused or unreachable ([state] then says why).
     */
    suspend fun join(host: String, port: Int = DEFAULT_PORT): Boolean {
        val answered = CompletableDeferred<Boolean>()
        scope.launch {
            try {
                http.webSocket(host = host, port = port, path = GAME_PATH) {
                    send(Frame.Binary(true, NetCodec.encode(ClientMessage.Hello(NET_VERSION, PROTOCOL_VERSION, rulesVersion, name, localPlayers))))
                    val sender = launch {
                        for (message in toServer) send(Frame.Binary(true, NetCodec.encode(message)))
                    }
                    for (frame in incoming) {
                        if (frame !is Frame.Binary) continue
                        val message = runCatching { NetCodec.decodeServer(frame.readBytes()) }.getOrNull() ?: continue
                        handle(message, answered)
                        if (stateFlow.value is ClientState.Out) break
                    }
                    sender.cancel()
                }
                lost("The connection to the host was closed.")
            } catch (e: Exception) {
                lost("Could not reach the game at $host: ${e.message ?: e::class.simpleName}")
            }
            answered.complete(false)
        }
        return withTimeoutOrNull(CONNECT_TIMEOUT) { answered.await() } ?: run {
            lost("No answer from $host.")
            close()
            false
        }
    }

    /** Leaves the game (lobby or match) and closes the connection. */
    fun close() {
        toServer.trySend(ClientMessage.Leave)
        toServer.close()
        scope.launch {
            kotlinx.coroutines.delay(200)
            http.close()
            scope.cancel()
        }
    }

    internal fun send(message: ClientMessage) {
        toServer.trySend(message)
    }

    private fun handle(message: ServerMessage, answered: CompletableDeferred<Boolean>) {
        val current = stateFlow.value
        when (message) {
            is ServerMessage.Welcome -> {
                stateFlow.value = ClientState.InLobby(message.hostName, message.seats, emptyList())
                answered.complete(true)
            }
            is ServerMessage.Refused -> {
                stateFlow.value = ClientState.Out(message.reason)
                answered.complete(false)
            }
            is ServerMessage.Lobby -> if (current is ClientState.InLobby) stateFlow.value = current.copy(members = message.members)
            is ServerMessage.MatchStart -> stateFlow.value = ClientState.Playing(RemoteMatchClient(message, this))
            is ServerMessage.Frame -> (current as? ClientState.Playing)?.match?.receive(frames.whole(message.frame))
            is ServerMessage.Delta -> (current as? ClientState.Playing)?.match?.receive(frames.apply(message.delta))
            is ServerMessage.Ended -> {
                (current as? ClientState.Playing)?.match?.end(message.reason)
                stateFlow.value = ClientState.Out(message.reason)
            }
        }
    }

    private fun lost(reason: String) {
        val current = stateFlow.value
        if (current is ClientState.Out) return
        (current as? ClientState.Playing)?.match?.end(reason)
        stateFlow.value = ClientState.Out(reason)
    }

    private companion object {
        val CONNECT_TIMEOUT = 5.seconds
    }
}

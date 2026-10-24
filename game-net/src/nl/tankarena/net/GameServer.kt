package nl.tankarena.net

import io.ktor.websocket.WebSocketDeflateExtension
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import nl.tankarena.protocol.InputFrame
import nl.tankarena.protocol.snapshot.PROTOCOL_VERSION
import nl.tankarena.protocol.snapshot.ServerFrame
import nl.tankarena.sim.DualSetup
import nl.tankarena.sim.LocalMatchClient
import nl.tankarena.sim.MapScore
import nl.tankarena.sim.MatchMode
import nl.tankarena.sim.Ruleset
import nl.tankarena.sim.map.MapDocument
import nl.tankarena.sim.map.MapFile
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/**
 * A game other machines join over the network (ADR 0024): a WebSocket server with a lobby, and once
 * the host starts it, the match ([HostedMatch]) whose snapshots it sends. The host's own
 * [hostPlayers] play with player ids 0 and up; every machine that joins gets the next free ones.
 */
class GameServer(
    private val hostName: String,
    private val hostPlayers: Int,
    /** The rules version of the ruleset the host plays; joining machines must have the same. */
    private val rulesVersion: Int,
    private val port: Int = DEFAULT_PORT,
    /** Where the lobby announces itself for [LanFinder]s; null not at all. */
    private val announceTo: List<java.net.InetSocketAddress>? = broadcastTargets(),
) {
    /** The map the lobby announces (a host picks it first, and may change it in the lobby). */
    @Volatile
    var mapName: String = ""
        set(value) {
            field = value
            if (server != null) publishMembers()
        }

    /** The rounds each side has won over every map of this game so far, shown in the lobby. */
    @Volatile
    var series: List<Int> = listOf(0, 0)

    /** Against each other: the lobby's map's record in this game's series; null when not played. */
    @Volatile
    var mapScore: MapScore? = null

    private var announcer: LanAnnouncer? = null

    private class Session(val name: String, var seats: List<Int>, val socket: DefaultWebSocketSession) {
        /** Messages to send, in order; frames are dropped when a client falls more than this behind. */
        val outgoing = Channel<ServerMessage>(OUTGOING_CAPACITY)

        /** The frames as deltas of the one sent before; only the simulation thread uses it. */
        val frames = DeltaEncoder()
    }

    private val lock = Any()
    private val sessions = ArrayList<Session>()
    private var started = false
    private var match: HostedMatch? = null
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    /** The latest controls of every remote player, by player id; the match reads them every tick. */
    internal val remoteInputs = ConcurrentHashMap<Int, InputFrame>()

    private val membersFlow = MutableStateFlow(listOf(LobbyMember(hostName, (0 until hostPlayers).toList())))

    /** Who is in the game, the host first. */
    val members: StateFlow<List<LobbyMember>> = membersFlow.asStateFlow()

    private val choicesFlow = MutableStateFlow<Map<Int, Int>>(emptyMap())

    /** Against each other: the vehicles picked so far for the coming round, by player id. */
    val choices: StateFlow<Map<Int, Int>> = choicesFlow.asStateFlow()

    /** The vehicles the coming round allows. */
    @Volatile
    private var allowed: List<Boolean> = emptyList()

    /** The players' ids in the game now: the host's and every machine's still connected. */
    fun seatsInGame(): List<Int> = synchronized(lock) { (0 until hostPlayers).toList() + sessions.flatMap { it.seats } }

    /** Starts listening; returns the port (useful when [port] is 0, any free one). */
    fun start(): Int {
        val engine = embeddedServer(CIO, port = port) {
            install(WebSockets) {
                pingPeriod = 5.seconds
                timeout = 15.seconds
                maxFrameSize = Long.MAX_VALUE
                // Compressed with the context of the frames before: a step is then 60-500 bytes.
                extensions { install(WebSocketDeflateExtension) }
            }
            routing {
                webSocket(GAME_PATH) { serve(this) }
            }
        }
        engine.start(wait = false)
        server = engine
        val bound = runBlocking { engine.engine.resolvedConnectors().first().port }
        announceTo?.let { targets ->
            announcer = LanAnnouncer(targets) {
                val joined = synchronized(lock) { if (started) null else hostPlayers + sessions.sumOf { it.seats.size } }
                joined?.let { LanBeacon(NET_VERSION, PROTOCOL_VERSION, rulesVersion, hostName, bound, mapName, it, MAX_NET_PLAYERS) }
            }.also { it.start() }
        }
        return bound
    }

    /** Ends the game for everyone and stops listening. */
    fun stop(reason: String = "The host stopped the game.") {
        val all = synchronized(lock) { sessions.toList().also { sessions.clear() } }
        for (session in all) {
            session.outgoing.trySend(ServerMessage.Ended(reason))
            session.outgoing.close()
        }
        announcer?.stop()
        announcer = null
        server?.stop(gracePeriodMillis = 200, timeoutMillis = 1_000)
        server = null
    }

    /**
     * Nobody can join any more; the players' ids are made consecutive (someone may have left), and
     * each machine learns its final ones with the next message. Returns the number of players.
     */
    fun closeLobby(): Int = synchronized(lock) {
        if (!started) {
            started = true
            var next = hostPlayers
            for (session in sessions) session.seats = List(session.seats.size) { next++ }
        }
        hostPlayers + sessions.sumOf { it.seats.size }
    }.also { publishMembers() }

    /**
     * The round, or the match, is over and the host goes back to the lobby with everyone still
     * connected: machines can join again, and the host may pick another map.
     */
    fun reopenLobby() {
        synchronized(lock) { started = false }
        for (seat in remoteInputs.keys) remoteInputs[seat] = InputFrame(playerId = seat, inputSequence = 0)
        publishMembers()
    }

    /**
     * Against each other: the vehicle choice for [round] begins; [choices] fills as players pick.
     * [series] are the rounds won over every map, [last] the vehicle each player drove last.
     */
    fun openChoice(
        round: Int,
        wins: List<Int>,
        allowed: List<Boolean>,
        seconds: Int,
        series: List<Int> = wins,
        last: Map<Int, Int> = emptyMap(),
    ) {
        closeLobby()
        this.allowed = allowed
        choicesFlow.value = emptyMap()
        val all = synchronized(lock) { sessions.toList() }
        for (session in all) {
            val lastOfSeats = session.seats.map { last[it] ?: -1 }
            session.outgoing.trySend(ServerMessage.ChooseVehicles(round, wins, allowed, seconds, session.seats, lastOfSeats, series))
        }
    }

    /** Player [seat] picks [vehicle] (the host's own players; the others' come in as messages). */
    fun choose(seat: Int, vehicle: Int) {
        if (allowed.getOrNull(vehicle) != true) return
        choicesFlow.value = choicesFlow.value + (seat to vehicle)
    }

    /** Tells everyone how the round went. */
    fun roundOver(result: ServerMessage.RoundOver) {
        val all = synchronized(lock) { sessions.toList() }
        for (session in all) session.outgoing.trySend(result)
    }

    /**
     * Starts a round on [map] with everyone in the game (the lobby closes with the first). A server
     * plays as many rounds as its host wants, one after the other.
     */
    fun startMatch(
        baseName: String,
        map: MapDocument,
        mode: MatchMode,
        dual: DualSetup = DualSetup(),
        ruleset: Ruleset = Ruleset.EXTENDED,
        recordAs: String? = null,
    ): HostedMatch {
        val players = closeLobby()
        check(match == null) { "a round is still being played" }
        val joined = synchronized(lock) { sessions.toList() }
        // The rounds' frames do not follow on from each other: every round begins with a whole one.
        for (session in joined) session.frames.reset()
        val local = LocalMatchClient.fromMap(map, mode, recordAs, dual, ruleset, players = players)
        val hosted = HostedMatch(local, this)
        match = hosted
        val first = local.currentServerFrame()
        val mapText = MapFile.write(map)
        for (session in joined) {
            session.outgoing.trySend(
                ServerMessage.MatchStart(baseName, mapText, mode, players, dual, ruleset, first.forSeats(session.seats)),
            )
        }
        return hosted
    }

    /** Sends one step of the match to every machine, each with only its own players' views. */
    internal fun broadcast(frame: ServerFrame) {
        val all = synchronized(lock) { sessions.toList() }
        for (session in all) {
            val message = session.frames.encode(frame.forSeats(session.seats))
            // A dropped frame breaks the chain of deltas: the next one is whole again.
            if (session.outgoing.trySend(message).isFailure) session.frames.reset()
        }
    }

    /** The round's match is over; the next may start. */
    internal fun matchEnded() {
        match = null
    }

    /**
     * Serves one machine on [socket], whoever opened it: a guest that connected here, or a tunnel to
     * a relay (ADR 0028). Returns when the machine has left.
     */
    suspend fun accept(socket: DefaultWebSocketSession) = serve(socket)

    /** The game as a relay lists it; null once the lobby has closed. */
    internal fun relayGame(): RelayGame? = synchronized(lock) {
        if (started) null else RelayGame(
            name = hostName, map = mapName, players = hostPlayers + sessions.sumOf { it.seats.size }, maxPlayers = MAX_NET_PLAYERS,
            netVersion = NET_VERSION, protocolVersion = PROTOCOL_VERSION, rulesVersion = rulesVersion,
        )
    }

    /** Players in the game now (the host's and every machine's). */
    internal fun playerCount(): Int = synchronized(lock) { hostPlayers + sessions.sumOf { it.seats.size } }

    /** Whether machines can still join. */
    internal val lobbyOpen: Boolean get() = synchronized(lock) { !started }

    private suspend fun serve(socket: DefaultWebSocketSession) {
        val hello = (socket.incoming.receiveCatching().getOrNull() as? Frame.Binary)
            ?.let { runCatching { NetCodec.decodeClient(it.readBytes()) }.getOrNull() } as? ClientMessage.Hello
        if (hello == null) {
            socket.close()
            return
        }
        val session = admit(hello, socket)
        if (session == null) return
        val sender = socket.launch {
            for (message in session.outgoing) socket.send(Frame.Binary(true, NetCodec.encode(message)))
            socket.close()
        }
        session.outgoing.trySend(ServerMessage.Welcome(session.seats, hostName))
        publishMembers()
        try {
            for (frame in socket.incoming) {
                if (frame !is Frame.Binary) continue
                when (val message = runCatching { NetCodec.decodeClient(frame.readBytes()) }.getOrNull()) {
                    is ClientMessage.Inputs -> {
                        val seats = session.seats
                        for (input in message.frames) if (input.playerId in seats) remoteInputs[input.playerId] = input
                    }
                    is ClientMessage.ChooseVehicle -> if (message.seat in session.seats) choose(message.seat, message.vehicle)
                    // Its vehicles stop for the rest of the round; it comes back with the next one.
                    ClientMessage.SitOut -> for (seat in session.seats) remoteInputs[seat] = InputFrame(playerId = seat, inputSequence = 0)
                    ClientMessage.Leave -> break
                    else -> Unit
                }
            }
        } catch (_: CancellationException) {
        } finally {
            leave(session)
            sender.cancel()
        }
    }

    /** Lets [hello]'s machine in with seats of its own, or tells it why not. */
    private suspend fun admit(hello: ClientMessage.Hello, socket: DefaultWebSocketSession): Session? {
        val refusal = when {
            hello.netVersion != NET_VERSION || hello.protocolVersion != PROTOCOL_VERSION ->
                "This game is a different version (network ${hello.netVersion}/$NET_VERSION, " +
                    "protocol ${hello.protocolVersion}/$PROTOCOL_VERSION)."
            hello.rulesVersion != rulesVersion -> "This game plays other rules (version ${hello.rulesVersion}/$rulesVersion)."
            hello.localPlayers !in 1..MAX_NET_PLAYERS -> "A machine brings 1 to $MAX_NET_PLAYERS players."
            else -> null
        }
        val session = if (refusal != null) {
            null
        } else {
            synchronized(lock) {
                when {
                    started -> null
                    else -> {
                        val taken = (0 until hostPlayers).toMutableSet().apply { sessions.forEach { addAll(it.seats) } }
                        val free = (0 until MAX_NET_PLAYERS).filter { it !in taken }
                        if (free.size < hello.localPlayers) {
                            null
                        } else {
                            Session(hello.name.take(MAX_NAME_LENGTH), free.take(hello.localPlayers), socket).also { sessions += it }
                        }
                    }
                }
            }
        }
        if (session == null) {
            val reason = refusal ?: if (started) "The game has already started." else "The game is full."
            socket.send(Frame.Binary(true, NetCodec.encode(ServerMessage.Refused(reason))))
            socket.flush()
            socket.close()
        }
        return session
    }

    private fun leave(session: Session) {
        val removed = synchronized(lock) { sessions.remove(session) }
        if (!removed) return
        // Its vehicles stop where they are instead of driving on with the last keys.
        for (seat in session.seats) remoteInputs[seat] = InputFrame(playerId = seat, inputSequence = 0)
        session.outgoing.close()
        publishMembers()
    }

    private fun publishMembers() {
        val (members, all, open) = synchronized(lock) {
            Triple(
                listOf(LobbyMember(hostName, (0 until hostPlayers).toList())) + sessions.map { LobbyMember(it.name, it.seats) },
                sessions.toList(),
                !started,
            )
        }
        membersFlow.value = members
        for (session in all) session.outgoing.trySend(ServerMessage.Lobby(members, mapName, series, open, mapScore))
    }

    private companion object {
        const val OUTGOING_CAPACITY = 64
        const val MAX_NAME_LENGTH = 20
    }
}

/** This frame as a machine with [seats] gets it: only its players' views. */
internal fun ServerFrame.forSeats(seats: List<Int>): ServerFrame = copy(playerViews = playerViews.filter { it.playerId in seats })

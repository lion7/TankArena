package nl.tankarena.net

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

/** The UDP port games announce themselves on (ADR 0024: LAN discovery by broadcast). */
const val DISCOVERY_PORT: Int = 47_301

/** What a game in its lobby says about itself, once a second. */
@Serializable
data class LanBeacon(
    val netVersion: Int,
    val protocolVersion: Int,
    val rulesVersion: Int,
    val name: String,
    /** The game's WebSocket port. */
    val port: Int,
    val map: String,
    val players: Int,
    val maxPlayers: Int,
)

/** A game heard on the network: where it is and what it said. */
data class LanGame(val address: String, val beacon: LanBeacon) {
    /** What to connect to. */
    val target: String get() = if (beacon.port == DEFAULT_PORT) address else "$address:${beacon.port}"
}

@OptIn(ExperimentalSerializationApi::class)
private val beaconCbor = Cbor { ignoreUnknownKeys = true }

/** Every network's broadcast address on this machine, and the general one. */
fun broadcastTargets(port: Int = DISCOVERY_PORT): List<InetSocketAddress> {
    val own = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.interfaceAddresses }
            .mapNotNull { it.broadcast }
    }.getOrDefault(emptyList())
    return (own + InetAddress.getByName("255.255.255.255")).distinct().map { InetSocketAddress(it, port) }
}

/**
 * Says [beacon] to [targets] once a second on a thread of its own until [stop]; a null beacon is
 * not sent (the game started).
 */
@OptIn(ExperimentalSerializationApi::class)
class LanAnnouncer(
    private val targets: List<InetSocketAddress>,
    private val beacon: () -> LanBeacon?,
) {
    @Volatile
    private var running = true
    private val thread = Thread(::run, "lan-announcer").apply { isDaemon = true }

    fun start() = thread.start()

    fun stop() {
        running = false
        thread.interrupt()
    }

    private fun run() {
        val socket = runCatching { DatagramSocket().apply { broadcast = true } }.getOrNull() ?: return
        socket.use {
            while (running) {
                beacon()?.let { now ->
                    val bytes = beaconCbor.encodeToByteArray(LanBeacon.serializer(), now)
                    for (target in targets) runCatching { socket.send(DatagramPacket(bytes, bytes.size, target)) }
                }
                try {
                    Thread.sleep(INTERVAL_MILLIS)
                } catch (_: InterruptedException) {
                    return
                }
            }
        }
    }

    private companion object {
        const val INTERVAL_MILLIS = 1_000L
    }
}

/**
 * Listens for games announcing themselves ([LanAnnouncer]) on [port] (0: any free one, for tests;
 * see [boundPort]); [games] holds those heard in the last few seconds that this version can join.
 */
@OptIn(ExperimentalSerializationApi::class)
class LanFinder(
    private val port: Int = DISCOVERY_PORT,
    private val protocolVersion: Int,
    private val rulesVersion: Int,
) {
    private val opened: Result<DatagramSocket> = runCatching {
        DatagramSocket(null as java.net.SocketAddress?).apply {
            reuseAddress = true
            broadcast = true
            soTimeout = RECEIVE_TIMEOUT_MILLIS
            bind(InetSocketAddress(this@LanFinder.port))
        }
    }
    private val socket: DatagramSocket? = opened.getOrNull()

    /** Why the port could not be opened, if it could not. */
    val openError: Throwable? get() = opened.exceptionOrNull()
    private val heard = LinkedHashMap<String, Pair<LanGame, Long>>()
    private val gamesFlow = MutableStateFlow<List<LanGame>>(emptyList())

    @Volatile
    private var running = false

    /** The games heard lately, in the order they were first heard. */
    val games: StateFlow<List<LanGame>> = gamesFlow.asStateFlow()

    /** The port listened on; -1 when it could not be opened (another program has it). */
    val boundPort: Int get() = socket?.localPort ?: -1

    fun start() {
        val socket = socket ?: return
        running = true
        Thread({ listen(socket) }, "lan-finder").apply { isDaemon = true }.start()
    }

    fun stop() {
        running = false
        socket?.close()
    }

    private fun listen(socket: DatagramSocket) {
        val buffer = ByteArray(MAX_BEACON_BYTES)
        while (running) {
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                socket.receive(packet)
                val beacon = runCatching {
                    beaconCbor.decodeFromByteArray(LanBeacon.serializer(), packet.data.copyOf(packet.length))
                }.getOrNull()
                // Android declares the address nullable; a packet without one is of no use.
                val address = packet.address?.hostAddress
                if (address != null && beacon != null && beacon.netVersion == NET_VERSION &&
                    beacon.protocolVersion == protocolVersion && beacon.rulesVersion == rulesVersion
                ) {
                    heard["$address:${beacon.port}"] = LanGame(address, beacon) to System.currentTimeMillis()
                }
            } catch (_: SocketTimeoutException) {
            } catch (_: Exception) {
                if (!running) return
            }
            val now = System.currentTimeMillis()
            heard.entries.removeAll { now - it.value.second > FORGET_MILLIS }
            gamesFlow.value = heard.values.map { it.first }
        }
    }

    private companion object {
        const val RECEIVE_TIMEOUT_MILLIS = 500
        const val FORGET_MILLIS = 3_500L
        const val MAX_BEACON_BYTES = 1_024
    }
}

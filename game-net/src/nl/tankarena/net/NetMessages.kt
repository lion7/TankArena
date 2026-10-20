package nl.tankarena.net

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import nl.tankarena.protocol.InputFrame
import nl.tankarena.protocol.snapshot.ServerFrame
import nl.tankarena.sim.DualSetup
import nl.tankarena.sim.MatchMode
import nl.tankarena.sim.Ruleset

/**
 * Version of the messages below. The frames inside carry [nl.tankarena.protocol.snapshot.PROTOCOL_VERSION];
 * the handshake compares both, and the rules version, and refuses a mismatch (ADR 0024).
 */
const val NET_VERSION: Int = 3

/** The port a game listens on unless told otherwise. */
const val DEFAULT_PORT: Int = 47_300

/** The WebSocket path of a game. */
const val GAME_PATH: String = "/tankarena"

/** At most this many players in a network game: two teams of two (ADR 0027). */
const val MAX_NET_PLAYERS: Int = 4

/** What a client sends. */
@Serializable
sealed interface ClientMessage {
    /** The first message: who wants to join, with how many players at that machine. */
    @Serializable
    @SerialName("hello")
    data class Hello(
        val netVersion: Int,
        val protocolVersion: Int,
        val rulesVersion: Int,
        val name: String,
        val localPlayers: Int,
    ) : ClientMessage

    /**
     * The client's players' controls since its last step (keys pressed at any tick count, as at the
     * keyboard), with the seats' player ids. Sent once a step, 33 times a second.
     */
    @Serializable
    @SerialName("inputs")
    data class Inputs(val frames: List<InputFrame>) : ClientMessage

    /** One of the machine's players picked its vehicle for the round (an index into the eight). */
    @Serializable
    @SerialName("choose")
    data class ChooseVehicle(val seat: Int, val vehicle: Int) : ClientMessage

    /** The client leaves the lobby or the match. */
    @Serializable
    @SerialName("leave")
    data object Leave : ClientMessage
}

/** What the server sends. */
@Serializable
sealed interface ServerMessage {
    /** The client is in: its players play with these player ids. */
    @Serializable
    @SerialName("welcome")
    data class Welcome(val seats: List<Int>, val hostName: String) : ServerMessage

    /** The client may not join; [reason] says why, to be shown as it is. */
    @Serializable
    @SerialName("refused")
    data class Refused(val reason: String) : ServerMessage

    /** Who is in the game before it starts, the host first. */
    @Serializable
    @SerialName("lobby")
    data class Lobby(val members: List<LobbyMember>) : ServerMessage

    /**
     * The match begins: [map] is the map's `.tamap` text, so any map can be played, also one only
     * the host has; [firstFrame] is what to show until the first [Frame] arrives.
     */
    @Serializable
    @SerialName("start")
    data class MatchStart(
        val baseName: String,
        val map: String,
        val mode: MatchMode,
        val players: Int,
        val dual: DualSetup,
        val ruleset: Ruleset,
        val firstFrame: ServerFrame,
    ) : ServerMessage

    /** One step of the match: the world, the events since the last frame, this client's views. */
    @Serializable
    @SerialName("frame")
    data class Frame(val frame: ServerFrame) : ServerMessage

    /** One step as the difference with the frame before it ([DeltaFrame]). */
    @Serializable
    @SerialName("delta")
    data class Delta(val delta: DeltaFrame) : ServerMessage

    /**
     * Against each other: the players pick their vehicles for round [round] (legacy select_tanks()),
     * from those [allowed]; who has not picked in [seconds] gets the first allowed. [seats] are this
     * machine's players' ids, final from now on.
     */
    @Serializable
    @SerialName("choose-vehicles")
    data class ChooseVehicles(
        val round: Int,
        val wins: List<Int>,
        val allowed: List<Boolean>,
        val seconds: Int,
        val seats: List<Int>,
    ) : ServerMessage

    /**
     * A round is over. Against each other [winner] is the side that won it (0: players 1 and 3,
     * 1: players 2 and 4) and [wins] the score; together [missionWon] says how the mission went.
     * [matchOver]: no round follows.
     */
    @Serializable
    @SerialName("round-over")
    data class RoundOver(
        val round: Int,
        val winner: Int?,
        val wins: List<Int>,
        val missionWon: Boolean?,
        val matchOver: Boolean,
    ) : ServerMessage

    /** The match or the lobby is over (the host left or stopped it). */
    @Serializable
    @SerialName("ended")
    data class Ended(val reason: String) : ServerMessage
}

/** One machine in the game: its name and its players' ids. */
@Serializable
data class LobbyMember(val name: String, val seats: List<Int>)

/** The wire form of the messages: CBOR, compact and the same on every platform. */
@OptIn(ExperimentalSerializationApi::class)
object NetCodec {
    private val cbor = Cbor { ignoreUnknownKeys = true }

    fun encode(message: ClientMessage): ByteArray = cbor.encodeToByteArray(ClientMessage.serializer(), message)

    fun encode(message: ServerMessage): ByteArray = cbor.encodeToByteArray(ServerMessage.serializer(), message)

    fun decodeClient(bytes: ByteArray): ClientMessage = cbor.decodeFromByteArray(ClientMessage.serializer(), bytes)

    fun decodeServer(bytes: ByteArray): ServerMessage = cbor.decodeFromByteArray(ServerMessage.serializer(), bytes)
}

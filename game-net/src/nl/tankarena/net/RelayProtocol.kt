package nl.tankarena.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The port a relay listens on unless told otherwise (ADR 0028). */
const val RELAY_PORT: Int = 47_310

/** The relay's paths: a host's control connection, a guest joining by code, a host's tunnel, the game list. */
object RelayPaths {
    const val HOST = "/host"
    const val JOIN = "/join/"
    const val TUNNEL = "/tunnel/"
    const val GAMES = "/games"
}

/** What a host and the relay say on the control connection (JSON text messages). */
@Serializable
sealed interface RelayMessage {
    /** The host publishes its game. */
    @Serializable
    @SerialName("register")
    data class Register(val game: RelayGame, val public: Boolean) : RelayMessage

    /** The game's code, to pass on to the players. */
    @Serializable
    @SerialName("registered")
    data class Registered(val code: String) : RelayMessage

    /** The lobby changed: players in it, listed publicly or not, still open to join. */
    @Serializable
    @SerialName("update")
    data class Update(val players: Int, val public: Boolean, val open: Boolean) : RelayMessage

    /** A guest wants in: the host opens [tunnel] to the relay and serves the guest on it. */
    @Serializable
    @SerialName("incoming")
    data class Incoming(val tunnel: String) : RelayMessage

    /** The relay will not keep the game (shutting down, refused); [reason] for the player. */
    @Serializable
    @SerialName("closed")
    data class Closed(val reason: String) : RelayMessage
}

/** A game on the relay, as the public list shows it. */
@Serializable
data class RelayGame(
    val code: String = "",
    val name: String,
    val map: String,
    val players: Int,
    val maxPlayers: Int,
    val netVersion: Int,
    val protocolVersion: Int,
    val rulesVersion: Int,
)

internal val relayJson = Json {
    ignoreUnknownKeys = true
    classDiscriminator = "type"
}

/**
 * Game codes: six characters without look-alikes (no 0/O, 1/I, 2/Z, 5/S, 8/B), shown as `K7Q-4MX`;
 * typed in any case, with or without the dash.
 */
object GameCodes {
    const val ALPHABET = "ACDEFGHJKLMNPQRTUVWXY34679"
    const val LENGTH = 6

    fun new(random: kotlin.random.Random = kotlin.random.Random.Default): String =
        String(CharArray(LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] })

    /** [text] as a code, or null if it is not one. */
    fun normalize(text: String): String? {
        val code = text.uppercase().filterNot { it == '-' || it.isWhitespace() }
        return code.takeIf { it.length == LENGTH && it.all { char -> char in ALPHABET } }
    }

    /** `K7Q4MX` as players see it: `K7Q-4MX`. */
    fun show(code: String): String = if (code.length == LENGTH) code.substring(0, 3) + "-" + code.substring(3) else code
}

/**
 * A relay's address as typed (`relay.example.org`, `host:47310`, `ws://…`, `wss://…`) as a WebSocket
 * base URL; plain names get `ws://` and the [RELAY_PORT].
 */
fun relayUrl(typed: String): String {
    val text = typed.trim().trimEnd('/')
    if (text.startsWith("ws://") || text.startsWith("wss://")) return text
    return if (text.substringAfterLast(']').contains(':')) "ws://$text" else "ws://$text:$RELAY_PORT"
}

/** The relay's HTTP base URL for [relayUrl] (the game list). */
internal fun relayHttpUrl(relayUrl: String): String = relayUrl.replaceFirst("wss://", "https://").replaceFirst("ws://", "http://")

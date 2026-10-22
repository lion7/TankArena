package nl.tankarena.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.builtins.ListSerializer

/** The public games on a relay (ADR 0028), those this version can join. */
object RelayDirectory {
    private val http by lazy { HttpClient(CIO) }

    suspend fun games(relay: String, protocolVersion: Int, rulesVersion: Int): List<RelayGame> = runCatching {
        val text = http.get(relayHttpUrl(relayUrl(relay)) + RelayPaths.GAMES).bodyAsText()
        relayJson.decodeFromString(ListSerializer(RelayGame.serializer()), text).filter {
            it.netVersion == NET_VERSION && it.protocolVersion == protocolVersion && it.rulesVersion == rulesVersion
        }
    }.getOrDefault(emptyList())
}

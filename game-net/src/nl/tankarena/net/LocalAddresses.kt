package nl.tankarena.net

import java.net.Inet4Address
import java.net.NetworkInterface

/** This machine's addresses on its networks (IPv4, no loopback), for the players who want to join. */
fun localAddresses(): List<String> = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback && !it.isVirtual }
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>()
        .mapNotNull { it.hostAddress }
        .distinct()
}.getOrDefault(emptyList())

/** A typed address: `host` or `host:port` (the port is [DEFAULT_PORT] when left out). */
fun parseAddress(text: String): Pair<String, Int> {
    val trimmed = text.trim()
    val colon = trimmed.lastIndexOf(':')
    if (colon <= 0 || trimmed.count { it == ':' } > 1) return trimmed to DEFAULT_PORT
    val port = trimmed.substring(colon + 1).toIntOrNull()?.takeIf { it in 1..65_535 } ?: return trimmed to DEFAULT_PORT
    return trimmed.substring(0, colon) to port
}

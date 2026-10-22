package nl.tankarena.relay

import nl.tankarena.net.RELAY_PORT
import nl.tankarena.net.RelayServer
import kotlin.system.exitProcess

/**
 * `app-relay [--port <port>] [--behind-proxy]`: runs the relay until the process is stopped.
 * `--behind-proxy` trusts `X-Forwarded-For` for the guest's address (a TLS proxy in front).
 */
fun main(args: Array<String>) {
    var port: Int? = RELAY_PORT
    var behindProxy = false
    val rest = args.iterator()
    while (port != null && rest.hasNext()) {
        when (rest.next()) {
            "--port" -> port = if (rest.hasNext()) rest.next().toIntOrNull()?.takeIf { it in 1..65_535 } else null
            "--behind-proxy" -> behindProxy = true
            else -> port = null
        }
    }
    if (port == null) {
        System.err.println("Usage: app-relay [--port <port>] [--behind-proxy]   (default port $RELAY_PORT)")
        exitProcess(2)
    }
    val relay = RelayServer(port, behindProxy = behindProxy) { println("[${java.time.LocalTime.now().withNano(0)}] $it") }
    val bound = relay.start()
    println("[${java.time.LocalTime.now().withNano(0)}] Relay listening on port $bound; games at /games")
    Runtime.getRuntime().addShutdownHook(Thread { relay.stop() })
    Thread.currentThread().join()
}

package nl.tankarena.relay

import nl.tankarena.net.RELAY_PORT
import nl.tankarena.net.RelayServer
import kotlin.system.exitProcess

/** `app-relay [--port <port>]`: runs the relay until the process is stopped. */
fun main(args: Array<String>) {
    val port = when {
        args.isEmpty() -> RELAY_PORT
        args.size == 2 && args[0] == "--port" -> args[1].toIntOrNull()?.takeIf { it in 1..65_535 }
        else -> null
    } ?: run {
        System.err.println("Usage: app-relay [--port <port>]   (default $RELAY_PORT)")
        exitProcess(2)
    }
    val relay = RelayServer(port) { println("[${java.time.LocalTime.now().withNano(0)}] $it") }
    val bound = relay.start()
    println("[${java.time.LocalTime.now().withNano(0)}] Relay listening on port $bound; games at /games")
    Runtime.getRuntime().addShutdownHook(Thread { relay.stop() })
    Thread.currentThread().join()
}

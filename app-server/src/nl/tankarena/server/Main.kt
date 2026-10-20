package nl.tankarena.server

import nl.tankarena.content.GameModeCompatibility
import nl.tankarena.net.DEFAULT_PORT
import nl.tankarena.net.GameServer
import nl.tankarena.net.HostedMatch
import nl.tankarena.net.NetSeries
import nl.tankarena.net.MAX_NET_PLAYERS
import nl.tankarena.net.localAddresses
import nl.tankarena.protocol.snapshot.GameEvent
import nl.tankarena.sim.MatchMode
import nl.tankarena.sim.Ruleset
import nl.tankarena.sim.SimulationLoop
import nl.tankarena.sim.map.MapConversion
import nl.tankarena.sim.map.MapDocument
import nl.tankarena.sim.map.MapFile
import nl.tankarena.sim.map.ShippedMaps
import java.io.File
import java.util.concurrent.CountDownLatch
import kotlin.system.exitProcess

/** What the server hosts, from the command line. */
data class ServerOptions(
    val map: String,
    val mode: MatchMode?,
    val players: Int,
    /** Against each other: rounds in a match (1, 3, 5, 7 or 9; 0 endless). */
    val rounds: Int = 3,
    val port: Int,
    val name: String,
    val repeat: Boolean,
) {
    companion object {
        const val USAGE = """Usage: app-server --map <name or file.tamap> [options]
  --map <map>       a shipped map by name (flagisle) or a .tamap file
  --mode <mode>     versus | together (default: what the map is made for)
  --players <n>     players to wait for before the match starts, 1-$MAX_NET_PLAYERS (default 2)
  --rounds <n>      versus: rounds in a match, 1, 3, 5, 7 or 9, 0 endless (default 3)
  --port <port>     the port to listen on (default $DEFAULT_PORT)
  --name <name>     the server's name in the lobby (default: tankarena)
  --repeat          host a new game on the same map after each one"""

        /** The options in [args], or null when they are wrong (the reason is printed). */
        fun parse(args: Array<String>): ServerOptions? {
            var map: String? = null
            var mode: MatchMode? = null
            var players = 2
            var rounds = 3
            var port = DEFAULT_PORT
            var name = "tankarena"
            var repeat = false
            var index = 0
            fun value(): String? = args.getOrNull(++index)
            while (index < args.size) {
                when (args[index]) {
                    "--map" -> map = value()
                    "--mode" -> mode = when (value()) {
                        "versus" -> MatchMode.DUAL
                        "together" -> MatchMode.DUAL_VS_COMPUTER
                        else -> return null.also { System.err.println("--mode is versus or together") }
                    }
                    "--players" -> players = value()?.toIntOrNull()?.takeIf { it in 1..MAX_NET_PLAYERS }
                        ?: return null.also { System.err.println("--players is 1 to $MAX_NET_PLAYERS") }
                    "--rounds" -> rounds = value()?.toIntOrNull()?.takeIf { it == 0 || (it in 1..9 && it % 2 == 1) }
                        ?: return null.also { System.err.println("--rounds is 1, 3, 5, 7, 9 or 0") }
                    "--port" -> port = value()?.toIntOrNull()?.takeIf { it in 1..65_535 }
                        ?: return null.also { System.err.println("--port is a port number") }
                    "--name" -> name = value() ?: return null
                    "--repeat" -> repeat = true
                    else -> return null.also { System.err.println("Unknown option ${args[index]}") }
                }
                index++
            }
            return ServerOptions(map ?: return null.also { System.err.println("--map is needed") }, mode, players, rounds, port, name, repeat)
        }
    }
}

fun main(args: Array<String>) {
    val options = ServerOptions.parse(args) ?: run {
        System.err.println(ServerOptions.USAGE)
        exitProcess(2)
    }
    val (baseName, map) = loadMap(options.map) ?: run {
        System.err.println("No map ${options.map}: neither a shipped map nor a .tamap file.")
        exitProcess(1)
    }
    do {
        DedicatedGame(options, baseName, map).run()
    } while (options.repeat)
}

/** A shipped map by its base name, or a `.tamap` file. */
fun loadMap(name: String): Pair<String, MapDocument>? {
    val file = File(name)
    if (file.isFile) return file.nameWithoutExtension.lowercase() to MapFile.read(file.readText())
    return ShippedMaps.loadAll().firstOrNull { it.baseName.equals(name, ignoreCase = true) }?.let { it.baseName to it.document }
}

/** The mode a map is made for: dual maps are played against each other, the rest together. */
fun defaultMode(map: MapDocument): MatchMode =
    when (MapConversion.sidecarOf(map).metadata.modeCompatibility) {
        GameModeCompatibility.DUAL, GameModeCompatibility.DONT_CARE -> MatchMode.DUAL
        else -> MatchMode.DUAL_VS_COMPUTER
    }

/**
 * One game on the dedicated server: wait in the lobby for the players, then play the rounds at 100
 * ticks a second (against each other with a vehicle choice before each, until the match is decided;
 * together one mission). A round ends as a local one does (3 s after it is won or lost); the game
 * also ends when everyone has left.
 */
class DedicatedGame(private val options: ServerOptions, private val baseName: String, private val map: MapDocument) {
    fun run() {
        val mode = options.mode ?: defaultMode(map)
        val server = GameServer(options.name, hostPlayers = 0, rulesVersion = Ruleset.EXTENDED.rulesVersion, port = options.port)
        server.mapName = baseName
        val port = server.start()
        log("Hosting ${baseName.uppercase()} (${if (mode == MatchMode.DUAL) "versus" else "together"}) for ${options.players} players")
        log("Join at ${(localAddresses().ifEmpty { listOf("this machine") }).joinToString(" or ") { "$it:$port" }}")
        var seen = 0
        while (true) {
            val joined = server.members.value.sumOf { it.seats.size }
            if (joined != seen) {
                log("Players: $joined of ${options.players} (${server.members.value.filter { it.seats.isNotEmpty() }.joinToString { it.name }})")
                seen = joined
            }
            if (joined >= options.players) break
            Thread.sleep(LOBBY_POLL_MILLIS)
        }
        val series = NetSeries(server, baseName, map, mode, rounds = options.rounds)
        while (true) {
            if (series.versus) {
                series.openChoice()
                log("Round ${series.round}: the players pick their vehicles")
                while (!series.choiceDone() && !everyoneLeft(server)) Thread.sleep(LOBBY_POLL_MILLIS)
            }
            if (everyoneLeft(server)) break
            val outcome = playRound(server, series.startRound(), series.round)
            val result = series.roundEnded(
                winner = if (series.versus) outcome?.let { if (it is GameEvent.MissionWon) 0 else 1 } else null,
                missionWon = if (series.versus) null else outcome is GameEvent.MissionWon,
            )
            val winner = result.winner
            log(
                when {
                    !series.versus -> if (result.missionWon == true) "The mission is won" else "The mission is not won"
                    winner == null -> "Nobody won the round"
                    else -> "Team ${winner + 1} won the round: ${result.wins.joinToString(" - ")}"
                },
            )
            if (result.matchOver || everyoneLeft(server)) break
            Thread.sleep(RESULT_MILLIS)
        }
        server.stop("The game is over.")
        log("The game is over")
    }

    private fun everyoneLeft(server: GameServer): Boolean = server.seatsInGame().isEmpty()

    /** Plays one round at 100 ticks a second; returns how it ended (null: everyone left). */
    private fun playRound(server: GameServer, match: HostedMatch, round: Int): GameEvent? {
        log("Round $round starts")
        val done = CountDownLatch(1)
        var endTick = Long.MAX_VALUE
        var ticks = 0L
        var outcome: GameEvent? = null
        val loop = SimulationLoop {
            val frame = match.tick()
            ticks++
            if (outcome == null) {
                outcome = frame.world.events.firstOrNull { it is GameEvent.MissionWon || it is GameEvent.MissionLost }
                if (outcome != null) endTick = ticks + END_BANNER_TICKS
            }
            val running = ticks < endTick && !everyoneLeft(server)
            if (!running) done.countDown()
            running
        }
        loop.start()
        done.await()
        loop.stop()
        match.dispose()
        return outcome
    }

    private fun log(text: String) = println("[${java.time.LocalTime.now().withNano(0)}] $text")

    private companion object {
        const val LOBBY_POLL_MILLIS = 100L

        /** How long the round's result shows before the next round's choice. */
        const val RESULT_MILLIS = 5_000L

        /** Legacy `game.end_round = tinit(300)`: the game runs on for 3 s after the end. */
        const val END_BANNER_TICKS = 300L
    }
}

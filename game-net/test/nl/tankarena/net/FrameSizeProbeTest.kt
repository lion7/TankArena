package nl.tankarena.net

import nl.tankarena.sim.LocalMatchClient
import nl.tankarena.sim.MatchMode
import nl.tankarena.sim.map.ShippedMaps
import kotlin.test.Test

/**
 * Not a test: how many bytes one frame takes on the wire for every shipped map, after 3 s of play.
 * Runs only with `FRAME_PROBE=1 ./kotlin test -m game-net`; prints `FRAMEPROBE` lines.
 */
class FrameSizeProbeTest {
    @Test
    fun probe() {
        System.getenv("FRAME_PROBE") ?: return
        val rows = ShippedMaps.loadAll().map { scene ->
            val client = LocalMatchClient.fromMap(scene.document, MatchMode.SINGLE)
            var frame = client.currentServerFrame()
            repeat(300) { frame = client.tick() }
            val bytes = NetCodec.encode(ServerMessage.Frame(frame.forSeats(listOf(0)))).size
            client.dispose()
            Triple(scene.baseName, bytes, frame.world.actors.size)
        }.sortedByDescending { it.second }
        rows.take(8).forEach { println("FRAMEPROBE ${it.first} ${it.second} bytes ${it.third} actors") }
        println("FRAMEPROBE median ${rows[rows.size / 2].second} bytes")
    }
}

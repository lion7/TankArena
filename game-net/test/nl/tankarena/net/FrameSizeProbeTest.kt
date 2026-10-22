package nl.tankarena.net

import nl.tankarena.protocol.InputFrame
import nl.tankarena.sim.LocalMatchClient
import nl.tankarena.sim.MatchMode
import nl.tankarena.sim.map.ShippedMaps
import java.util.zip.Deflater
import kotlin.test.Test

/**
 * Not a test: how many bytes a network frame takes for every shipped map over 10 s of play: whole,
 * as a delta, and as the deflated delta the WebSocket sends. Runs only with
 * `FRAME_PROBE=1 ./kotlin test -m game-net`; prints `FRAMEPROBE` lines, largest first.
 */
class FrameSizeProbeTest {
    @Test
    fun probe() {
        System.getenv("FRAME_PROBE") ?: return
        val steps = 330
        val rows = ShippedMaps.loadAll().map { scene ->
            val match = LocalMatchClient.fromMap(scene.document, MatchMode.SINGLE)
            val encoder = DeltaEncoder()
            val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
            val out = ByteArray(1 shl 20)
            var whole = 0L
            var delta = 0L
            var deflated = 0L
            repeat(steps) { step ->
                var frame = match.tick()
                repeat(2) {
                    match.submitInput(InputFrame(0, step.toLong(), forward = step % 50 < 40, firePrimary = true))
                    frame = match.tick()
                }
                val sent = frame.forSeats(listOf(0))
                val bytes = NetCodec.encode(encoder.encode(sent))
                whole += NetCodec.encode(ServerMessage.Frame(sent)).size
                delta += bytes.size
                deflater.setInput(bytes)
                deflated += deflater.deflate(out, 0, out.size, Deflater.SYNC_FLUSH)
            }
            match.dispose()
            listOf(scene.baseName, whole / steps, delta / steps, deflated / steps)
        }.sortedByDescending { it[3] as Long }
        println("FRAMEPROBE map            whole  delta  sent (bytes per frame)")
        rows.take(10).forEach { println("FRAMEPROBE %-14s %6d %6d %5d".format(it[0], it[1], it[2], it[3])) }
        println("FRAMEPROBE median sent ${rows[rows.size / 2][3]} bytes")
    }
}

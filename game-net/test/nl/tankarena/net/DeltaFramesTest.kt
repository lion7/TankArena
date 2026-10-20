package nl.tankarena.net

import nl.tankarena.protocol.InputFrame
import nl.tankarena.sim.LocalMatchClient
import nl.tankarena.sim.MatchMode
import nl.tankarena.sim.map.ShippedMaps
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DeltaFramesTest {
    /** Plays [map] with a player who drives and fires, and checks every frame survives encode, wire and decode. */
    private fun roundTrip(map: String, steps: Int): Pair<Long, Long> {
        val match = LocalMatchClient.fromMap(ShippedMaps.loadAll().first { it.baseName == map }.document, MatchMode.SINGLE)
        val encoder = DeltaEncoder()
        val decoder = DeltaDecoder()
        var wholeBytes = 0L
        var deltaBytes = 0L
        var tick = 0
        repeat(steps) { step ->
            // Drop a frame now and then, as a full queue does: the encoder starts over with a whole one.
            if (step == steps / 2) encoder.reset()
            var frame = match.tick()
            repeat(2) {
                match.submitInput(InputFrame(0, (tick++).toLong(), forward = step % 50 < 40, turnLeft = step % 70 < 10, firePrimary = true))
                frame = match.tick()
            }
            val sent = frame.forSeats(listOf(0))
            val message = NetCodec.decodeServer(NetCodec.encode(encoder.encode(sent)))
            val received = when (message) {
                is ServerMessage.Frame -> decoder.whole(message.frame)
                is ServerMessage.Delta -> decoder.apply(message.delta)
                else -> error("unexpected $message")
            }
            assertEquals(sent, received, "step $step of $map")
            if (step > 0 && step != steps / 2) assertIs<ServerMessage.Delta>(message)
            wholeBytes += NetCodec.encode(ServerMessage.Frame(sent)).size
            deltaBytes += NetCodec.encode(message).size
        }
        match.dispose()
        return wholeBytes to deltaBytes
    }

    @Test
    fun `deltas rebuild every frame exactly`() {
        roundTrip("begin1", 300)
        roundTrip("flagisle", 300)
    }

    @Test
    fun `on a map full of things the deltas are much smaller`() {
        for (map in listOf("suburbs", "nuke")) {
            val (whole, delta) = roundTrip(map, 200)
            assertTrue(delta * 3 < whole, "$map: deltas $delta bytes against $whole whole")
        }
    }
}

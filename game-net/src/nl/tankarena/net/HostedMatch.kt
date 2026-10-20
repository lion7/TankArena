package nl.tankarena.net

import nl.tankarena.protocol.InputFrame
import nl.tankarena.protocol.snapshot.GameEvent
import nl.tankarena.protocol.snapshot.ServerFrame
import nl.tankarena.sim.LocalMatchClient
import nl.tankarena.sim.MatchClient
import nl.tankarena.sim.MatchRecorder
import nl.tankarena.sim.STEP_TICKS

/**
 * The match on the host (ADR 0024): it simulates like a local game, with the joined players' latest
 * controls added on every tick, and sends the world to the other machines once a step, with every
 * event since the last one. The host's screen uses it like a [LocalMatchClient].
 */
class HostedMatch internal constructor(
    private val match: LocalMatchClient,
    private val server: GameServer,
) : MatchClient {
    private val events = ArrayList<GameEvent>()

    /** The recording of the match, as in a local game. */
    val recorder: MatchRecorder? get() = match.recorder

    override fun submitInput(input: InputFrame) = match.submitInput(input)

    override fun tick(): ServerFrame {
        for (input in server.remoteInputs.values) match.submitInput(input)
        val frame = match.tick()
        events += frame.world.events
        // The world only changes on a step; a frame made by one is one tick past it (ticks 1, 4, 7, ...).
        if (frame.tick % STEP_TICKS == 1L) {
            server.broadcast(frame.copy(world = frame.world.copy(events = events.toList())))
            events.clear()
        }
        return frame
    }

    override fun currentServerFrame(): ServerFrame = match.currentServerFrame()

    /** The host's game ends: the others are told, and the server stops. */
    override fun dispose() {
        server.matchEnded()
        match.dispose()
    }
}

package nl.tankarena.net

import nl.tankarena.protocol.InputFrame
import nl.tankarena.protocol.snapshot.GameEvent
import nl.tankarena.protocol.snapshot.ServerFrame
import nl.tankarena.sim.MatchClient
import nl.tankarena.sim.MatchMode
import nl.tankarena.sim.STEP_TICKS
import nl.tankarena.sim.map.MapDocument
import nl.tankarena.sim.map.MapFile

/**
 * A match played on another machine, shown here like a local one (ADR 0024): [tick] runs at the
 * same 100 ticks a second, sends this machine's players' controls once a step, and plays the
 * received steps back [DELAY_TICKS] behind the newest, so an uneven network does not show. Every
 * event reaches the screen exactly once, with the first tick after its step.
 */
class RemoteMatchClient internal constructor(
    private val start: ServerMessage.MatchStart,
    private val connection: GameClient,
) : MatchClient {
    /** The map, sent by the host. */
    val map: MapDocument by lazy { MapFile.read(start.map) }
    val baseName: String get() = start.baseName
    val mode: MatchMode get() = start.mode
    val players: Int get() = start.players

    /** This machine's players' ids, in order. */
    val seats: List<Int> = start.firstFrame.playerViews.map { it.playerId }

    /** Why the match stopped coming (the host left, the connection broke), or null while it runs. */
    @Volatile
    var endedReason: String? = null
        private set

    private val received = ArrayDeque<ServerFrame>()
    private val latched = HashMap<Int, InputFrame>()
    private val pendingEvents = ArrayList<GameEvent>()
    private var shown: ServerFrame = start.firstFrame
    private var playout = Long.MIN_VALUE
    private var ticks = 0L

    internal fun receive(frame: ServerFrame) {
        synchronized(received) { received.addLast(frame) }
    }

    internal fun end(reason: String) {
        if (endedReason == null) endedReason = reason
    }

    /** Like at the keyboard, a key pressed at any tick of a step counts for the step. */
    override fun submitInput(input: InputFrame) {
        latched[input.playerId] = latched[input.playerId]?.let { it latch input } ?: input
    }

    override fun tick(): ServerFrame {
        ticks++
        if (ticks % STEP_TICKS == 0L && latched.isNotEmpty()) {
            connection.send(ClientMessage.Inputs(latched.values.toList()))
            latched.clear()
        }
        synchronized(received) {
            if (playout == Long.MIN_VALUE && received.isNotEmpty()) playout = received.first().tick - DELAY_TICKS
            if (playout != Long.MIN_VALUE) {
                playout++
                // Far behind (a stall, a slow machine): catch up to the delay; the frames skipped still give their events.
                val newest = received.lastOrNull()
                if (newest != null && newest.tick - playout > DELAY_TICKS + MAX_EXTRA_DELAY) playout = newest.tick - DELAY_TICKS
                while (received.isNotEmpty() && received.first().tick <= playout) {
                    val frame = received.removeFirst()
                    pendingEvents += frame.world.events
                    shown = frame
                }
                // Nothing came in time: wait here, so the delay builds up again instead of every frame showing late.
                if (received.isEmpty() && playout > shown.tick) playout = shown.tick
            }
        }
        if (pendingEvents.isEmpty()) return shown.withoutEvents()
        val withEvents = shown.copy(world = shown.world.copy(events = pendingEvents.toList()))
        pendingEvents.clear()
        return withEvents
    }

    override fun currentServerFrame(): ServerFrame = shown.withoutEvents()

    /** Leaves the match. */
    override fun dispose() = connection.close()

    private var quiet: Pair<ServerFrame, ServerFrame>? = null

    /** [shown] again without its events, which were handed out already (made once per frame). */
    private fun ServerFrame.withoutEvents(): ServerFrame {
        if (world.events.isEmpty()) return this
        quiet?.let { (from, to) -> if (from === this) return to }
        val stripped = copy(world = world.copy(events = emptyList()))
        quiet = this to stripped
        return stripped
    }

    companion object {
        /** About 100 ms: three steps behind the newest frame. */
        const val DELAY_TICKS = 3L * STEP_TICKS
        private const val MAX_EXTRA_DELAY = 6L * STEP_TICKS
    }
}

private infix fun InputFrame.latch(next: InputFrame) = next.copy(
    forward = forward || next.forward,
    reverse = reverse || next.reverse,
    turnLeft = turnLeft || next.turnLeft,
    turnRight = turnRight || next.turnRight,
    aimLeft = aimLeft || next.aimLeft,
    aimRight = aimRight || next.aimRight,
    firePrimary = firePrimary || next.firePrimary,
    fireSecondary = fireSecondary || next.fireSecondary,
    shield = shield || next.shield,
    cycleWeaponLeft = cycleWeaponLeft || next.cycleWeaponLeft,
    cycleWeaponRight = cycleWeaponRight || next.cycleWeaponRight,
    selectTankId = if (next.selectTankId != 0) next.selectTankId else selectTankId,
)

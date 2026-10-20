package nl.tankarena.sim

import nl.tankarena.core.FixedStepClock
import java.util.concurrent.locks.LockSupport

/**
 * Runs a match on its own thread at the original's fixed 100 ticks per second, whatever the
 * screen does. [step] runs one tick and returns false to stop. When a tick (or the machine)
 * falls behind, the loop catches up by running ticks back to back, but drops the backlog after
 * [MAX_CATCH_UP] of them, so a long stall slows the game down instead of fast-forwarding it.
 */
class SimulationLoop(private val step: () -> Boolean) {
    @Volatile
    private var running = false
    private val thread = Thread(::run, "simulation").apply { isDaemon = true }

    fun start() {
        running = true
        thread.start()
    }

    /** Stops the loop and waits until the tick in progress (if any) has finished. */
    fun stop() {
        running = false
        if (Thread.currentThread() !== thread) thread.join(1_000)
    }

    private fun run() {
        var next = System.nanoTime()
        while (running) {
            var ran = 0
            while (running && System.nanoTime() >= next && ran < MAX_CATCH_UP) {
                if (!step()) {
                    running = false
                    return
                }
                next += TICK_NANOS
                ran++
            }
            if (ran == MAX_CATCH_UP) next = System.nanoTime()
            val wait = next - System.nanoTime()
            if (wait > 0) LockSupport.parkNanos(wait)
        }
    }

    private companion object {
        val TICK_NANOS: Long = FixedStepClock.MILLIS_PER_TICK * 1_000_000L
        const val MAX_CATCH_UP = 10
    }
}

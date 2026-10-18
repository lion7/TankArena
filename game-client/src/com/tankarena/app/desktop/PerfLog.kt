package com.tankarena.app.desktop

import java.io.File
import java.lang.management.ManagementFactory

/**
 * Opt-in performance log (`TANKARENA_PERF=1`): once a second, how long the simulation ticks
 * took, how evenly the game loop and the screen ran, and how much the garbage collector stopped
 * everything. Written to stdout and to `perf.log` in the working directory.
 */
internal object PerfLog {
    val enabled: Boolean = System.getenv("TANKARENA_PERF") == "1"

    private val gcBeans = ManagementFactory.getGarbageCollectorMXBeans()
    private val memory = ManagementFactory.getMemoryMXBean()
    private val file: File? = if (enabled) File("perf.log").also { it.writeText("") } else null

    private var windowStart = System.nanoTime()
    private var ticks = 0
    private var tickNanos = 0L
    private var tickMaxNanos = 0L
    private var lastLoopNanos = 0L
    private var loopGapMaxNanos = 0L
    private var frames = 0
    private var lastFrameNanos = 0L
    private var frameGapMaxNanos = 0L
    private var framesOver20ms = 0
    private var gcMillisAtStart = gcMillis()
    private var label = ""

    @Synchronized
    fun start(label: String) {
        if (!enabled) return
        this.label = label
        reset(System.nanoTime())
        write("PERF start $label")
    }

    /** One simulation tick of [nanos], run by the game loop at [now]. */
    @Synchronized
    fun tick(nanos: Long, now: Long) {
        if (!enabled) return
        ticks++
        tickNanos += nanos
        if (nanos > tickMaxNanos) tickMaxNanos = nanos
        if (lastLoopNanos != 0L) loopGapMaxNanos = maxOf(loopGapMaxNanos, now - lastLoopNanos)
        lastLoopNanos = now
        flushIfDue(now)
    }

    /** A frame drawn at [frameNanos] (Compose's frame clock). */
    @Synchronized
    fun frame(frameNanos: Long) {
        if (!enabled) return
        frames++
        if (lastFrameNanos != 0L) {
            val gap = frameNanos - lastFrameNanos
            if (gap > frameGapMaxNanos) frameGapMaxNanos = gap
            if (gap > 20_000_000L) framesOver20ms++
        }
        lastFrameNanos = frameNanos
    }

    private fun flushIfDue(now: Long) {
        if (now - windowStart < 1_000_000_000L) return
        val gc = gcMillis()
        val heapMb = memory.heapMemoryUsage.used / (1024 * 1024)
        write(
            "PERF %s ticks=%d tickMean=%.2fms tickMax=%.2fms loopGapMax=%.1fms frames=%d frameGapMax=%.1fms frames>20ms=%d gc=%dms heap=%dMB".format(
                label, ticks, if (ticks > 0) tickNanos / 1e6 / ticks else 0.0, tickMaxNanos / 1e6, loopGapMaxNanos / 1e6,
                frames, frameGapMaxNanos / 1e6, framesOver20ms, gc - gcMillisAtStart, heapMb,
            ),
        )
        reset(now)
    }

    private fun reset(now: Long) {
        windowStart = now
        ticks = 0
        tickNanos = 0
        tickMaxNanos = 0
        loopGapMaxNanos = 0
        frames = 0
        frameGapMaxNanos = 0
        framesOver20ms = 0
        gcMillisAtStart = gcMillis()
    }

    private fun gcMillis(): Long = gcBeans.sumOf { it.collectionTime.coerceAtLeast(0) }

    private fun write(line: String) {
        println(line)
        file?.appendText(line + "\n")
    }
}

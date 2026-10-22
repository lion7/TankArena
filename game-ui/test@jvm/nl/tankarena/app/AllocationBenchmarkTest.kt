package nl.tankarena.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import nl.tankarena.protocol.snapshot.ServerFrame
import nl.tankarena.render.ViewportEventQueue
import nl.tankarena.sim.MatchRecording
import nl.tankarena.sim.MatchReplay
import nl.tankarena.ui.compose.GameMode
import nl.tankarena.ui.compose.menu.MissionCatalog
import java.io.File
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlinx.coroutines.runBlocking

/**
 * Not a test: how much memory a game allocates, per simulation tick and per drawn frame, replaying a
 * recorded match through the real [GameplayFrame] at a phone's 2400×1080. Runs only with
 * `ALLOC_BENCH=1 ./kotlin test -m game-ui`; prints `ALLOCBENCH` lines.
 */
class AllocationBenchmarkTest {

    @Test
    fun benchmark() {
        System.getenv("ALLOC_BENCH") ?: return
        val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        fun allocated() = threads.getThreadAllocatedBytes(Thread.currentThread().threadId())
        val missions = runBlocking { MissionCatalog.load() }.associateBy { it.baseName }
        val mission = missions.getValue("begin1")
        val recording = MatchRecording.fromJson(File(RECORDINGS, "begin1-20261019-172316-won.json").readText())

        // The simulation alone: bytes per tick.
        val frames = ArrayList<ServerFrame>()
        var simBytes = 0L
        var mark = allocated()
        MatchReplay.replayFrames(recording, mission.document) { frame ->
            simBytes += allocated() - mark
            if (frame.tick in FIRST_TICK until FIRST_TICK + TICKS) frames += frame
            mark = allocated()
            frame.tick < FIRST_TICK + TICKS
        }
        val simTicks = frames.last().tick
        println("ALLOCBENCH sim: %d ticks, %.1f KB per tick (frames kept included)".format(simTicks, simBytes / 1024.0 / simTicks))

        // Drawing: one frame per tick, as at 100 frames a second.
        val queues = List(2) { ViewportEventQueue() }
        var shown by mutableStateOf(frames.first())
        ImageComposeScene(WIDTH, HEIGHT) {
            GameplayFrame(mission, GameMode.SINGLE_PLAYER_VS_COMPUTER, shown, listOf(0), queues, null, brightness = 1f)
        }.use { scene ->
            var time = 0L
            repeat(40) { scene.render(time); time += FRAME_NANOS; Thread.sleep(15) }
            for (round in 0 until 2) {
                val start = allocated()
                for (frame in frames) {
                    queues.forEach { it.push(frame.world.events) }
                    shown = frame
                    scene.render(time)
                    time += FRAME_NANOS
                }
                val bytes = allocated() - start
                println("ALLOCBENCH render round %d: %d frames, %.1f KB per frame".format(round, frames.size, bytes / 1024.0 / frames.size))
            }
            // The same picture again and again: what a frame costs when nothing changes.
            val start = allocated()
            repeat(STILL_FRAMES) { scene.render(time); time += FRAME_NANOS }
            println("ALLOCBENCH render still: %.1f KB per frame".format((allocated() - start) / 1024.0 / STILL_FRAMES))
        }
    }

    private companion object {
        const val RECORDINGS = "../game-sim/testResources/recordings/v11"
        const val WIDTH = 2400
        const val HEIGHT = 1080
        const val FIRST_TICK = 1000L
        const val TICKS = 1000L
        const val STILL_FRAMES = 300
        const val FRAME_NANOS = 10_000_000L
    }
}

package com.tankarena.app.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import com.tankarena.protocol.snapshot.ServerFrame
import com.tankarena.render.ViewportEventQueue
import com.tankarena.sim.MatchMode
import com.tankarena.sim.MatchRecording
import com.tankarena.sim.MatchReplay
import com.tankarena.ui.compose.GameMode
import com.tankarena.ui.compose.MissionOutcome
import com.tankarena.ui.compose.menu.MissionCatalog
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat

/**
 * Reference renders of the game screen: fixed moments of recorded (or idle) matches, drawn through
 * the real [GameplayFrame] at the original's 640×480 and compared with the PNGs in
 * `testResources/reference-renders`. They pin what the current renderer draws, so that a new one
 * (ADR 0019) can be checked against it.
 *
 * `UPDATE_REFERENCE_RENDERS=1 ./kotlin test -m game-client` rewrites the PNGs; review them before
 * committing. On a mismatch the actual image is written to `build/reference-renders`.
 */
class ReferenceRenderTest {

    private class Scenario(
        val name: String,
        val mission: String,
        val mode: GameMode,
        /** A committed recording to replay; null plays the mission with nobody at the controls. */
        val recording: String?,
        val tick: Long,
        val banner: MissionOutcome? = null,
    )

    private val scenarios = listOf(
        Scenario("begin1-start", "begin1", GameMode.SINGLE_PLAYER_VS_COMPUTER, "begin1-20261019-172316-won.json", tick = 60),
        Scenario("begin1-fight", "begin1", GameMode.SINGLE_PLAYER_VS_COMPUTER, "begin1-20261019-172316-won.json", tick = 1500),
        Scenario("begin1-won", "begin1", GameMode.SINGLE_PLAYER_VS_COMPUTER, "begin1-20261019-172316-won.json", tick = 3950, MissionOutcome.WON),
        Scenario("combat1-lost", "combat1", GameMode.SINGLE_PLAYER_VS_COMPUTER, "combat1-20261019-142812-lost.json", tick = 1900, MissionOutcome.LOST),
        Scenario("begin3-play", "begin3", GameMode.SINGLE_PLAYER_VS_COMPUTER, "begin3-20261019-142540-won.json", tick = 2500),
        Scenario("zulu-night", "zulu", GameMode.SINGLE_PLAYER_VS_COMPUTER, recording = null, tick = 300),
        Scenario("air-two-players", "air", GameMode.DUAL_PLAYER_VS_COMPUTER, recording = null, tick = 300),
        Scenario("flagisle-versus", "flagisle", GameMode.PLAYER_VS_PLAYER, recording = null, tick = 300),
    )

    @Test
    fun `the game screen draws as in the reference renders`() {
        val update = System.getenv("UPDATE_REFERENCE_RENDERS") == "1"
        val missions = runBlocking { MissionCatalog.load() }.associateBy { it.baseName }
        val failures = mutableListOf<String>()
        for (scenario in scenarios) {
            val mission = missions.getValue(scenario.mission)
            val recording = scenario.recording
                ?.let { MatchRecording.fromJson(File(RECORDINGS, it).readText()) }
                ?: MatchRecording(mission = scenario.mission, mode = scenario.mode.toMatchMode(), ticks = scenario.tick + 1)
            val actual = render(scenario, mission, recording)
            val reference = File(REFERENCES, "${scenario.name}.png")
            if (update) {
                reference.parentFile.mkdirs()
                ImageIO.write(actual, "png", reference)
                continue
            }
            if (!reference.isFile) {
                failures += "${scenario.name}: no reference render; run with UPDATE_REFERENCE_RENDERS=1"
                continue
            }
            val differing = differingPixels(ImageIO.read(reference), actual)
            if (differing > actual.width * actual.height * MAX_DIFFERING_FRACTION) {
                File(OUTPUT).mkdirs()
                ImageIO.write(actual, "png", File(OUTPUT, "${scenario.name}.png"))
                failures += "${scenario.name}: $differing pixels differ (actual in $OUTPUT)"
            }
        }
        if (failures.isNotEmpty()) fail(failures.joinToString("\n"))
    }

    /**
     * Replays [recording] to [Scenario.tick] and draws that tick. Only the last [RENDERED_TICKS]
     * ticks are drawn one by one (with their events), so effects and colour cycling are in the same
     * state on every run; the first frame is drawn until its pictures have loaded.
     */
    private fun render(scenario: Scenario, mission: com.tankarena.ui.compose.menu.MissionEntry, recording: MatchRecording): BufferedImage {
        val frames = mutableListOf<ServerFrame>()
        MatchReplay.replayFrames(recording, mission.document) { frame ->
            if (frame.tick > scenario.tick - RENDERED_TICKS) frames += frame
            frame.tick < scenario.tick
        }
        assertTrue(frames.isNotEmpty() && frames.last().tick == scenario.tick, "${scenario.name}: the match ended before tick ${scenario.tick}")
        val playerIds = if (scenario.mode == GameMode.SINGLE_PLAYER_VS_COMPUTER) listOf(0) else listOf(0, 1)
        val queues = List(2) { ViewportEventQueue() }
        var shown by mutableStateOf(frames.first())
        ImageComposeScene(SCREEN_WIDTH, SCREEN_HEIGHT) {
            GameplayFrame(mission, scenario.mode, shown, playerIds, queues, scenario.banner, brightness = 1f)
        }.use { scene ->
            var time = 0L
            fun draw() = scene.render(time).also { time += FRAME_NANOS }
            repeat(SETTLE_FRAMES) { draw(); Thread.sleep(SETTLE_SLEEP_MILLIS) }
            for (frame in frames.drop(1)) {
                queues.forEach { it.push(frame.world.events) }
                shown = frame
                draw()
            }
            repeat(SETTLE_FRAMES) { draw(); Thread.sleep(SETTLE_SLEEP_MILLIS) }
            val png = draw().encodeToData(EncodedImageFormat.PNG)!!.bytes
            return ImageIO.read(png.inputStream())
        }
    }

    /** Pixels whose colour differs by more than [CHANNEL_TOLERANCE] in any channel. */
    private fun differingPixels(expected: BufferedImage, actual: BufferedImage): Int {
        if (expected.width != actual.width || expected.height != actual.height) return actual.width * actual.height
        var count = 0
        for (y in 0 until actual.height) {
            for (x in 0 until actual.width) {
                val a = expected.getRGB(x, y)
                val b = actual.getRGB(x, y)
                if (a == b) continue
                val differs = (0..24 step 8).any { shift -> abs((a shr shift and 0xFF) - (b shr shift and 0xFF)) > CHANNEL_TOLERANCE }
                if (differs) count++
            }
        }
        return count
    }

    private fun GameMode.toMatchMode(): MatchMode = when (this) {
        GameMode.SINGLE_PLAYER_VS_COMPUTER -> MatchMode.SINGLE
        GameMode.DUAL_PLAYER_VS_COMPUTER -> MatchMode.DUAL_VS_COMPUTER
        GameMode.PLAYER_VS_PLAYER -> MatchMode.DUAL
    }

    private companion object {
        const val RECORDINGS = "../game-server/testResources/recordings"
        const val REFERENCES = "testResources/reference-renders"
        const val OUTPUT = "build/reference-renders"
        const val SCREEN_WIDTH = 640
        const val SCREEN_HEIGHT = 480
        const val RENDERED_TICKS = 150L
        const val FRAME_NANOS = 10_000_000L
        const val SETTLE_FRAMES = 40
        const val SETTLE_SLEEP_MILLIS = 15L
        const val CHANNEL_TOLERANCE = 8
        const val MAX_DIFFERING_FRACTION = 0.002
    }
}

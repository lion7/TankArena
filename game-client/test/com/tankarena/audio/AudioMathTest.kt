package com.tankarena.audio

import com.tankarena.protocol.snapshot.GameEvent
import com.tankarena.protocol.snapshot.HudState
import com.tankarena.protocol.snapshot.PlayerView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Legacy create_sound_absolute() (src/hardware/sound.c): the distance to the player's vehicle sets
 * the volume (full nearer than 100, then (612 - d) / 2, not played at 32 or less), two players pan by
 * their distances, and the pitch is 900 + rnd(200).
 */
class AudioMathTest {
    private class Recorder : AudioBackend {
        val plays = mutableListOf<Triple<Int, Int, Int>>()
        override fun play(kind: SoundKind, volume: Int, pan: Int, pitch: Int) {
            plays += Triple(volume, pan, pitch)
        }
        override fun stopAll() = Unit
        override var masterVolume: Int = 255
        override fun dispose() = Unit
    }

    private fun view(playerId: Int, x: Int, y: Int, hasVehicle: Boolean = true, cameraX: Int = x) = PlayerView(
        playerId = playerId,
        controlledActorId = if (hasVehicle) 1L + playerId else null,
        cameraCenterX = cameraX,
        cameraCenterY = y,
        hud = HudState(),
        viewerX = x,
        viewerY = y,
    )

    private fun play(views: List<PlayerView>, x: Int, y: Int = 0): Triple<Int, Int, Int>? {
        val recorder = Recorder()
        AudioManager(recorder).processEvents(listOf(GameEvent.Sound(com.tankarena.protocol.snapshot.SoundKind.EXPLODE, x, y)), views)
        return recorder.plays.singleOrNull()
    }

    @Test
    fun `the volume follows the distance to the vehicle, not the camera`() {
        assertEquals(255, play(listOf(view(0, 0, 0, cameraX = 5000)), 50)?.first)
        assertEquals(255, play(listOf(view(0, 0, 0)), 100)?.first, "(612 - 100) / 2 = 256, at most 255")
        assertEquals(128, play(listOf(view(0, 0, 0)), 356)?.first)
        assertEquals(33, play(listOf(view(0, 0, 0)), 546)?.first)
    }

    @Test
    fun `quiet and far sounds are not played, nor without a vehicle`() {
        assertEquals(null, play(listOf(view(0, 0, 0)), 548), "volume 32 is not played")
        assertEquals(null, play(listOf(view(0, 0, 0)), 700))
        assertEquals(null, play(listOf(view(0, 0, 0, hasVehicle = false)), 10))
    }

    @Test
    fun `a single player hears every sound in the centre`() {
        for (x in listOf(-200, 0, 200)) assertEquals(128, play(listOf(view(0, 0, 0)), x)?.second)
    }

    @Test
    fun `two players pan by the difference of their distances`() {
        val players = listOf(view(0, 0, 0), view(1, 500, 0))
        assertEquals(128, play(players, 250)?.second, "equally far")
        // -(612 - 100) / 4 + (612 - 400) / 4 + 128 = 53
        assertEquals(53, play(players, 100)?.second)
        assertEquals(203, play(players, 400)?.second)
        // Distances count up to 612: player two far away pans fully to player one's side.
        assertEquals(0, play(listOf(view(0, 0, 0), view(1, 5000, 0)), 10)?.second)
    }

    @Test
    fun `the pitch is 900 to 1099`() {
        val recorder = Recorder()
        val manager = AudioManager(recorder)
        repeat(200) { manager.processEvents(listOf(GameEvent.Sound(com.tankarena.protocol.snapshot.SoundKind.EXPLODE, 0, 0)), listOf(view(0, 0, 0))) }
        assertTrue(recorder.plays.all { it.third in 900..1099 })
    }
}

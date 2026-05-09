package com.tankarena.audio

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for audio distance attenuation and panning calculations.
 * 
 * Verifies the formulas from docs/game/mechanics.md §"Sound System":
 * - Max range: 612 pixels
 * - Volume: full at d<100, linear falloff 100-612, silent at d>=612
 * - Pan: stereo panning based on relative player distances
 * - Pitch: 900-1100 range
 */
class AudioMathTest {

    @Test
    fun `volume is full at near field distance`() {
        val backend = StubAudioBackend()
        val manager = AudioManager(backend)
        
        // Use reflection to access private computeVolume method
        val volume = computeVolumeViaReflection(manager, 50f)
        assertEquals(255, volume, "Volume should be 255 at distance 50")
    }

    @Test
    fun `volume is full at exactly near field threshold`() {
        val backend = StubAudioBackend()
        val manager = AudioManager(backend)
        
        val volume = computeVolumeViaReflection(manager, 99f)
        assertEquals(255, volume, "Volume should be 255 at distance 99")
    }

    @Test
    fun `volume starts falloff at 100 pixels`() {
        val backend = StubAudioBackend()
        val manager = AudioManager(backend)
        
        val volume = computeVolumeViaReflection(manager, 100f)
        // (612 - 100) / 2 = 256, clamped to 255
        assertEquals(255, volume, "Volume should be 255 at distance 100")
    }

    @Test
    fun `volume falls off linearly at mid range`() {
        val backend = StubAudioBackend()
        val manager = AudioManager(backend)
        
        val volume = computeVolumeViaReflection(manager, 356f)
        // (612 - 356) / 2 = 128
        assertEquals(128, volume, "Volume should be 128 at distance 356")
    }

    @Test
    fun `volume is zero at max range`() {
        val backend = StubAudioBackend()
        val manager = AudioManager(backend)
        
        val volume = computeVolumeViaReflection(manager, 612f)
        assertEquals(0, volume, "Volume should be 0 at distance 612")
    }

    @Test
    fun `volume is zero beyond max range`() {
        val backend = StubAudioBackend()
        val manager = AudioManager(backend)
        
        val volume = computeVolumeViaReflection(manager, 700f)
        assertEquals(0, volume, "Volume should be 0 at distance 700")
    }

    @Test
    fun `pan is centered when equidistant from both players`() {
        val backend = StubAudioBackend()
        val manager = AudioManager(backend)
        
        val pan = computePanViaReflection(manager, 200f, 200f, 500, 500)
        // -(612 - 200) / 4 + (612 - 200) / 4 + 128 = 128
        assertEquals(128, pan, "Pan should be centered (128) when equidistant")
    }

    @Test
    fun `pan shifts left when closer to player 1`() {
        val backend = StubAudioBackend()
        val manager = AudioManager(backend)
        
        val pan = computePanViaReflection(manager, 100f, 400f, 500, 500)
        // -(612 - 100) / 4 + (612 - 400) / 4 + 128 = -128 + 53 + 128 = 53
        assertEquals(53, pan, "Pan should shift left when closer to player 1")
    }

    @Test
    fun `pan shifts right when closer to player 2`() {
        val backend = StubAudioBackend()
        val manager = AudioManager(backend)
        
        val pan = computePanViaReflection(manager, 400f, 100f, 500, 500)
        // -(612 - 400) / 4 + (612 - 100) / 4 + 128 = -53 + 128 + 128 = 203
        assertEquals(203, pan, "Pan should shift right when closer to player 2")
    }

    @Test
    fun `pan is clamped to valid range`() {
        val backend = StubAudioBackend()
        val manager = AudioManager(backend)
        
        // Extreme case that would produce pan < 0
        val panLeft = computePanViaReflection(manager, 0f, 612f, 500, 500)
        // -(612 - 0) / 4 + (612 - 612) / 4 + 128 = -153 + 0 + 128 = -25 -> clamped to 0
        assertEquals(0, panLeft, "Pan should be clamped to minimum 0")
        
        // Extreme case that would produce pan > 255
        val panRight = computePanViaReflection(manager, 612f, 0f, 500, 500)
        // -(612 - 612) / 4 + (612 - 0) / 4 + 128 = 0 + 153 + 128 = 281 -> clamped to 255
        assertEquals(255, panRight, "Pan should be clamped to maximum 255")
    }

    @Test
    fun `pitch is within valid range`() {
        val backend = StubAudioBackend()
        val manager = AudioManager(backend)
        
        // Test multiple times to verify range
        repeat(100) {
            val pitch = computePitchViaReflection(manager)
            assertTrue(pitch in 900..1100, "Pitch $pitch should be in range 900-1100")
        }
    }

    @Test
    fun `single player pan uses horizontal offset`() {
        val backend = StubAudioBackend()
        val manager = AudioManager(backend)
        
        // Sound directly in front of camera (same X)
        val panCenter = computePanViaReflection(manager, 200f, Float.POSITIVE_INFINITY, 500, 500)
        // offset = 0, normalized = 0, pan = (1 - 0) * 128 = 128
        assertEquals(128, panCenter, "Pan should be centered when sound is at camera X")
        
        // Sound to the left of camera
        val panLeft = computePanViaReflection(manager, 200f, Float.POSITIVE_INFINITY, 200, 500)
        // offset = -300, normalized = -300/612 = -0.49, pan = (1 - (-0.49)) * 128 = 191
        assertEquals(191, panLeft, "Pan should shift for left sound")
        
        // Sound to the right of camera
        val panRight = computePanViaReflection(manager, 200f, Float.POSITIVE_INFINITY, 800, 500)
        // offset = 300, normalized = 300/612 = 0.49, pan = (1 - 0.49) * 128 = 65
        assertEquals(65, panRight, "Pan should shift for right sound")
    }

    private fun computeVolumeViaReflection(manager: AudioManager, distance: Float): Int {
        val method = AudioManager::class.java.getDeclaredMethod("computeVolume", Float::class.javaPrimitiveType)
        method.isAccessible = true
        return method.invoke(manager, distance) as Int
    }

    private fun computePanViaReflection(
        manager: AudioManager,
        d1: Float,
        d2: Float,
        soundX: Int,
        cameraX: Int
    ): Int {
        val method = AudioManager::class.java.getDeclaredMethod(
            "computePan",
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )
        method.isAccessible = true
        return method.invoke(manager, d1, d2, soundX, cameraX) as Int
    }

    private fun computePitchViaReflection(manager: AudioManager): Int {
        val method = AudioManager::class.java.getDeclaredMethod("computePitch")
        method.isAccessible = true
        return method.invoke(manager) as Int
    }

    private fun assertTrue(condition: Boolean, message: String) {
        if (!condition) throw AssertionError(message)
    }
}

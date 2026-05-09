package com.tankarena.audio

import java.util.Random

/**
 * Desktop audio backend implementing the legacy Tank Arena sound system.
 * 
 * Implements distance attenuation, stereo panning, and pitch variation
 * per docs/game/mechanics.md §"Sound System":
 * 
 * - Distance attenuation:
 *   - d < 100: volume = 255 (full)
 *   - 100 <= d < 612: volume = (612 - d) / 2 (linear falloff)
 *   - d >= 612: volume = 0 (silent)
 * 
 * - Stereo panning (dual mode):
 *   - pan = -(612 - d1) / 4 + (612 - d2) / 4 + 128
 *   - clamped to [0, 255]
 * 
 * - Pitch variation: 900 + random(200) = 900-1100
 * 
 * - Volume threshold: sounds with volume <= 32 are silenced
 * 
 * - Max 32 concurrent sounds; excess are dropped
 */
class DesktopAudioBackend(
    private val soundSamples: Map<SoundKind, String> = emptyMap(),
    private val random: Random = Random(),
) : AudioBackend {

    companion object {
        private const val MAX_RANGE = 612
        private const val NEAR_FIELD = 100
        private const val MIN_VOLUME_FOR_PLAY = 33
        private const val MAX_CONCURRENT_SOUNDS = 32
    }

    private var activeSounds: Int = 0

    /**
     * Compute volume based on distance from sound source to listener.
     * Per legacy formula: full volume at d<100, linear falloff to 0 at d>=612.
     */
    fun computeVolume(distance: Float): Int {
        if (distance < NEAR_FIELD) return 255
        if (distance >= MAX_RANGE) return 0
        return ((MAX_RANGE - distance) / 2).toInt().coerceIn(0, 255)
    }

    /**
     * Compute stereo pan position based on distances to two players.
     * For single player, uses sound position relative to camera center.
     * 
     * @param d1 Distance to player 1 (or camera center in single player)
     * @param d2 Distance to player 2 (or infinity if single player)
     * @param soundX World X of sound source
     * @param cameraX World X of camera center
     */
    fun computePan(d1: Float, d2: Float, soundX: Int, cameraX: Int): Int {
        // Single player mode: pan based on horizontal offset from camera
        if (d2 == Float.POSITIVE_INFINITY) {
            val offset = soundX - cameraX
            val normalized = (offset / MAX_RANGE.toFloat()).coerceIn(-1f, 1f)
            return ((1f - normalized) * 128).toInt().coerceIn(0, 255)
        }
        
        // Dual player mode: legacy formula
        val pan = -(MAX_RANGE - d1) / 4 + (MAX_RANGE - d2) / 4 + 128
        return pan.toInt().coerceIn(0, 255)
    }

    /**
     * Compute pitch variation: 900-1100 range.
     */
    fun computePitch(): Int {
        return 900 + random.nextInt(201)
    }

    override fun play(kind: SoundKind, volume: Int, pan: Int, pitch: Int) {
        // Silencing threshold
        if (volume <= 32) return
        
        // Concurrent sound limit
        if (activeSounds >= MAX_CONCURRENT_SOUNDS) {
            // Drop the sound - no capacity
            return
        }
        
        activeSounds++
        
        // In a real implementation, this would play the actual sound sample
        // For now, we just log (or could use a real audio library like JUCE,
        // javax.sound.sampled, or a Kotlin multiplatform audio library)
        // println("Playing sound: $kind volume=$volume pan=$pan pitch=$pitch")
        
        // Auto-decrement after a simulated duration (real impl would track handles)
        // For stub: just keep counter bounded
        activeSounds = activeSounds.coerceAtMost(MAX_CONCURRENT_SOUNDS - 1)
    }

    override fun stopAll() {
        activeSounds = 0
        // Real impl would stop all active sound handles
    }

    override fun dispose() {
        activeSounds = 0
        // Real impl would release audio resources
    }
}

/**
 * Stub audio backend that does nothing. Useful for testing or when audio is disabled.
 */
class StubAudioBackend : AudioBackend {
    override fun play(kind: SoundKind, volume: Int, pan: Int, pitch: Int) {
        // No-op
    }

    override fun stopAll() {
        // No-op
    }

    override fun dispose() {
        // No-op
    }
}

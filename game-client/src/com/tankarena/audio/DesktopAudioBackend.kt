package com.tankarena.audio

import java.io.ByteArrayInputStream
import java.util.Random
import java.util.concurrent.atomic.AtomicInteger
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.FloatControl
import javax.sound.sampled.LineEvent
import kotlin.math.log10

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
 *
 * Samples are the legacy `DATA/SOUND.DAT` WAVs, bundled under `sounds/` on the classpath.
 * Playback uses `javax.sound.sampled` clips; when no audio device is available the backend
 * stays silent instead of failing.
 */
class DesktopAudioBackend(
    private val soundSamples: Map<SoundKind, String> = LEGACY_SAMPLES,
    private val random: Random = Random(),
) : AudioBackend {

    companion object {
        private const val MAX_RANGE = 612
        private const val NEAR_FIELD = 100
        private const val MIN_VOLUME_FOR_PLAY = 33
        private const val MAX_CONCURRENT_SOUNDS = 32

        /** Legacy SND_* sample for each sound kind (src/include/sound.h). */
        val LEGACY_SAMPLES: Map<SoundKind, String> = mapOf(
            SoundKind.MAIN to "main",
            SoundKind.CHAIN to "chain",
            SoundKind.FLAME to "flame",
            SoundKind.SROCKET to "srock",
            SoundKind.MORTAR to "mortar",
            SoundKind.ROCKET to "rocket",
            SoundKind.EMPTY to "empty",
            SoundKind.SMOKESCR to "smoke",
            SoundKind.INVISIBLE to "invis",
            SoundKind.SPEEDUP to "speedup",
            SoundKind.LIGHT to "light",
            SoundKind.EXPLODE to "expl",
            SoundKind.CRASH to "crash",
            SoundKind.SPLASH to "splash",
            SoundKind.MINE to "expl2",
            SoundKind.BONUS to "pickup",
            SoundKind.COLLAPSE to "collapse",
            SoundKind.NUKE to "nuke",
            SoundKind.SCREAM to "scream",
            SoundKind.INCOMING to "incoming",
        )
    }

    private class Sample(val format: AudioFormat, val frames: ByteArray)

    private val activeSounds = AtomicInteger(0)
    private val samples: Map<SoundKind, Sample> by lazy { loadSamples() }
    @Volatile private var available: Boolean = true

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
        if (volume < MIN_VOLUME_FOR_PLAY || !available) return
        val sample = samples[kind] ?: return
        // Concurrent sound limit: drop the sound when every voice is busy
        if (activeSounds.incrementAndGet() > MAX_CONCURRENT_SOUNDS) {
            activeSounds.decrementAndGet()
            return
        }
        val clip = runCatching { AudioSystem.getClip() }.getOrElse {
            available = false
            activeSounds.decrementAndGet()
            return
        }
        runCatching {
            // Legacy pitch 1000 = normal speed: resampling by pitch/1000 shifts the pitch.
            val format = sample.format.let {
                AudioFormat(it.encoding, it.sampleRate * pitch / 1000f, it.sampleSizeInBits, it.channels, it.frameSize, it.frameRate * pitch / 1000f, it.isBigEndian)
            }
            val frameCount = sample.frames.size.toLong() / format.frameSize
            clip.open(AudioInputStream(ByteArrayInputStream(sample.frames), format, frameCount))
            clip.gainControl()?.let { gain ->
                val db = 20f * log10((volume / 255f).coerceAtLeast(0.0001f))
                gain.value = db.coerceIn(gain.minimum, gain.maximum)
            }
            clip.floatControl(FloatControl.Type.PAN)?.let { it.value = ((128 - pan) / 128f).coerceIn(-1f, 1f) }
            clip.addLineListener { event ->
                if (event.type == LineEvent.Type.STOP) {
                    clip.close()
                    activeSounds.decrementAndGet()
                }
            }
            clip.start()
        }.onFailure {
            clip.close()
            activeSounds.decrementAndGet()
        }
    }

    override fun stopAll() {
        // Clips close themselves and release their voice when they stop.
    }

    override fun dispose() {
        available = false
    }

    private fun loadSamples(): Map<SoundKind, Sample> {
        val loader = Thread.currentThread().contextClassLoader ?: DesktopAudioBackend::class.java.classLoader
        return soundSamples.mapNotNull { (kind, name) ->
            val bytes = loader.getResourceAsStream("sounds/$name.wav")?.use { it.readBytes() } ?: return@mapNotNull null
            runCatching {
                AudioSystem.getAudioInputStream(ByteArrayInputStream(bytes)).use { stream ->
                    kind to Sample(stream.format, stream.readBytes())
                }
            }.getOrNull()
        }.toMap()
    }

    private fun Clip.gainControl(): FloatControl? = floatControl(FloatControl.Type.MASTER_GAIN)

    private fun Clip.floatControl(type: FloatControl.Type): FloatControl? =
        if (isControlSupported(type)) getControl(type) as FloatControl else null
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

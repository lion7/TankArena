package com.tankarena.audio

import java.io.ByteArrayInputStream
import java.util.Random
import java.util.concurrent.ConcurrentLinkedQueue
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

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
 * Playback mixes them in software onto one `javax.sound.sampled` line on its own thread, so
 * playing a sound never blocks the game; when no audio device is available the backend stays
 * silent instead of failing.
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
        private const val OUTPUT_RATE = 22050f
        /** About 12 ms per chunk, four chunks of buffer: sounds start within ~50 ms. */
        private const val CHUNK_FRAMES = 256
        private const val BUFFER_CHUNKS = 4
        /** Headroom so a few overlapping explosions do not clip at once. */
        private const val MASTER_GAIN = 0.7f

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
        mixer.start()
        // Never blocks: the mixer thread picks the voice up within one buffer.
        requests.add(Voice(kind, volume, pan, pitch))
    }

    override fun stopAll() {
        stopRequested = true
    }

    override fun dispose() {
        available = false
        mixer.stop()
    }

    /** A sound waiting to start, or playing at [position] (in source frames). */
    private class Voice(val kind: SoundKind, val volume: Int, val pan: Int, val pitch: Int) {
        var data: FloatArray = FloatArray(0)
        var position = 0.0
        var step = 1.0
        var left = 0f
        var right = 0f
    }

    private val requests = ConcurrentLinkedQueue<Voice>()
    @Volatile private var stopRequested = false

    /**
     * Legacy play_sample() through Allegro's mixer: one output line, opened once, on which up to
     * [MAX_CONCURRENT_SOUNDS] voices are mixed in software. Opening a line per sound (as a
     * `Clip` does) takes tens to hundreds of milliseconds on Linux.
     */
    private val mixer = object {
        private var thread: Thread? = null
        @Volatile private var running = false

        @Synchronized
        fun start() {
            if (thread != null) return
            running = true
            thread = Thread(::run, "audio-mixer").apply {
                isDaemon = true
                start()
            }
        }

        @Synchronized
        fun stop() {
            running = false
            thread?.join(500)
        }

        private fun run() {
            val format = AudioFormat(OUTPUT_RATE, 16, 2, true, false)
            val line = runCatching {
                AudioSystem.getSourceDataLine(format).apply {
                    open(format, CHUNK_FRAMES * 4 * BUFFER_CHUNKS)
                    start()
                }
            }.getOrElse {
                available = false
                return
            }
            val voices = ArrayList<Voice>(MAX_CONCURRENT_SOUNDS)
            val mix = FloatArray(CHUNK_FRAMES * 2)
            val out = ByteArray(CHUNK_FRAMES * 4)
            try {
                while (running) {
                    if (stopRequested) {
                        stopRequested = false
                        voices.clear()
                        requests.clear()
                    }
                    while (true) {
                        val voice = requests.poll() ?: break
                        // Concurrent sound limit: drop the sound when every voice is busy.
                        if (voices.size < MAX_CONCURRENT_SOUNDS && prepare(voice)) voices += voice
                    }
                    mixInto(voices, mix)
                    for (i in mix.indices) {
                        val sample = (mix[i] * MASTER_GAIN).coerceIn(-1f, 1f)
                        val value = (sample * Short.MAX_VALUE).toInt()
                        out[i * 2] = value.toByte()
                        out[i * 2 + 1] = (value shr 8).toByte()
                    }
                    // Blocks while the line's buffer is full, which paces the loop.
                    line.write(out, 0, out.size)
                }
            } finally {
                line.stop()
                line.close()
            }
        }
    }

    /** Looks up the sample and works out the voice's speed and stereo gains. */
    private fun prepare(voice: Voice): Boolean {
        val sample = samples[voice.kind] ?: return false
        voice.data = sample.data
        // Legacy pitch 1000 = normal speed: resampling by pitch/1000 shifts the pitch.
        voice.step = sample.rate * voice.pitch / 1000.0 / OUTPUT_RATE
        val volume = voice.volume / 255f
        // Allegro pan: 0 full left, 128 centre, 255 full right.
        voice.left = volume * ((255 - voice.pan) / 127.5f).coerceAtMost(1f)
        voice.right = volume * (voice.pan / 127.5f).coerceAtMost(1f)
        return true
    }

    /** Adds every voice's next chunk to [mix] (interleaved left/right) and drops finished ones. */
    private fun mixInto(voices: MutableList<Voice>, mix: FloatArray) {
        mix.fill(0f)
        val iterator = voices.iterator()
        while (iterator.hasNext()) {
            val voice = iterator.next()
            val data = voice.data
            var position = voice.position
            var frame = 0
            while (frame < CHUNK_FRAMES) {
                val index = position.toInt()
                if (index >= data.size) break
                val value = data[index]
                mix[frame * 2] += value * voice.left
                mix[frame * 2 + 1] += value * voice.right
                position += voice.step
                frame++
            }
            voice.position = position
            if (position.toInt() >= data.size) iterator.remove()
        }
    }

    /** A sample as floats in −1..1 at its own rate. */
    private class Sample(val rate: Float, val data: FloatArray)

    private fun loadSamples(): Map<SoundKind, Sample> {
        val loader = Thread.currentThread().contextClassLoader ?: DesktopAudioBackend::class.java.classLoader
        return soundSamples.mapNotNull { (kind, name) ->
            val bytes = loader.getResourceAsStream("sounds/$name.wav")?.use { it.readBytes() } ?: return@mapNotNull null
            runCatching {
                AudioSystem.getAudioInputStream(ByteArrayInputStream(bytes)).use { stream ->
                    kind to Sample(stream.format.sampleRate, toFloats(stream.format, stream.readBytes()))
                }
            }.getOrNull()
        }.toMap()
    }

    /** The legacy WAVs are 8-bit unsigned mono; 16-bit and signed samples are read too. */
    private fun toFloats(format: AudioFormat, bytes: ByteArray): FloatArray {
        val channels = format.channels.coerceAtLeast(1)
        return when (format.sampleSizeInBits) {
            8 -> {
                val unsigned = format.encoding == AudioFormat.Encoding.PCM_UNSIGNED
                FloatArray(bytes.size / channels) { i ->
                    val b = bytes[i * channels].toInt()
                    (if (unsigned) (b and 0xFF) - 128 else b) / 128f
                }
            }
            16 -> FloatArray(bytes.size / (2 * channels)) { i ->
                val at = i * 2 * channels
                val value = if (format.isBigEndian) {
                    (bytes[at].toInt() shl 8) or (bytes[at + 1].toInt() and 0xFF)
                } else {
                    (bytes[at + 1].toInt() shl 8) or (bytes[at].toInt() and 0xFF)
                }
                value.toShort() / 32768f
            }
            else -> FloatArray(0)
        }
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

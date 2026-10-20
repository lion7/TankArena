package nl.tankarena.app.android

import android.app.Activity
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.ui.input.key.Key
import nl.tankarena.app.GamePlatform
import nl.tankarena.app.Music
import nl.tankarena.audio.AudioBackend
import nl.tankarena.audio.MixerAudioBackend
import nl.tankarena.audio.PcmOutput
import nl.tankarena.input.PadReading
import java.io.File
import kotlin.math.roundToInt

/** Android's services for the game (ADR 0025): AudioTrack, MediaPlayer, the pads. */
class AndroidPlatform(private val activity: Activity) : GamePlatform {
    val pads = AndroidPads()

    override fun audioBackend(): AudioBackend = MixerAudioBackend(::openAudioTrack)

    override val music = AndroidMusic(activity)

    override fun gamepads(): List<PadReading> = pads.readings()

    /** Android turns pad buttons into keys itself (see MainActivity). */
    override val postKey: ((Key) -> Unit)? = null

    override val deviceName: String = android.os.Build.MODEL ?: "Android"

    override val touchScreen: Boolean = activity.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN)

    override fun quit() = activity.finishAndRemoveTask()
}

/** The mixer's output on an AudioTrack: 16-bit stereo, streamed; write blocks while it is full. */
private fun openAudioTrack(rate: Int, bufferBytes: Int): PcmOutput {
    val minimum = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
    val track = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
        .setBufferSizeInBytes(maxOf(minimum, bufferBytes))
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()
    track.play()
    return object : PcmOutput {
        override fun write(bytes: ByteArray) {
            track.write(bytes, 0, bytes.size)
        }

        override fun close() {
            track.stop()
            track.release()
        }
    }
}

/** The game's MIDI tunes through MediaPlayer (Android plays `.mid` files itself). */
class AndroidMusic(private val activity: Activity) : Music {
    private var player: MediaPlayer? = null
    private var current: String? = null

    override var volume: Int = 255
        set(value) {
            field = value.coerceIn(0, 255)
            player?.setVolume(field / 255f, field / 255f)
        }

    @Synchronized
    override fun play(tune: String) {
        if (tune.equals(current, ignoreCase = true)) return
        runCatching {
            // MediaPlayer plays files, not class-path resources: the tune is copied to the cache once.
            val file = File(activity.cacheDir, tune)
            if (!file.isFile) {
                val bytes = javaClass.classLoader?.getResourceAsStream("music/$tune")?.use { it.readBytes() } ?: return
                file.writeBytes(bytes)
            }
            player?.release()
            player = MediaPlayer().apply {
                setDataSource(file.path)
                isLooping = true
                prepare()
                setVolume(volume / 255f, volume / 255f)
                start()
            }
            current = tune
        }.onFailure { android.util.Log.w("TankArena", "Cannot play $tune", it) }
    }

    fun pause() {
        player?.takeIf { it.isPlaying }?.pause()
    }

    fun resume() {
        player?.start()
    }

    @Synchronized
    override fun dispose() {
        player?.release()
        player = null
        current = null
    }
}

/**
 * The gamepads as the desktop's Jamepad reports them ([PadReading]): Android gives their buttons
 * as key events and their sticks and D-pad as motion events; pads are numbered in the order they
 * were first used.
 */
class AndroidPads {
    private class Pad(var stickX: Int = 0, var stickY: Int = 0, var hatX: Int = 0, var hatY: Int = 0, var buttons: Int = 0)

    private val pads = LinkedHashMap<Int, Pad>()

    @Synchronized
    fun readings(): List<PadReading> = pads.values.map { pad ->
        PadReading(
            stickX = if (pad.hatX != 0) pad.hatX * 100 else pad.stickX,
            stickY = if (pad.hatY != 0) pad.hatY * 100 else pad.stickY,
            buttons = pad.buttons,
        )
    }

    @Synchronized
    fun onKey(event: KeyEvent) {
        if (!event.isFromSource(InputDevice.SOURCE_GAMEPAD) && !event.isFromSource(InputDevice.SOURCE_JOYSTICK)) return
        val bit = when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> 0
            KeyEvent.KEYCODE_BUTTON_B -> 1
            KeyEvent.KEYCODE_BUTTON_X -> 2
            KeyEvent.KEYCODE_BUTTON_Y -> 3
            KeyEvent.KEYCODE_BUTTON_L1 -> 4
            KeyEvent.KEYCODE_BUTTON_R1 -> 5
            KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BACK -> 6
            KeyEvent.KEYCODE_BUTTON_START -> 7
            else -> return
        }
        val pad = pads.getOrPut(event.deviceId) { Pad() }
        pad.buttons = if (event.action == KeyEvent.ACTION_DOWN) pad.buttons or (1 shl bit) else pad.buttons and (1 shl bit).inv()
    }

    @Synchronized
    fun onMotion(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_MOVE) return false
        val pad = pads.getOrPut(event.deviceId) { Pad() }
        pad.stickX = (event.getAxisValue(MotionEvent.AXIS_X) * 100).roundToInt().coerceIn(-100, 100)
        pad.stickY = (event.getAxisValue(MotionEvent.AXIS_Y) * 100).roundToInt().coerceIn(-100, 100)
        pad.hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X).roundToInt()
        pad.hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y).roundToInt()
        return true
    }
}

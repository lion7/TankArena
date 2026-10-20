package nl.tankarena.audio

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

/** The desktop's sound: the game's mixer on one `javax.sound.sampled` line. */
fun desktopAudioBackend(): AudioBackend = MixerAudioBackend(::openJavaSoundOutput)

private fun openJavaSoundOutput(rate: Int, bufferBytes: Int): PcmOutput {
    val format = AudioFormat(rate.toFloat(), 16, 2, true, false)
    val line = AudioSystem.getSourceDataLine(format).apply {
        open(format, bufferBytes)
        start()
    }
    return object : PcmOutput {
        override fun write(bytes: ByteArray) {
            line.write(bytes, 0, bytes.size)
        }

        override fun close() {
            line.stop()
            line.close()
        }
    }
}

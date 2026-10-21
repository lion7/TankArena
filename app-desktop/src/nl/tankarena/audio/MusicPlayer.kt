package nl.tankarena.audio

import nl.tankarena.app.Music
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequencer
import javax.sound.midi.SysexMessage
import javax.sound.midi.Synthesizer

/**
 * Legacy init_player() and Allegro's MIDI volume (original/src/hardware/sound.c): one tune at a time,
 * looping, played by Java's software synthesizer. Without a MIDI device it stays silent.
 */
class MusicPlayer : Music {
    private var sequencer: Sequencer? = null
    private var synthesizer: Synthesizer? = null
    private var current: String? = null

    /** Allegro set_volume(-1, music): 0..255. */
    override var volume: Int = 255
        set(value) {
            field = value.coerceIn(0, 255)
            applyVolume()
        }

    /** Legacy init_player(): starts [tune] (a file in `music/`) unless it is already playing. */
    @Synchronized
    override fun play(tune: String) {
        if (tune.equals(current, ignoreCase = true)) return
        runCatching {
            val bytes = javaClass.classLoader.getResourceAsStream("music/$tune")?.use { it.readBytes() } ?: return
            val sequencer = sequencer ?: open() ?: return
            sequencer.stop()
            sequencer.sequence = MidiSystem.getSequence(bytes.inputStream())
            sequencer.loopCount = Sequencer.LOOP_CONTINUOUSLY
            sequencer.tickPosition = 0
            sequencer.start()
            current = tune
            applyVolume()
        }.onFailure { System.err.println("Cannot play $tune: ${it.message}") }
    }

    @Synchronized
    override fun dispose() {
        sequencer?.close()
        synthesizer?.close()
        sequencer = null
        synthesizer = null
        current = null
    }

    private fun open(): Sequencer? {
        val synthesizer = MidiSystem.getSynthesizer().apply { open() }
        val sequencer = MidiSystem.getSequencer(false).apply { open() }
        sequencer.transmitter.receiver = synthesizer.receiver
        this.synthesizer = synthesizer
        this.sequencer = sequencer
        return sequencer
    }

    /** The General MIDI master volume message, which the tunes' own channel volumes do not undo. */
    private fun applyVolume() {
        val receiver = synthesizer?.receiver ?: return
        val level = volume * 16383 / 255
        val data = byteArrayOf(
            0xF0.toByte(), 0x7F, 0x7F, 0x04, 0x01, (level and 0x7F).toByte(), (level shr 7).toByte(), 0xF7.toByte(),
        )
        runCatching { receiver.send(SysexMessage(data, data.size), -1) }
    }

    companion object {
        /** Legacy load_map(): `map.background == 3` (the night world) plays BELVED.MID, the rest TUNE.MID. */
        const val TUNE = "TUNE.MID"
        const val NIGHT_TUNE = "BELVED.MID"
    }
}

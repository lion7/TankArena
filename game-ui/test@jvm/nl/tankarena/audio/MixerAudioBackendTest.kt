package nl.tankarena.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MixerAudioBackendTest {
    @Test
    fun `every legacy sound loads from its WAV file`() {
        val rates = MixerAudioBackend({ _, _ -> null }).loadedRates()
        assertEquals(SoundKind.entries.toSet(), rates.keys)
        assertTrue(rates.values.all { it in 8000f..44100f }, "$rates")
    }
}

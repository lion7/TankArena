package nl.tankarena.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VersusScoresTest {
    @Test
    fun `rounds and matches are counted per map and rounds over every map`() {
        val scores = VersusScores()
        scores.round("flagisle", 0)
        scores.round("FLAGISLE", 0)
        scores.matchEnded("flagisle", 0)
        scores.round("hockey", 1)
        scores.matchEnded("hockey", null)
        assertEquals(MapScore(played = 1, matchWins = listOf(1, 0), roundWins = listOf(2, 0)), scores.of("FlagIsle"))
        assertEquals(MapScore(played = 1, matchWins = listOf(0, 0), roundWins = listOf(0, 1)), scores.of("hockey"))
        assertEquals(listOf(2, 1), scores.rounds)
        assertEquals("played 1× · matches 1–0 · rounds 2–0", scores.of("flagisle")!!.text())
        scores.clear()
        assertNull(scores.of("flagisle"))
        assertEquals(listOf(0, 0), scores.rounds)
    }
}

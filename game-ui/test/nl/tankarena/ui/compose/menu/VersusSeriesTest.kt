package nl.tankarena.ui.compose.menu

import nl.tankarena.sim.Ruleset
import nl.tankarena.sim.map.ShippedMaps
import nl.tankarena.ui.compose.DualMatch
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class VersusSeriesTest {
    private val mission = MissionEntry.of("flagbatl", ShippedMaps.load("flagbatl")!!.document)

    private fun gate(ruleset: Ruleset) = MissionGate(
        PasswordStore(Files.createTempDirectory("tankarena-series").resolve("passwd.json").toFile()),
        GameSettings().also { it.update(it.options.copy(ruleset = ruleset)) },
    )

    @Test
    fun `Extended counts the rounds won over every map until the players join anew`() {
        val gate = gate(Ruleset.EXTENDED)
        var match = DualMatch(mission, seriesBefore = gate.versusSeries)
        for (winner in listOf(1, 1, 0)) {
            gate.roundEnded(match, winner)
            match = match.afterRound(winner)
        }
        assertEquals(listOf(1, 2), gate.versusSeries)
        // The next map starts its own match, the losers tank still sees the series.
        val next = DualMatch(mission, seriesBefore = gate.versusSeries).afterRound(1)
        assertEquals(listOf(0, 1), next.wins)
        assertEquals(listOf(1, 3), next.seriesWins)
        gate.newSeries()
        assertEquals(listOf(0, 0), gate.versusSeries)
    }

    @Test
    fun `Classic matches stand alone`() {
        val gate = gate(Ruleset.CLASSIC)
        gate.roundEnded(DualMatch(mission), 1)
        assertEquals(listOf(0, 0), gate.versusSeries)
    }
}

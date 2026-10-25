package nl.tankarena.ui.compose.menu

import nl.tankarena.sim.Ruleset
import nl.tankarena.sim.map.ShippedMaps
import nl.tankarena.ui.compose.DualMatch
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import nl.tankarena.ui.compose.GameMode
import nl.tankarena.ui.compose.MissionOutcome

class VersusSeriesTest {
    private val mission = MissionEntry.of("flagbatl", ShippedMaps.load("flagbatl")!!.document)

    private val dir = Files.createTempDirectory("tankarena-series")

    private fun gate(ruleset: Ruleset) = MissionGate(
        PasswordStore(dir.resolve("passwd.json").toFile()),
        GameSettings().also { it.update(it.options.copy(ruleset = ruleset)) },
        Ruleset.entries.associateWith { MissionStatsStore(dir.resolve("mission-stats-$it.json").toFile()) },
    )

    @Test
    fun `Extended counts the rounds won over every map until the players join anew`() {
        val gate = gate(Ruleset.EXTENDED)
        var match = DualMatch(mission, seriesBefore = gate.versusSeries)
        for (winner in listOf(1, 0, 1)) {
            gate.roundEnded(match, winner)
            match = match.afterRound(winner)
        }
        assertEquals(listOf(1, 2), gate.versusSeries)
        // Best of 3: 1–2 decides the match for team 2.
        assertEquals(nl.tankarena.sim.MapScore(played = 1, matchWins = listOf(0, 1), roundWins = listOf(1, 2)), gate.versusScores.of("flagbatl"))
        // The next map starts its own match, the losers tank still sees the series.
        val next = DualMatch(mission, seriesBefore = gate.versusSeries).afterRound(1)
        assertEquals(listOf(0, 1), next.wins)
        assertEquals(listOf(1, 3), next.seriesWins)
        gate.newSeries()
        assertEquals(listOf(0, 0), gate.versusSeries)
    }

    @Test
    fun `a match left after a round counts as played, one left before as nothing`() {
        val gate = gate(Ruleset.EXTENDED)
        gate.roundEnded(DualMatch(mission), null)
        assertNull(gate.versusScores.of("flagbatl"))
        val match = DualMatch(mission)
        gate.roundEnded(match, 0)
        gate.roundEnded(match.afterRound(0), null)
        assertEquals(nl.tankarena.sim.MapScore(played = 1, matchWins = listOf(0, 0), roundWins = listOf(1, 0)), gate.versusScores.of("flagbatl"))
    }

    @Test
    fun `campaign missions are recorded, also when left`() {
        val gate = gate(Ruleset.EXTENDED)
        gate.missionEnded(mission, GameMode.SINGLE_PLAYER_VS_COMPUTER, null)
        gate.missionEnded(mission, GameMode.PLAYER_VS_PLAYER, MissionOutcome.WON)
        assertEquals(MissionRecord(played = 1), gate.stats.of(GameMode.SINGLE_PLAYER_VS_COMPUTER, "flagbatl"))
        assertNull(gate.stats.of(GameMode.PLAYER_VS_PLAYER, "flagbatl"))
    }

    @Test
    fun `Classic matches stand alone`() {
        val gate = gate(Ruleset.CLASSIC)
        gate.roundEnded(DualMatch(mission), 1)
        assertEquals(listOf(0, 0), gate.versusSeries)
    }
}

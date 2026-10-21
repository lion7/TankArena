package nl.tankarena.ui.compose.menu

import nl.tankarena.content.MapSceneSidecar
import nl.tankarena.content.MapMetadata
import nl.tankarena.ui.compose.DualMatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Legacy select_tanks() (original/src/game/seltank.c) and dual_game()'s rounds (original/src/game/dual.c). */
class TankSelectTest {

    @Test
    fun `a map allows the vehicles it has room for`() {
        // FLAGISLE: tanks, helicopters and flags (1 + 2 + 16).
        val allowed = allowedTanks(19, listOf(0, 0))
        assertEquals(listOf(true, true, true, true, false, true, false, false), allowed)
        // A race map only allows the race tank.
        assertEquals(listOf(false, false, false, false, false, false, true, false), allowedTanks(8, listOf(0, 0)))
    }

    @Test
    fun `a vehicle the map does not allow is swapped for the first one it does`() {
        val raceOnly = allowedTanks(8, listOf(0, 0))
        assertEquals(listOf(6, 6), autoSelect(listOf(0, 5), raceOnly))
        val flyers = allowedTanks(4, listOf(0, 0))
        assertEquals(listOf(4, 4), autoSelect(listOf(4, 1), flyers))
    }

    @Test
    fun `a match of three rounds is decided once a player has won two`() {
        val mission = MissionEntry("FLAGISLE", "flagisle", "", MapSceneSidecar(metadata = MapMetadata("FLAGISLE", 10, 10, "FLAGISLE")), testMap())
        var match = DualMatch(mission)
        match = match.afterRound(winner = 1)
        assertEquals(2, match.round)
        assertEquals(listOf(0, 1), match.wins)
        assertFalse(match.decided)
        match = match.afterRound(winner = 1)
        assertTrue(match.decided)
    }
}

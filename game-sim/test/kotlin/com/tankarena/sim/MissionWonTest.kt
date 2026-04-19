package com.tankarena.sim

import com.tankarena.content.AuthoredObject
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.ObjectKinds
import com.tankarena.input.PlayerIntentFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MissionWonTest {
    @Test
    fun `collecting two goals worth 60 each emits MissionWon exactly once`() {
        val playerStart = LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2 to LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2
        val goalA = 4 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2 to LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2
        val goalB = 8 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2 to LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2
        val map = TestMaps.empty(widthTiles = 12, heightTiles = 3).copy(
            objects = listOf(
                AuthoredObject(
                    id = "p",
                    kind = ObjectKinds.PLAYER_START,
                    x = playerStart.first,
                    y = playerStart.second,
                ),
                goalAt("g1", goalA, contribution = 60),
                goalAt("g2", goalB, contribution = 60),
            ),
        )
        val sim = SimulationFactory.fromCanonicalMap(map)

        var missionWonCount = 0
        repeat(400) {
            val result = sim.tick(mapOf(0 to PlayerIntentFrame(steer = 1)))
            missionWonCount += result.events.count { it == SimulationEvent.MissionWon }
        }

        assertEquals(1, missionWonCount, "expected MissionWon to be emitted exactly once")
        val state = sim.currentState()
        assertEquals(MissionStatus.WON, state.mission.status)
        assertTrue(state.mission.goalGood >= 100)
    }

    private fun goalAt(id: String, pos: Pair<Int, Int>, contribution: Int): AuthoredObject =
        AuthoredObject(
            id = id,
            kind = ObjectKinds.GOAL,
            x = pos.first,
            y = pos.second,
            properties = mapOf(
                "radius" to LEGACY_TILE_SIZE.toString(),
                "who" to "0",
                "goalContribution" to contribution.toString(),
            ),
        )
}

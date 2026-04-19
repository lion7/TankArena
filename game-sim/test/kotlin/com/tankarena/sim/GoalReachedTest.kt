package com.tankarena.sim

import com.tankarena.content.AuthoredObject
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.ObjectKinds
import com.tankarena.input.PlayerIntentFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GoalReachedTest {
    @Test
    fun `driving onto a goal emits GoalReached once and bumps mission counter`() {
        val playerStart = LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2 to LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2
        val goalPos = 4 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2 to LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2
        val map = TestMaps.empty(widthTiles = 10, heightTiles = 3).copy(
            objects = listOf(
                AuthoredObject(
                    id = "p",
                    kind = ObjectKinds.PLAYER_START,
                    x = playerStart.first,
                    y = playerStart.second,
                ),
                AuthoredObject(
                    id = "g",
                    kind = ObjectKinds.GOAL,
                    x = goalPos.first,
                    y = goalPos.second,
                    properties = mapOf(
                        "radius" to LEGACY_TILE_SIZE.toString(),
                        "who" to "0",
                        "goalContribution" to "60",
                    ),
                ),
            ),
        )
        val sim = SimulationFactory.fromCanonicalMap(map)

        // Drive right toward the goal until we either reach it or run out of ticks.
        var reachedEvents = 0
        repeat(200) {
            val result = sim.tick(mapOf(0 to PlayerIntentFrame(steer = 1)))
            reachedEvents += result.events.count { it is SimulationEvent.GoalReached }
        }

        assertEquals(1, reachedEvents, "expected exactly one GoalReached event")
        val state = sim.currentState()
        assertEquals(60, state.mission.goalGood)
        assertTrue(state.goals.single().isClaimed)
    }
}

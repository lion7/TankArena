package com.tankarena.sim

import com.tankarena.content.AuthoredObject
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.ObjectKinds
import com.tankarena.input.PlayerIntentFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MissionLostTest {
    @Test
    fun `losing all lives emits MissionLost and flips status`() {
        // Single player tank with one life sitting in front of a quick-firing turret.
        // No mobile enemies, so "all player tanks permanently dead" reduces to player 0.
        val playerStart = LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2 to LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2
        val turretPos = 4 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2 to LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2
        val map = TestMaps.empty(widthTiles = 8, heightTiles = 3).copy(
            objects = listOf(
                AuthoredObject(
                    id = "p0",
                    kind = ObjectKinds.PLAYER_START,
                    x = playerStart.first,
                    y = playerStart.second,
                    properties = mapOf("lives" to "1", "direction" to "4"),
                ),
                AuthoredObject(
                    id = "t1",
                    kind = ObjectKinds.TURRET,
                    x = turretPos.first,
                    y = turretPos.second,
                    properties = mapOf(
                        "delay" to "8",
                        "power" to "60",
                        "radius" to "8",
                        "direction" to "12",
                    ),
                ),
            ),
        )
        val sim = SimulationFactory.fromCanonicalMap(map)

        var missionLostCount = 0
        for (tick in 0 until 600) {
            val result = sim.tick(emptyMap())
            missionLostCount += result.events.count { it == SimulationEvent.MissionLost }
            if (result.events.any { it == SimulationEvent.MissionLost }) break
        }

        assertEquals(1, missionLostCount, "expected MissionLost emitted exactly once")
        val state = sim.currentState()
        assertEquals(MissionStatus.LOST, state.mission.status)
        val player = state.tanks.first { it.playerIndex == 0 }
        assertTrue(!player.isAlive && player.lives <= 0, "player must be permanently dead")
    }
}

package com.tankarena.sim

import com.tankarena.content.AuthoredObject
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.ObjectKinds
import com.tankarena.input.PlayerIntentFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LivesLifecycleTest {
    @Test
    fun `tank with two lives respawns once then stays permanently dead`() {
        val playerStart = LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2 to LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2
        val enemyStart = 5 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2 to LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2
        val map = TestMaps.empty(widthTiles = 8, heightTiles = 3).copy(
            objects = listOf(
                AuthoredObject(
                    id = "p0",
                    kind = ObjectKinds.PLAYER_START,
                    x = playerStart.first,
                    y = playerStart.second,
                    properties = mapOf("lives" to "2", "direction" to "4"),
                ),
                AuthoredObject(
                    id = "p1",
                    kind = ObjectKinds.PLAYER_START,
                    x = enemyStart.first,
                    y = enemyStart.second,
                    properties = mapOf("lives" to "9", "direction" to "12"),
                ),
            ),
        )
        val sim = SimulationFactory.fromCanonicalMap(map)

        var deathCount = 0
        var respawnCount = 0
        for (tick in 0 until 1200) {
            val result = sim.tick(
                mapOf(
                    1 to PlayerIntentFrame(firePrimary = tick % 6 == 0),
                ),
            )
            deathCount += result.events.count {
                it is SimulationEvent.TankDestroyed && it.tankId == playerTankId(sim)
            }
            respawnCount += result.events.count {
                it is SimulationEvent.TankRespawned && it.tankId == playerTankId(sim)
            }
            val player = sim.currentState().tanks.first { it.playerIndex == 0 }
            if (!player.isAlive && player.lives <= 0) break
        }

        assertEquals(2, deathCount, "expected exactly two TankDestroyed events for player 0")
        assertEquals(1, respawnCount, "expected exactly one respawn between the two deaths")

        val player = sim.currentState().tanks.first { it.playerIndex == 0 }
        assertFalse(player.isAlive)
        assertEquals(0, player.lives)
        assertTrue(player.respawnInTicks < 0, "permanently-dead tank should have sentinel respawnInTicks")
    }

    private fun playerTankId(sim: TankArenaSimulation): Long =
        sim.currentState().tanks.first { it.playerIndex == 0 }.id
}

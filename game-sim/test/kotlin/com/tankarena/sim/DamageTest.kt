package com.tankarena.sim

import com.tankarena.content.AuthoredObject
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.ObjectKinds
import com.tankarena.input.PlayerIntentFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DamageTest {
    @Test
    fun `projectile from one tank reduces armor of another and emits TankHit`() {
        val map = TestMaps.empty(widthTiles = 8, heightTiles = 3).copy(
            objects = listOf(
                AuthoredObject(
                    id = "a",
                    kind = ObjectKinds.PLAYER_START,
                    x = LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    y = LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                ),
                AuthoredObject(
                    id = "b",
                    kind = ObjectKinds.PLAYER_START,
                    x = 5 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    y = LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                ),
            ),
        )
        val sim = SimulationFactory.fromCanonicalMap(map)

        // Player 0 fires right toward player 1.
        sim.tick(mapOf(0 to PlayerIntentFrame(aimX = 1, firePrimary = true)))

        var sawHit = false
        repeat(40) {
            val result = sim.tick(emptyMap())
            if (result.events.any { it is SimulationEvent.TankHit }) sawHit = true
        }

        assertTrue(sawHit, "expected TankHit event")
        val target = sim.currentState().tanks.first { it.playerIndex == 1 }
        assertTrue(target.armor < 100, "target armor should be reduced, was ${target.armor}")
    }

    @Test
    fun `tank dying emits TankDestroyed and respawns at start point`() {
        val map = TestMaps.empty(widthTiles = 8, heightTiles = 3).copy(
            objects = listOf(
                AuthoredObject(
                    id = "a",
                    kind = ObjectKinds.PLAYER_START,
                    x = LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    y = LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                ),
                AuthoredObject(
                    id = "b",
                    kind = ObjectKinds.PLAYER_START,
                    x = 5 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    y = LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                ),
            ),
        )
        val sim = SimulationFactory.fromCanonicalMap(map)
        val targetSpawn = sim.currentState().tanks.first { it.playerIndex == 1 }.position

        var destroyedSeen = false
        var respawnSeen = false
        // Fire until destroyed, then stop firing and wait for respawn.
        for (tick in 0 until 400) {
            val firing = !destroyedSeen && tick % 12 == 0
            val result = sim.tick(mapOf(0 to PlayerIntentFrame(aimX = 1, firePrimary = firing)))
            if (result.events.any { it is SimulationEvent.TankDestroyed }) destroyedSeen = true
            if (result.events.any { it is SimulationEvent.TankRespawned }) {
                respawnSeen = true
                break
            }
        }

        assertTrue(destroyedSeen, "expected TankDestroyed event")
        assertTrue(respawnSeen, "expected TankRespawned event after respawn timer")

        val target = sim.currentState().tanks.first { it.playerIndex == 1 }
        assertTrue(target.isAlive)
        assertEquals(100, target.armor)
        assertEquals(targetSpawn, target.position)
    }
}

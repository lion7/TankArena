package com.tankarena.sim

import com.tankarena.content.AuthoredObject
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.ObjectKinds
import com.tankarena.input.PlayerIntentFrame
import kotlin.test.Test
import kotlin.test.assertTrue

class ProjectileCollisionTest {
    @Test
    fun `projectile fired into wall explodes and is removed`() {
        // Wall at column 3, tank at column 1 firing right.
        val map = TestMaps.verticalWall(widthTiles = 5, heightTiles = 3, wallX = 3)
            .copy(
                objects = listOf(
                    AuthoredObject(
                        id = "p1",
                        kind = ObjectKinds.PLAYER_START,
                        x = LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                        y = LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                        properties = mapOf("direction" to "4"),
                    ),
                ),
            )
        val sim = SimulationFactory.fromCanonicalMap(map)

        sim.tick(mapOf(0 to PlayerIntentFrame(firePrimary = true)))
        var sawExplosion = false
        repeat(30) {
            val result = sim.tick(emptyMap())
            if (result.events.any { it is SimulationEvent.Explosion }) sawExplosion = true
        }

        assertTrue(sawExplosion, "expected explosion event when projectile hits wall")
        assertTrue(sim.currentState().projectiles.isEmpty(), "wall-bound projectile should be cleaned up")
    }
}

package com.tankarena.sim

import com.tankarena.content.AuthoredObject
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.ObjectKinds
import com.tankarena.input.PlayerIntentFrame
import kotlin.test.Test
import kotlin.test.assertTrue

class TurretFireTest {
    @Test
    fun `stationary turret with player in range fires within delay`() {
        val map = TestMaps.empty(widthTiles = 10, heightTiles = 5).copy(
            objects = listOf(
                AuthoredObject(
                    id = "p1",
                    kind = ObjectKinds.PLAYER_START,
                    x = 2 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    y = 2 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                ),
                AuthoredObject(
                    id = "t1",
                    kind = ObjectKinds.TURRET,
                    x = 6 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    y = 2 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    properties = mapOf(
                        "delay" to "10",
                        "power" to "10",
                        "radius" to "10",
                    ),
                ),
            ),
        )
        val sim = SimulationFactory.fromCanonicalMap(map)

        var fired = false
        repeat(200) {
            val result = sim.tick(mapOf(0 to PlayerIntentFrame()))
            if (result.events.any {
                    it is SimulationEvent.FireProjectile && it.ownerKind == ProjectileOwnerKind.TURRET
                }) {
                fired = true
            }
        }

        assertTrue(fired, "turret with player in range should fire at least once")
    }

    @Test
    fun `turret with player out of range does not fire`() {
        val map = TestMaps.empty(widthTiles = 30, heightTiles = 5).copy(
            objects = listOf(
                AuthoredObject(
                    id = "p1",
                    kind = ObjectKinds.PLAYER_START,
                    x = LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    y = LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                ),
                AuthoredObject(
                    id = "t1",
                    kind = ObjectKinds.TURRET,
                    x = 28 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    y = LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    properties = mapOf(
                        "delay" to "10",
                        "power" to "10",
                        "radius" to "3",
                    ),
                ),
            ),
        )
        val sim = SimulationFactory.fromCanonicalMap(map)

        var fired = false
        repeat(200) {
            val result = sim.tick(mapOf(0 to PlayerIntentFrame()))
            if (result.events.any {
                    it is SimulationEvent.FireProjectile && it.ownerKind == ProjectileOwnerKind.TURRET
                }) {
                fired = true
            }
        }

        assertTrue(!fired, "turret with player out of range should not fire")
    }
}

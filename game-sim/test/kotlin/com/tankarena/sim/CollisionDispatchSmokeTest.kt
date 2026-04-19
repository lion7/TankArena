package com.tankarena.sim

import com.tankarena.content.AuthoredObject
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.ObjectKinds
import com.tankarena.input.PlayerIntentFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Smoke coverage for the Kubriko-style [com.tankarena.sim.runtime.CollisionDispatch]
 * loop: tank-vs-wall stop, tank-vs-tank mutual push, projectile owner filter,
 * goal claim idempotency, and projectile-vs-wall removal + Explosion event.
 */
class CollisionDispatchSmokeTest {

    @Test
    fun `tank cannot push through a single wall and slides along it on Y`() {
        val map = TestMaps.verticalWall(widthTiles = 5, heightTiles = 5, wallX = 3).copy(
            objects = listOf(
                AuthoredObject(
                    id = "p1",
                    kind = ObjectKinds.PLAYER_START,
                    x = 1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    y = 2 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                ),
            ),
        )
        val sim = SimulationFactory.fromCanonicalMap(map)

        repeat(40) { sim.tick(mapOf(0 to PlayerIntentFrame(steer = 1, throttle = 1))) }
        val tank = sim.currentState().tanks.single()

        assertTrue(
            tank.position.x < 3 * LEGACY_TILE_SIZE,
            "tank x=${tank.position.x} should not have crossed wall left edge ${3 * LEGACY_TILE_SIZE}",
        )
        assertTrue(
            tank.position.y > 2 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
            "tank should still slide along the y axis",
        )
    }

    @Test
    fun `two tanks driving into each other separate via mutual push`() {
        val map = TestMaps.empty(widthTiles = 8, heightTiles = 3).copy(
            objects = listOf(
                AuthoredObject(
                    id = "a",
                    kind = ObjectKinds.PLAYER_START,
                    x = 2 * LEGACY_TILE_SIZE,
                    y = 1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                ),
                AuthoredObject(
                    id = "b",
                    kind = ObjectKinds.PLAYER_START,
                    x = 2 * LEGACY_TILE_SIZE + 24,
                    y = 1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                ),
            ),
        )
        val sim = SimulationFactory.fromCanonicalMap(map)

        repeat(20) {
            sim.tick(
                mapOf(
                    0 to PlayerIntentFrame(steer = 1),
                    1 to PlayerIntentFrame(steer = -1),
                ),
            )
        }
        val tanks = sim.currentState().tanks.sortedBy { it.playerIndex }
        val gap = tanks[1].position.x - tanks[0].position.x
        // Tanks may not pass through each other.
        assertTrue(
            gap >= 2 * (LEGACY_TILE_SIZE / 2 - 2),
            "tanks should remain at least 2*TANK_HALF apart, gap=$gap",
        )
    }

    @Test
    fun `tank-fired projectile cannot hit its owning tank`() {
        val map = TestMaps.empty(widthTiles = 6, heightTiles = 3).copy(
            objects = listOf(
                AuthoredObject(
                    id = "p1",
                    kind = ObjectKinds.PLAYER_START,
                    x = 2 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    y = 1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                ),
            ),
        )
        val sim = SimulationFactory.fromCanonicalMap(map)

        // Aim left and fire: the projectile spawns at the tank's left edge.
        // It should pass cleanly past the tank without inflicting damage.
        sim.tick(mapOf(0 to PlayerIntentFrame(aimX = -1, firePrimary = true)))
        val initialArmor = sim.currentState().tanks.single().armor
        repeat(8) { sim.tick(mapOf(0 to PlayerIntentFrame(aimX = -1))) }

        val tank = sim.currentState().tanks.single()
        assertEquals(initialArmor, tank.armor, "owner tank must not take damage from its own bullet")
    }

    @Test
    fun `goal collection is idempotent — driving over the same goal twice stays at one event`() {
        val map = TestMaps.empty(widthTiles = 6, heightTiles = 3).copy(
            objects = listOf(
                AuthoredObject(
                    id = "p1",
                    kind = ObjectKinds.PLAYER_START,
                    x = 1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    y = 1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                ),
                AuthoredObject(
                    id = "g",
                    kind = ObjectKinds.GOAL,
                    x = 3 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    y = 1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                ),
            ),
        )
        val sim = SimulationFactory.fromCanonicalMap(map)

        var goalEvents = 0
        repeat(60) {
            val result = sim.tick(mapOf(0 to PlayerIntentFrame(steer = 1)))
            goalEvents += result.events.count { it is SimulationEvent.GoalReached }
        }
        assertEquals(1, goalEvents, "goal should only emit GoalReached once")
        assertTrue(sim.currentState().goals.single().isClaimed)
    }

    @Test
    fun `projectile that hits a wall is removed and emits Explosion`() {
        val map = TestMaps.verticalWall(widthTiles = 6, heightTiles = 3, wallX = 3).copy(
            objects = listOf(
                AuthoredObject(
                    id = "p1",
                    kind = ObjectKinds.PLAYER_START,
                    x = 1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    y = 1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                ),
            ),
        )
        val sim = SimulationFactory.fromCanonicalMap(map)

        sim.tick(mapOf(0 to PlayerIntentFrame(aimX = 1, firePrimary = true)))
        var explosions = 0
        repeat(30) {
            val result = sim.tick(mapOf(0 to PlayerIntentFrame(aimX = 1)))
            explosions += result.events.count { it is SimulationEvent.Explosion }
        }
        assertTrue(explosions >= 1, "projectile must emit Explosion when it hits the wall")
        assertTrue(sim.currentState().projectiles.isEmpty(), "wall-bound projectile should be cleaned up")
    }
}

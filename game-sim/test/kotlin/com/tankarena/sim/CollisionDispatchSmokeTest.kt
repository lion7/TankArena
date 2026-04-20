package com.tankarena.sim

import com.tankarena.content.AuthoredObject
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.ObjectKinds
import com.tankarena.input.PlayerIntentFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CollisionDispatchSmokeTest {
    @Test
    fun `tank cannot push through a single wall while driving diagonally`() {
        val map = TestMaps.verticalWall(widthTiles = 5, heightTiles = 5, wallX = 3).copy(
            objects = listOf(
                AuthoredObject(
                    id = "p1",
                    kind = ObjectKinds.PLAYER_START,
                    x = 1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    y = 2 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    properties = mapOf("direction" to "6"),
                ),
            ),
        )
        val sim = SimulationFactory.fromCanonicalMap(map)

        repeat(40) { sim.tick(mapOf(0 to PlayerIntentFrame(forward = true))) }
        val tank = sim.currentState().tanks.single()

        assertTrue(tank.position.x < 3 * LEGACY_TILE_SIZE)
        assertTrue(tank.position.y > 2 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2)
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
                    properties = mapOf("direction" to "4"),
                ),
                AuthoredObject(
                    id = "b",
                    kind = ObjectKinds.PLAYER_START,
                    x = 2 * LEGACY_TILE_SIZE + 24,
                    y = 1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    properties = mapOf("direction" to "12"),
                ),
            ),
        )
        val sim = SimulationFactory.fromCanonicalMap(map)

        repeat(20) {
            sim.tick(
                mapOf(
                    0 to PlayerIntentFrame(forward = true),
                    1 to PlayerIntentFrame(forward = true),
                ),
            )
        }
        val tanks = sim.currentState().tanks.sortedBy { it.playerIndex }
        val gap = tanks[1].position.x - tanks[0].position.x
        assertTrue(gap >= 2 * (LEGACY_TILE_SIZE / 2 - 2), "gap=$gap")
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
                    properties = mapOf("direction" to "12"),
                ),
            ),
        )
        val sim = SimulationFactory.fromCanonicalMap(map)

        sim.tick(mapOf(0 to PlayerIntentFrame(firePrimary = true)))
        val initialArmor = sim.currentState().tanks.single().armor
        repeat(8) { sim.tick(emptyMap()) }

        val tank = sim.currentState().tanks.single()
        assertEquals(initialArmor, tank.armor)
    }

    @Test
    fun `goal collection is idempotent when driving over the same goal twice`() {
        val map = TestMaps.empty(widthTiles = 6, heightTiles = 3).copy(
            objects = listOf(
                AuthoredObject(
                    id = "p1",
                    kind = ObjectKinds.PLAYER_START,
                    x = 1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    y = 1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    properties = mapOf("direction" to "4"),
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
            val result = sim.tick(mapOf(0 to PlayerIntentFrame(forward = true)))
            goalEvents += result.events.count { it is SimulationEvent.GoalReached }
        }
        assertEquals(1, goalEvents)
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
                    properties = mapOf("direction" to "4"),
                ),
            ),
        )
        val sim = SimulationFactory.fromCanonicalMap(map)

        sim.tick(mapOf(0 to PlayerIntentFrame(firePrimary = true)))
        var explosions = 0
        repeat(30) {
            val result = sim.tick(emptyMap())
            explosions += result.events.count { it is SimulationEvent.Explosion }
        }
        assertTrue(explosions >= 1)
        assertTrue(sim.currentState().projectiles.isEmpty())
    }
}

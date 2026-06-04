package com.tankarena.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GameSimulationTest {
    @Test
    fun `tank moves upward when MoveUp is active`() {
        val sim = GameSimulation()
        val initial = sim.initialState()

        val next = sim.step(
            state = initial,
            intents = listOf(PlayerIntent(0, setOf(PlayerAction.MoveUp))),
        )

        val tank = next.entities.filterIsInstance<TankEntity>().first { it.playerId == 0 }
        assertTrue(tank.position.y < 120f)
    }

    @Test
    fun `fire primary spawns projectile`() {
        val sim = GameSimulation()
        val initial = sim.initialState()

        val next = sim.step(
            state = initial,
            intents = listOf(PlayerIntent(0, setOf(PlayerAction.FirePrimary))),
        )

        assertEquals(1, next.entities.filterIsInstance<ProjectileEntity>().size)
    }

    @Test
    fun `projectile collision damages enemy tank`() {
        val sim = GameSimulation()
        val initial = GameState(
            tick = 0,
            worldWidth = 320f,
            worldHeight = 240f,
            entities = listOf(
                TankEntity(id = 1, playerId = 0, position = Vec2(100f, 100f)),
                TankEntity(id = 2, playerId = 1, position = Vec2(100f, 90f), health = 3),
                ProjectileEntity(
                    id = 3,
                    ownerTankId = 1,
                    position = Vec2(100f, 95f),
                    velocity = Vec2(0f, -60f),
                    ttlTicks = 10,
                ),
            ),
        )

        val next = sim.step(initial, intents = emptyList())

        val enemy = next.entities.filterIsInstance<TankEntity>().first { it.id == 2L }
        assertEquals(2, enemy.health)
        assertEquals(0, next.entities.filterIsInstance<ProjectileEntity>().size)
        assertEquals(1, next.entities.filterIsInstance<ExplosionEntity>().size)
    }

    @Test
    fun `projectile ttl expiration creates explosion`() {
        val sim = GameSimulation()
        val initial = GameState(
            tick = 0,
            worldWidth = 320f,
            worldHeight = 240f,
            entities = listOf(
                TankEntity(id = 1, playerId = 0, position = Vec2(160f, 120f)),
                ProjectileEntity(
                    id = 2,
                    ownerTankId = 1,
                    position = Vec2(160f, 120f),
                    velocity = Vec2(0f, -10f),
                    ttlTicks = 1,
                ),
            ),
        )

        val next = sim.step(initial, intents = emptyList())

        assertEquals(0, next.entities.filterIsInstance<ProjectileEntity>().size)
        assertNotNull(next.entities.filterIsInstance<ExplosionEntity>().firstOrNull())
    }
}

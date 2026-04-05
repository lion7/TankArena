package com.tankarena.core

import kotlin.test.Test
import kotlin.test.assertEquals
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

        val tank = next.entities.filterIsInstance<TankEntity>().first()
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
}

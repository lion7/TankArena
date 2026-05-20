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

        assertEquals(1, next.entities.filterIsInstance<ProjectileEntity>().count { it.ownerTankId == 1L })
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

    @Test
    fun `ai controlled tank advances toward player`() {
        val sim = GameSimulation()
        val initial = sim.initialState()

        val beforeAiY = initial.entities.filterIsInstance<TankEntity>().first { it.playerId == 1 }.position.y
        val next = sim.step(initial, intents = emptyList())
        val afterAiY = next.entities.filterIsInstance<TankEntity>().first { it.playerId == 1 }.position.y

        assertTrue(afterAiY > beforeAiY)
    }

    @Test
    fun `last tank standing mode sets winner`() {
        val sim = GameSimulation()
        val initial = GameState(
            tick = 0,
            worldWidth = 320f,
            worldHeight = 240f,
            entities = listOf(
                TankEntity(id = 1, playerId = 0, position = Vec2(100f, 100f), health = 3, alive = true),
                TankEntity(id = 2, playerId = 1, position = Vec2(100f, 90f), health = 1, alive = true),
                ProjectileEntity(
                    id = 3,
                    ownerTankId = 1,
                    position = Vec2(100f, 95f),
                    velocity = Vec2(0f, -60f),
                    ttlTicks = 10,
                    damage = 1,
                ),
            ),
            modeId = "last_tank_standing",
        )

        val next = sim.step(initial, intents = emptyList())
        assertEquals(0, next.winnerPlayerId)
    }


    @Test
    fun `tank movement is blocked by structure tile`() {
        val sim = GameSimulation()
        val blockedMap = MapDefinition(
            width = 4,
            height = 4,
            tileSize = 16f,
            cells = List(16) { index ->
                if (index == (1 + 1 * 4)) TileCell(structureId = 1) else TileCell()
            },
        )
        val initial = GameState(
            tick = 0,
            worldWidth = 64f,
            worldHeight = 64f,
            map = blockedMap,
            entities = listOf(
                TankEntity(id = 1, playerId = 0, position = Vec2(15f, 24f)),
            ),
            modeId = "sandbox",
        )

        val next = sim.step(initial, intents = listOf(PlayerIntent(0, setOf(PlayerAction.MoveRight))))
        val tank = next.entities.filterIsInstance<TankEntity>().first()
        assertEquals(15f, tank.position.x)
    }

    @Test
    fun `projectile hitting structure creates explosion`() {
        val sim = GameSimulation()
        val blockedMap = MapDefinition(
            width = 4,
            height = 4,
            tileSize = 16f,
            cells = List(16) { index ->
                if (index == (1 + 1 * 4)) TileCell(structureId = 1) else TileCell()
            },
        )
        val initial = GameState(
            tick = 0,
            worldWidth = 64f,
            worldHeight = 64f,
            map = blockedMap,
            entities = listOf(
                TankEntity(id = 1, playerId = 0, position = Vec2(24f, 40f)),
                ProjectileEntity(
                    id = 2,
                    ownerTankId = 1,
                    position = Vec2(24f, 32f),
                    velocity = Vec2(0f, -60f),
                    ttlTicks = 10,
                ),
            ),
            modeId = "sandbox",
        )

        val next = sim.step(initial, intents = emptyList())
        assertEquals(0, next.entities.filterIsInstance<ProjectileEntity>().size)
        assertEquals(1, next.entities.filterIsInstance<ExplosionEntity>().size)
    }



    @Test
    fun `triple spread weapon spawns three projectiles`() {
        val sim = GameSimulation()
        val initial = GameState(
            tick = 0,
            worldWidth = 320f,
            worldHeight = 240f,
            entities = listOf(
                TankEntity(id = 1, playerId = 0, position = Vec2(100f, 100f), primaryWeaponId = "triple_spread"),
                TankEntity(id = 2, playerId = 1, position = Vec2(120f, 60f), alive = false),
            ),
            modeId = "sandbox",
        )

        val next = sim.step(initial, intents = listOf(PlayerIntent(0, setOf(PlayerAction.FirePrimary))))
        assertEquals(3, next.entities.filterIsInstance<ProjectileEntity>().count { it.ownerTankId == 1L })
    }

}

package com.tankarena.core

import kotlin.math.hypot

/**
 * Core deterministic state. Keep free from rendering/engine objects.
 */
data class GameState(
    val tick: Long,
    val worldWidth: Float,
    val worldHeight: Float,
    val entities: List<Entity>,
    val map: MapDefinition? = null,
    val modeId: String = "last_tank_standing",
    val winnerPlayerId: Int? = null,
)

class GameSimulation(
    private val fixedDeltaSeconds: Float = 1f / 60f,
    private val tankSpeedUnitsPerSecond: Float = 80f,
    private val extensions: GameplayExtensions = GameplayExtensions(),
) {
    private var nextEntityId: Long = 1000L

    fun initialState(): GameState = GameState(
        tick = 0,
        worldWidth = 320f,
        worldHeight = 240f,
        map = MapDefinition.empty(width = 20, height = 15),
        entities = listOf(
            TankEntity(id = 1, position = Vec2(160f, 120f), playerId = 0),
            TankEntity(id = 2, position = Vec2(160f, 50f), playerId = 1, aiControllerId = "basic_chase"),
        ),
    )

    fun step(state: GameState, intents: List<PlayerIntent>): GameState {
        val tanks = state.entities.filterIsInstance<TankEntity>()
        val projectiles = state.entities.filterIsInstance<ProjectileEntity>()
        val explosions = state.entities.filterIsInstance<ExplosionEntity>()

        val actionsByPlayer = intents.associate { it.playerId to it.activeActions }.toMutableMap()
        for (tank in tanks) {
            if (!tank.alive) continue
            if (actionsByPlayer[tank.playerId] != null) continue
            val aiId = tank.aiControllerId ?: continue
            val aiIntent = extensions.aiControllers[aiId]?.decide(state, tank) ?: continue
            actionsByPlayer[tank.playerId] = aiIntent.activeActions
        }

        val movedTanks = tanks.map { tank ->
            val actions = actionsByPlayer[tank.playerId].orEmpty()
            updateTank(tank, state, actions)
        }

        val spawnedProjectiles = movedTanks.flatMap { tank ->
            val actions = actionsByPlayer[tank.playerId].orEmpty()
            if (!tank.alive || PlayerAction.FirePrimary !in actions) {
                emptyList()
            } else {
                val weapon = extensions.weapons[tank.primaryWeaponId] ?: BasicShellWeapon
                weapon.onFire(state, tank) { allocateEntityId() }.filterIsInstance<ProjectileEntity>()
            }
        }

        val (resolvedProjectiles, collisionExplosions, damagedTankIds) = resolveProjectiles(
            tanks = movedTanks,
            projectiles = projectiles + spawnedProjectiles,
            state = state,
        )

        val resolvedTanks = movedTanks.map { tank ->
            val hits = damagedTankIds[tank.id] ?: 0
            if (hits == 0 || !tank.alive) {
                tank
            } else {
                val newHealth = (tank.health - hits).coerceAtLeast(0)
                tank.copy(health = newHealth, alive = newHealth > 0)
            }
        }

        val liveExplosions = explosions.flatMap { updateExplosion(it) }

        val next = state.copy(
            tick = state.tick + 1,
            entities = resolvedTanks + resolvedProjectiles + liveExplosions + collisionExplosions,
        )

        val modeRules = extensions.gameModes[next.modeId]
        return modeRules?.onTick(next) ?: next
    }

    private fun updateTank(
        tank: TankEntity,
        state: GameState,
        actions: Set<PlayerAction>,
    ): TankEntity {
        if (!tank.alive) return tank.copy(velocity = Vec2.ZERO)

        val x = (if (PlayerAction.MoveRight in actions) 1 else 0) -
            (if (PlayerAction.MoveLeft in actions) 1 else 0)
        val y = (if (PlayerAction.MoveDown in actions) 1 else 0) -
            (if (PlayerAction.MoveUp in actions) 1 else 0)

        val movement = Vec2(x.toFloat(), y.toFloat()).normalized()
        val velocity = movement * tankSpeedUnitsPerSecond
        val newPosition = clampToWorld(
            position = tank.position + (velocity * fixedDeltaSeconds),
            state = state,
        )

        val blocked = state.map?.blocksMovementAtWorld(newPosition.x, newPosition.y) == true
        val finalPosition = if (blocked) tank.position else newPosition

        return tank.copy(position = finalPosition, velocity = if (blocked) Vec2.ZERO else velocity)
    }

    private fun allocateEntityId(): Long {
        nextEntityId += 1
        return nextEntityId
    }

    private data class ProjectileResolution(
        val projectiles: List<ProjectileEntity>,
        val explosions: List<ExplosionEntity>,
        val damagedTankIds: Map<Long, Int>,
    )

    private fun resolveProjectiles(
        tanks: List<TankEntity>,
        projectiles: List<ProjectileEntity>,
        state: GameState,
    ): ProjectileResolution {
        val remainingProjectiles = mutableListOf<ProjectileEntity>()
        val explosions = mutableListOf<ExplosionEntity>()
        val damaged = mutableMapOf<Long, Int>()

        for (projectile in projectiles) {
            val movedProjectile = projectile.copy(
                position = projectile.position + (projectile.velocity * fixedDeltaSeconds),
                ttlTicks = projectile.ttlTicks - 1,
            )

            val hitTank = tanks.firstOrNull { tank ->
                tank.alive &&
                    tank.id != movedProjectile.ownerTankId &&
                    distance(tank.position, movedProjectile.position) <= (tank.radius + movedProjectile.radius)
            }

            when {
                hitTank != null -> {
                    damaged[hitTank.id] = (damaged[hitTank.id] ?: 0) + movedProjectile.damage
                    explosions += createExplosion(movedProjectile.position)
                }

                movedProjectile.ttlTicks <= 0 -> {
                    explosions += createExplosion(movedProjectile.position)
                }

                outOfWorld(movedProjectile.position, state) -> {
                    explosions += createExplosion(movedProjectile.position)
                }

                state.map?.blocksMovementAtWorld(movedProjectile.position.x, movedProjectile.position.y) == true -> {
                    explosions += createExplosion(movedProjectile.position)
                }

                else -> remainingProjectiles += movedProjectile
            }
        }

        return ProjectileResolution(
            projectiles = remainingProjectiles,
            explosions = explosions,
            damagedTankIds = damaged,
        )
    }

    private fun createExplosion(position: Vec2): ExplosionEntity {
        return ExplosionEntity(
            id = allocateEntityId(),
            position = position,
            ttlTicks = 12,
        )
    }

    private fun updateExplosion(explosion: ExplosionEntity): List<Entity> {
        val next = explosion.copy(ttlTicks = explosion.ttlTicks - 1)
        return if (next.ttlTicks > 0) listOf(next) else emptyList()
    }

    private fun clampToWorld(position: Vec2, state: GameState): Vec2 = Vec2(
        x = position.x.coerceIn(0f, state.worldWidth),
        y = position.y.coerceIn(0f, state.worldHeight),
    )

    private fun outOfWorld(position: Vec2, state: GameState): Boolean =
        position.x < 0f || position.x > state.worldWidth || position.y < 0f || position.y > state.worldHeight

    private fun distance(a: Vec2, b: Vec2): Float = hypot(a.x - b.x, a.y - b.y)
}

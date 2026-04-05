package com.tankarena.core

/**
 * Core deterministic state. Keep free from rendering/engine objects.
 */
data class GameState(
    val tick: Long,
    val worldWidth: Float,
    val worldHeight: Float,
    val entities: List<Entity>,
    val map: MapDefinition? = null,
    val modeId: String = "sandbox",
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
        entities = listOf(
            TankEntity(
                id = 1,
                position = Vec2(160f, 120f),
                playerId = 0,
            ),
        ),
    )

    fun step(state: GameState, intents: List<PlayerIntent>): GameState {
        val player0Actions = intents.firstOrNull { it.playerId == 0 }?.activeActions.orEmpty()

        val updatedEntities = state.entities.flatMap { entity ->
            when (entity) {
                is TankEntity -> updateTank(entity, state, player0Actions)
                is ProjectileEntity -> updateProjectile(entity)
                is ExplosionEntity -> updateExplosion(entity)
            }
        }

        val next = state.copy(
            tick = state.tick + 1,
            entities = updatedEntities,
        )

        val modeRules = extensions.gameModes[next.modeId]
        return modeRules?.onTick(next) ?: next
    }

    private fun updateTank(
        tank: TankEntity,
        state: GameState,
        actions: Set<PlayerAction>,
    ): List<Entity> {
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

        val spawnProjectile = PlayerAction.FirePrimary in actions
        val updatedTank = tank.copy(position = newPosition, velocity = velocity)

        return if (spawnProjectile) {
            listOf(updatedTank, createPlaceholderProjectile(updatedTank))
        } else {
            listOf(updatedTank)
        }
    }

    private fun createPlaceholderProjectile(tank: TankEntity): ProjectileEntity {
        nextEntityId += 1
        return ProjectileEntity(
            id = nextEntityId,
            position = tank.position,
            velocity = Vec2(0f, -160f),
            ownerTankId = tank.id,
            ttlTicks = 30,
        )
    }

    private fun updateProjectile(projectile: ProjectileEntity): List<Entity> {
        val next = projectile.copy(
            position = projectile.position + (projectile.velocity * fixedDeltaSeconds),
            ttlTicks = projectile.ttlTicks - 1,
        )
        return if (next.ttlTicks <= 0) {
            nextEntityId += 1
            listOf(
                ExplosionEntity(
                    id = nextEntityId,
                    position = projectile.position,
                    ttlTicks = 12,
                ),
            )
        } else {
            listOf(next)
        }
    }

    private fun updateExplosion(explosion: ExplosionEntity): List<Entity> {
        val next = explosion.copy(ttlTicks = explosion.ttlTicks - 1)
        return if (next.ttlTicks > 0) listOf(next) else emptyList()
    }

    private fun clampToWorld(position: Vec2, state: GameState): Vec2 = Vec2(
        x = position.x.coerceIn(0f, state.worldWidth),
        y = position.y.coerceIn(0f, state.worldHeight),
    )
}

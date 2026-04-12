package com.tankarena.sim

import com.tankarena.content.WeaponType
import com.tankarena.core.Int2
import com.tankarena.core.wrapCoordinate
import com.tankarena.input.PlayerIntentFrame
import kotlin.math.sign

data class SimulationResult(
    val previous: WorldState,
    val current: WorldState,
    val events: List<SimulationEvent>,
)

sealed interface SimulationEvent {
    data class FireProjectile(val ownerId: Long, val origin: Int2, val velocity: Int2) : SimulationEvent
    data class Explosion(val position: Int2) : SimulationEvent
}

class TankArenaSimulation(
    initialState: WorldState,
) {
    private var state: WorldState = initialState
    private var nextProjectileId: Long = 10_000

    fun currentState(): WorldState = state

    fun tick(playerInputs: Map<Int, PlayerIntentFrame>): SimulationResult {
        val previous = state
        val events = mutableListOf<SimulationEvent>()

        val updatedTanks = state.tanks.map { tank ->
            val input = playerInputs[tank.playerIndex] ?: PlayerIntentFrame()
            val thrust = input.throttle.coerceIn(-1, 1) * 4
            val facing = when {
                input.aimX != 0 || input.aimY != 0 -> Int2(input.aimX.sign, input.aimY.sign)
                input.steer > 0 -> Int2(1, tank.facing.y)
                input.steer < 0 -> Int2(-1, tank.facing.y)
                else -> tank.facing
            }
            val velocity = Int2(
                x = input.steer.coerceIn(-1, 1) * 3,
                y = thrust,
            )
            val nextPosition = Int2(
                x = wrapCoordinate(tank.position.x + velocity.x, state.bounds.widthPixels),
                y = wrapCoordinate(tank.position.y + velocity.y, state.bounds.heightPixels),
            )
            if (input.firePrimary) {
                events += SimulationEvent.FireProjectile(
                    ownerId = tank.id,
                    origin = nextPosition,
                    velocity = Int2(facing.x * 8, facing.y * 8),
                )
            }
            tank.copy(position = nextPosition, facing = facing, velocity = velocity)
        }

        val updatedTurrets = state.turrets.map { turret ->
            if (turret.cooldownTicks > 0) turret.copy(cooldownTicks = turret.cooldownTicks - 1)
            else turret
        }

        val spawnedProjectiles = events.filterIsInstance<SimulationEvent.FireProjectile>().map { event ->
            ProjectileState(
                id = nextProjectileId++,
                ownerId = event.ownerId,
                position = event.origin,
                velocity = event.velocity,
                ttlTicks = 50,
            )
        }

        val updatedProjectiles = (state.projectiles + spawnedProjectiles).mapNotNull { projectile ->
            val nextTtl = projectile.ttlTicks - 1
            if (nextTtl <= 0) {
                events += SimulationEvent.Explosion(projectile.position)
                null
            } else {
                projectile.copy(
                    position = Int2(
                        x = wrapCoordinate(projectile.position.x + projectile.velocity.x, state.bounds.widthPixels),
                        y = wrapCoordinate(projectile.position.y + projectile.velocity.y, state.bounds.heightPixels),
                    ),
                    ttlTicks = nextTtl,
                )
            }
        }

        state = state.copy(
            tick = state.tick + 1,
            tanks = updatedTanks,
            turrets = updatedTurrets,
            projectiles = updatedProjectiles,
        )
        return SimulationResult(previous = previous, current = state, events = events)
    }

    companion object {
        fun firstMilestonePrototype(widthPixels: Int, heightPixels: Int): TankArenaSimulation {
            val initial = WorldState(
                bounds = WorldBounds(widthPixels = widthPixels, heightPixels = heightPixels),
                tanks = listOf(
                    TankState(
                        id = 1,
                        playerIndex = 0,
                        position = Int2(widthPixels / 2, heightPixels / 2),
                        facing = Int2(0, -1),
                        velocity = Int2(0, 0),
                        armor = 100,
                        fuel = 100,
                        selectedWeapon = WeaponType.MAIN_CANNON,
                    ),
                ),
                turrets = listOf(
                    TurretState(
                        id = 2,
                        position = Int2(widthPixels / 2 + 128, heightPixels / 2),
                        cooldownTicks = 0,
                    ),
                ),
            )
            return TankArenaSimulation(initial)
        }
    }
}


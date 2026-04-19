package com.tankarena.sim

import com.tankarena.core.Int2

data class SimulationResult(
    val previous: WorldState,
    val current: WorldState,
    val events: List<SimulationEvent>,
)

sealed interface SimulationEvent {
    data class FireProjectile(
        val ownerId: Long,
        val ownerKind: ProjectileOwnerKind,
        val origin: Int2,
        val velocity: Int2,
        val damage: Int,
    ) : SimulationEvent

    data class Explosion(val position: Int2) : SimulationEvent
    data class TankHit(val tankId: Long, val damage: Int) : SimulationEvent
    data class TankDestroyed(val tankId: Long) : SimulationEvent
    data class TankRespawned(val tankId: Long) : SimulationEvent
    data class GoalReached(val goalId: Long, val tankId: Long, val contribution: Int) : SimulationEvent
    data object MissionWon : SimulationEvent
    data object MissionLost : SimulationEvent
}

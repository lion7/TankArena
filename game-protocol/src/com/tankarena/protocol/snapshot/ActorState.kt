package com.tankarena.protocol.snapshot

import com.tankarena.protocol.Team
import kotlinx.serialization.Serializable

/**
 * Per-actor wire state. Each concrete subtype doubles as the Kubriko `State` produced by
 * the matching server actor's `save()` and consumed by the client actor's `sync()`.
 */
@Serializable
sealed interface ActorState {
    val actorId: Long
}

@Serializable
data class TankState(
    override val actorId: Long,
    val team: Team = Team.NEUTRAL,
    val tankType: Int = 0,
    val x: Int,
    val y: Int,
    val vx: Float = 0f,
    val vy: Float = 0f,
    val bodyDirection: Int = 0,
    val turretDirection: Int = 0,
    val armor: Int = 0,
    val alive: Boolean = true,
    val invulnerable: Boolean = false,
    val controlled: Boolean = false,
    val primaryCooldownTicks: Int = 0,
    val currentWeapon: Int = 0,
    val chainAmmo: Int = 0,
    val mineAmmo: Int = 0,
    val rocketAmmo: Int = 0,
    val mortarAmmo: Int = 0,
) : ActorState

@Serializable
data class TurretState(
    override val actorId: Long,
    val team: Team = Team.NEUTRAL,
    val turretType: Int = 0,
    val x: Int,
    val y: Int,
    val turretDirection: Int = 0,
    val alive: Boolean = true,
    val primaryCooldownTicks: Int = 0,
) : ActorState

@Serializable
data class ProjectileState(
    override val actorId: Long,
    val ownerId: Long,
    val x: Int,
    val y: Int,
    val vx: Int = 0,
    val vy: Int = 0,
    val ownerKind: ProjectileOwnerKind = ProjectileOwnerKind.TANK,
) : ActorState

@Serializable
data class GoalState(
    override val actorId: Long,
    val team: Team = Team.NEUTRAL,
    val x: Int,
    val y: Int,
    val captured: Boolean = false,
) : ActorState

@Serializable
data class WallState(
    override val actorId: Long,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) : ActorState

@Serializable
data class FlagState(
    override val actorId: Long,
    val flagType: Int = 0,
    val number: Int = 0,
    val x: Int,
    val y: Int,
    val isCarried: Boolean = false,
) : ActorState

@Serializable
data class ProductState(
    override val actorId: Long,
    val productType: Int = 0,
    val price: Int = 1,
    val x: Int,
    val y: Int,
    val isCollected: Boolean = false,
) : ActorState

@Serializable
data class LockState(
    override val actorId: Long,
    val activation: Int = 0,
    val target: Int = 0,
    val x: Int,
    val y: Int,
    val isFired: Boolean = false,
) : ActorState

@Serializable
data class WarpState(
    override val actorId: Long,
    val targetX: Int = 0,
    val targetY: Int = 0,
    val x: Int,
    val y: Int,
    val cooldownTicks: Int = 0,
) : ActorState

@Serializable
data class DestroyerState(
    override val actorId: Long,
    val radius: Int = 0,
    val what: Int = 3,
    val immediate: Boolean = false,
    val x: Int,
    val y: Int,
    val isFired: Boolean = false,
) : ActorState

@Serializable
data class EnforcerState(
    override val actorId: Long,
    val radius: Int = 0,
    val weapon: Int = 0,
    val delay: Int = 0,
    val good: Boolean = false,
    val bad: Boolean = false,
    val x: Int,
    val y: Int,
) : ActorState

@Serializable
data class TrainState(
    override val actorId: Long,
    val x: Int,
    val y: Int,
    val isEngine: Boolean = true,
    val armor: Int = 60,
    val alive: Boolean = true,
) : ActorState

@Serializable
data class ZeppelinState(
    override val actorId: Long,
    val x: Int,
    val y: Int,
    val alive: Boolean = true,
) : ActorState

@Serializable
data class B52State(
    override val actorId: Long,
    val x: Int,
    val y: Int,
    val armor: Int = 30,
    val alive: Boolean = true,
    val bombCount: Int = 0,
) : ActorState

@Serializable
enum class ProjectileOwnerKind { TANK, TURRET }

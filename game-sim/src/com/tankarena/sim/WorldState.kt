package com.tankarena.sim

import com.tankarena.content.WeaponType
import com.tankarena.core.EntityId
import com.tankarena.core.Int2
import com.tankarena.core.Tick
import kotlinx.serialization.Serializable

@Serializable
enum class ProjectileOwnerKind { TANK, TURRET }

@Serializable
data class TankState(
    val id: Long,
    val playerIndex: Int,
    val tankType: Int = 0,
    val position: Int2,
    val facing: Int2,
    val turretFacing: Int2 = facing,
    val velocity: Int2,
    val armor: Int,
    val fuel: Int,
    val selectedWeapon: WeaponType,
    val primaryCooldownTicks: Int = 0,
    val isAlive: Boolean = true,
    val respawnInTicks: Int = 0,
    val lives: Int = 3,
)

@Serializable
data class TurretState(
    val id: Long,
    val turretType: Int = 0,
    val direction: Int = 0,
    val position: Int2,
    val cooldownTicks: Int,
    val fireDelayTicks: Int = 60,
    val rangePixels: Int = 220,
    val damage: Int = 20,
)

@Serializable
data class ProjectileState(
    val id: Long,
    val ownerId: Long,
    val ownerKind: ProjectileOwnerKind = ProjectileOwnerKind.TANK,
    val position: Int2,
    val velocity: Int2,
    val ttlTicks: Int,
    val damage: Int = 25,
)

@Serializable
data class WorldBounds(
    val widthPixels: Int,
    val heightPixels: Int,
)

@Serializable
data class WorldState(
    val tick: Long = 0,
    val bounds: WorldBounds,
    val tanks: List<TankState> = emptyList(),
    val turrets: List<TurretState> = emptyList(),
    val projectiles: List<ProjectileState> = emptyList(),
)

fun EntityId.asLong(): Long = value
fun Tick.asLong(): Long = value

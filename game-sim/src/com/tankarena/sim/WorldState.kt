package com.tankarena.sim

import com.tankarena.content.WeaponType
import com.tankarena.core.EntityId
import com.tankarena.core.Int2
import com.tankarena.core.Tick
import kotlinx.serialization.Serializable

@Serializable
data class TankState(
    val id: Long,
    val playerIndex: Int,
    val tankType: Int = 0,
    val position: Int2,
    val facing: Int2,
    val velocity: Int2,
    val armor: Int,
    val fuel: Int,
    val selectedWeapon: WeaponType,
)

@Serializable
data class TurretState(
    val id: Long,
    val turretType: Int = 0,
    val direction: Int = 0,
    val position: Int2,
    val cooldownTicks: Int,
)

@Serializable
data class ProjectileState(
    val id: Long,
    val ownerId: Long,
    val position: Int2,
    val velocity: Int2,
    val ttlTicks: Int,
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

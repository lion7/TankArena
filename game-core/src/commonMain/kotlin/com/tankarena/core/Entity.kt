package com.tankarena.core

enum class EntityKind {
    Tank,
    Projectile,
    Explosion,
}

sealed interface Entity {
    val id: Long
    val kind: EntityKind
    val position: Vec2
}

data class TankEntity(
    override val id: Long,
    override val position: Vec2,
    val velocity: Vec2 = Vec2.ZERO,
    val angleDegrees: Float = 0f,
    val playerId: Int,
    val alive: Boolean = true,
) : Entity {
    override val kind: EntityKind = EntityKind.Tank
}

data class ProjectileEntity(
    override val id: Long,
    override val position: Vec2,
    val velocity: Vec2,
    val ownerTankId: Long,
    val ttlTicks: Int,
) : Entity {
    override val kind: EntityKind = EntityKind.Projectile
}

data class ExplosionEntity(
    override val id: Long,
    override val position: Vec2,
    val ttlTicks: Int,
) : Entity {
    override val kind: EntityKind = EntityKind.Explosion
}

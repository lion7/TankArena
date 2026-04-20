package com.tankarena.sim.actor

import com.pandulapeter.kubriko.actor.body.PointBody
import com.pandulapeter.kubriko.collision.Collidable
import com.pandulapeter.kubriko.collision.mask.BoxCollisionMask
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.SceneSize
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.core.Int2
import com.tankarena.core.LegacyDirections
import com.tankarena.sim.ProjectileOwnerKind
import com.tankarena.sim.SimulationEvent
import com.tankarena.sim.TurretState

private const val TURRET_HALF = LEGACY_TILE_SIZE / 2 - 2
private const val PROJECTILE_SPEED = 8

internal class TurretActor(
    val id: Long,
    val turretType: Int,
    val position: Int2,
    initialDirection: Int,
    initialCooldown: Int,
    val fireDelayTicks: Int,
    val rangePixels: Int,
    val damage: Int,
) : Collidable {

    var direction: Int = initialDirection
    var cooldownTicks: Int = initialCooldown

    override val body: PointBody = PointBody(initialPosition = position.toSceneOffset())

    override val collisionMask: BoxCollisionMask = BoxCollisionMask(
        initialPosition = position.toSceneOffset(),
        initialSize = SceneSize(
            width = (TURRET_HALF * 2).toFloat().sceneUnit,
            height = (TURRET_HALF * 2).toFloat().sceneUnit,
        ),
    )

    fun step(tanks: List<TankActor>, events: MutableList<SimulationEvent>) {
        val target = nearestAliveTankInRange(tanks) ?: run {
            cooldownTicks = (cooldownTicks - 1).coerceAtLeast(0)
            return
        }
        val desired = directionToTarget(position, Int2(target.positionX, target.positionY))
        direction = stepDirection(direction, desired)
        cooldownTicks = (cooldownTicks - 1).coerceAtLeast(0)
        if (cooldownTicks == 0 && direction == desired) {
            val (vx, vy) = projectileVelocityFromDirection(direction)
            val origin = Int2(
                x = position.x + vx * 2,
                y = position.y + vy * 2,
            )
            events += SimulationEvent.FireProjectile(
                ownerId = id,
                ownerKind = ProjectileOwnerKind.TURRET,
                origin = origin,
                velocity = Int2(vx, vy),
                damage = damage,
            )
            cooldownTicks = fireDelayTicks
        }
    }

    fun toState(): TurretState = TurretState(
        id = id,
        turretType = turretType,
        direction = direction,
        position = position,
        cooldownTicks = cooldownTicks,
        fireDelayTicks = fireDelayTicks,
        rangePixels = rangePixels,
        damage = damage,
    )

    private fun nearestAliveTankInRange(tanks: List<TankActor>): TankActor? {
        var best: TankActor? = null
        var bestDistSq = Int.MAX_VALUE
        val rangeSq = rangePixels.toLong() * rangePixels.toLong()
        for (tank in tanks) {
            if (!tank.isAlive) continue
            val dx = tank.positionX - position.x
            val dy = tank.positionY - position.y
            val distSq = dx * dx + dy * dy
            if (distSq <= rangeSq && distSq < bestDistSq) {
                best = tank
                bestDistSq = distSq
            }
        }
        return best
    }

    private fun directionToTarget(from: Int2, to: Int2): Int {
        return LegacyDirections.fromFacing(
            facingX = (to.x - from.x).coerceIn(-1, 1),
            facingY = (to.y - from.y).coerceIn(-1, 1),
        )
    }

    private fun stepDirection(current: Int, desired: Int): Int {
        return LegacyDirections.stepToward(current, desired)
    }

    private fun projectileVelocityFromDirection(direction: Int): Pair<Int, Int> {
        val (vx, vy) = LegacyDirections.toVelocityStep(direction, PROJECTILE_SPEED.toFloat())
        return vx.toInt() to vy.toInt()
    }

    companion object {
        fun fromState(state: TurretState): TurretActor = TurretActor(
            id = state.id,
            turretType = state.turretType,
            position = state.position,
            initialDirection = state.direction,
            initialCooldown = state.cooldownTicks,
            fireDelayTicks = state.fireDelayTicks,
            rangePixels = state.rangePixels,
            damage = state.damage,
        )
    }
}

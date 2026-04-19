package com.tankarena.sim.actor

import com.pandulapeter.kubriko.actor.body.PointBody
import com.pandulapeter.kubriko.collision.Collidable
import com.pandulapeter.kubriko.collision.mask.BoxCollisionMask
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.SceneSize
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.core.Int2
import com.tankarena.sim.ProjectileOwnerKind
import com.tankarena.sim.SimulationEvent
import com.tankarena.sim.TurretState
import kotlin.math.sign

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
        val dx = (to.x - from.x).sign
        val dy = (to.y - from.y).sign
        return when {
            dx == 0 && dy < 0 -> 0
            dx > 0 && dy < 0 -> 2
            dx > 0 && dy == 0 -> 4
            dx > 0 && dy > 0 -> 6
            dx == 0 && dy > 0 -> 8
            dx < 0 && dy > 0 -> 10
            dx < 0 && dy == 0 -> 12
            dx < 0 && dy < 0 -> 14
            else -> 0
        }
    }

    private fun stepDirection(current: Int, desired: Int): Int {
        if (current == desired) return current
        val diff = ((desired - current + 16) % 16)
        val step = if (diff <= 8) 1 else -1
        return ((current + step + 16) % 16)
    }

    private fun projectileVelocityFromDirection(direction: Int): Pair<Int, Int> {
        val (dx, dy) = DIRECTION_VECTORS[((direction % 16) + 16) % 16]
        return dx * PROJECTILE_SPEED to dy * PROJECTILE_SPEED
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

        private val DIRECTION_VECTORS: Array<Pair<Int, Int>> = arrayOf(
            0 to -1,   // 0: up
            1 to -2,   // 1: up-right (more up)
            1 to -1,   // 2: up-right
            2 to -1,   // 3: right (more right)
            1 to 0,    // 4: right
            2 to 1,    // 5: right (more right)
            1 to 1,    // 6: down-right
            1 to 2,    // 7: down (more down)
            0 to 1,    // 8: down
            -1 to 2,   // 9
            -1 to 1,   // 10: down-left
            -2 to 1,   // 11
            -1 to 0,   // 12: left
            -2 to -1,  // 13
            -1 to -1,  // 14: up-left
            -1 to -2,  // 15
        )
    }
}

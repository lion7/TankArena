package com.tankarena.sim.actor

import com.pandulapeter.kubriko.actor.body.PointBody
import com.pandulapeter.kubriko.collision.Collidable
import com.pandulapeter.kubriko.collision.CollisionDetector
import com.pandulapeter.kubriko.collision.mask.CircleCollisionMask
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.tankarena.core.Int2
import com.tankarena.sim.ProjectileOwnerKind
import com.tankarena.sim.ProjectileState
import com.tankarena.sim.SimulationEvent
import kotlin.reflect.KClass

internal const val PROJECTILE_TTL_TICKS: Int = 50
private const val PROJECTILE_RADIUS_PX = 1.5f

/**
 * Runtime wrapper for an in-flight projectile. Detects collisions against
 * walls, tanks, and turrets; emits Explosion / TankHit and queues damage on
 * the impacted tank.
 *
 * Owner filter: a TANK-fired projectile cannot hit the tank it came from.
 * TURRET-fired projectiles can hit any tank (matching legacy behaviour).
 */
internal class ProjectileActor(
    val id: Long,
    val ownerId: Long,
    val ownerKind: ProjectileOwnerKind,
    initialPosition: Int2,
    val velocity: Int2,
    initialTtl: Int,
    val damage: Int,
) : CollisionDetector {

    var positionX: Int = initialPosition.x
    var positionY: Int = initialPosition.y
    var ttlTicks: Int = initialTtl
    var isDead: Boolean = false
        private set
    var explosionAt: Int2? = null
        private set

    override val body: PointBody = PointBody(
        initialPosition = sceneOffsetOf(positionX, positionY),
    )

    override val collisionMask: CircleCollisionMask = CircleCollisionMask(
        initialPosition = sceneOffsetOf(positionX, positionY),
        initialRadius = PROJECTILE_RADIUS_PX.sceneUnit,
    )

    override val collidableTypes: List<KClass<out Collidable>> = listOf(
        WallActor::class,
        TankActor::class,
        TurretActor::class,
    )

    /**
     * Advances position by [velocity], decrements TTL, and clamps against the
     * world bounds. If the position leaves the world or the TTL expires, the
     * projectile is killed and an explosion is queued. Wall and tank impacts
     * are handled later in [onCollisionDetected] during the dispatch phase.
     */
    fun step(worldWidthPixels: Int, worldHeightPixels: Int) {
        if (isDead) return
        positionX += velocity.x
        positionY += velocity.y
        if (positionX < 0 || positionY < 0 ||
            positionX >= worldWidthPixels || positionY >= worldHeightPixels
        ) {
            kill(Int2(positionX.coerceIn(0, worldWidthPixels - 1), positionY.coerceIn(0, worldHeightPixels - 1)))
            return
        }
        syncMaskPosition()
        ttlTicks -= 1
        if (ttlTicks <= 0) {
            kill(Int2(positionX, positionY))
        }
    }

    override fun onCollisionDetected(collidables: List<Collidable>) {
        if (isDead) return
        for (other in collidables) {
            if (isDead) return
            when (other) {
                is WallActor -> kill(Int2(positionX, positionY))
                is TankActor -> {
                    if (!other.isAlive) continue
                    if (ownerKind == ProjectileOwnerKind.TANK && other.id == ownerId) continue
                    other.queueDamage(damage)
                    kill(Int2(positionX, positionY), emitTankHit = other.id)
                }
                is TurretActor -> {
                    // Legacy behavior never damages turrets but the projectile is consumed.
                    kill(Int2(positionX, positionY))
                }
                else -> Unit
            }
        }
    }

    fun drainEvents(events: MutableList<SimulationEvent>) {
        val explosion = explosionAt ?: return
        pendingHitTank?.let { tankId ->
            events += SimulationEvent.TankHit(tankId = tankId, damage = damage)
        }
        events += SimulationEvent.Explosion(position = explosion)
        explosionAt = null
        pendingHitTank = null
    }

    private var pendingHitTank: Long? = null

    private fun kill(at: Int2, emitTankHit: Long? = null) {
        if (isDead) return
        isDead = true
        explosionAt = at
        if (emitTankHit != null) pendingHitTank = emitTankHit
    }

    fun syncMaskPosition() {
        val offset = sceneOffsetOf(positionX, positionY)
        body.position = offset
        collisionMask.position = offset
    }

    fun toState(): ProjectileState = ProjectileState(
        id = id,
        ownerId = ownerId,
        ownerKind = ownerKind,
        position = Int2(positionX, positionY),
        velocity = velocity,
        ttlTicks = ttlTicks,
        damage = damage,
    )

    companion object {
        fun fromEvent(id: Long, event: SimulationEvent.FireProjectile): ProjectileActor = ProjectileActor(
            id = id,
            ownerId = event.ownerId,
            ownerKind = event.ownerKind,
            initialPosition = event.origin,
            velocity = event.velocity,
            initialTtl = PROJECTILE_TTL_TICKS,
            damage = event.damage,
        )

        fun fromState(state: ProjectileState): ProjectileActor = ProjectileActor(
            id = state.id,
            ownerId = state.ownerId,
            ownerKind = state.ownerKind,
            initialPosition = state.position,
            velocity = state.velocity,
            initialTtl = state.ttlTicks,
            damage = state.damage,
        )
    }
}

package com.tankarena.sim.kubriko.server

import com.pandulapeter.kubriko.actor.body.PointBody
import com.pandulapeter.kubriko.actor.traits.Dynamic
import com.pandulapeter.kubriko.collision.Collidable
import com.pandulapeter.kubriko.collision.CollisionDetector
import com.pandulapeter.kubriko.collision.mask.CircleCollisionMask
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.serialization.Serializable
import com.pandulapeter.kubriko.serialization.typeSerializers.SerializablePointBody
import com.pandulapeter.kubriko.types.SceneOffset
import com.tankarena.protocol.snapshot.ProjectileOwnerKind
import kotlin.reflect.KClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

internal const val PROJECTILE_TTL_TICKS: Int = 50
private const val PROJECTILE_RADIUS_PX: Float = 1.5f

class ServerProjectileActor internal constructor(state: State) :
    CollisionDetector,
    Dynamic,
    Serializable<ServerProjectileActor> {

    override val body: PointBody = state.body
    override val collisionMask: CircleCollisionMask = CircleCollisionMask(
        initialPosition = body.position,
        initialRadius = PROJECTILE_RADIUS_PX.sceneUnit,
    )
    override val isAlwaysActive: Boolean = true

    override val collidableTypes: List<KClass<out Collidable>> = listOf(
        ServerWallActor::class,
        ServerTankActor::class,
        ServerTurretActor::class,
    )

    val ownerActorId: Long = state.ownerActorId
    val ownerKind: ProjectileOwnerKind = state.ownerKind
    val damage: Int = state.damage
    val velocityX: Int = state.velocityX
    val velocityY: Int = state.velocityY

    var positionX: Int = body.position.x.raw.toInt()
        private set
    var positionY: Int = body.position.y.raw.toInt()
        private set
    var ttlTicks: Int = state.ttlTicks
        private set
    var isDead: Boolean = false
        private set

    private var explosionX: Int = 0
    private var explosionY: Int = 0
    private var explosionPending: Boolean = false
    private var tankHitRef: ServerTankActor? = null
    internal var ownerRef: ServerTankActor? = null

    override fun update(deltaTimeInMilliseconds: Int) {
        if (isDead) return
        positionX += velocityX
        positionY += velocityY
        syncBody()
        ttlTicks -= 1
        if (ttlTicks <= 0) {
            markDead(positionX, positionY)
        }
    }

    fun markOutOfBoundsIfNeeded(worldWidthPixels: Int, worldHeightPixels: Int) {
        if (isDead) return
        if (positionX < 0 || positionY < 0 ||
            positionX >= worldWidthPixels || positionY >= worldHeightPixels
        ) {
            markDead(
                positionX.coerceIn(0, worldWidthPixels - 1),
                positionY.coerceIn(0, worldHeightPixels - 1),
            )
        }
    }

    override fun onCollisionDetected(collidables: List<Collidable>) {
        if (isDead) return
        for (other in collidables) {
            if (isDead) return
            when (other) {
                is ServerWallActor -> markDead(positionX, positionY)
                is ServerTankActor -> {
                    if (ownerKind == ProjectileOwnerKind.TANK && other === ownerRef) continue
                    if (other.armor <= 0) continue
                    other.queueDamage(damage)
                    tankHitRef = other
                    markDead(positionX, positionY)
                }

                is ServerTurretActor -> markDead(positionX, positionY)
                else -> Unit
            }
        }
    }

    fun drainExplosion(): Explosion? {
        if (!explosionPending) return null
        explosionPending = false
        val hit = tankHitRef
        tankHitRef = null
        return Explosion(explosionX, explosionY, hit)
    }

    data class Explosion(val x: Int, val y: Int, val tankHit: ServerTankActor?)

    override fun save(): State = State(
        body = body,
        ownerActorId = ownerActorId,
        ownerKind = ownerKind,
        damage = damage,
        velocityX = velocityX,
        velocityY = velocityY,
        ttlTicks = ttlTicks,
    )

    private fun markDead(atX: Int, atY: Int) {
        if (isDead) return
        isDead = true
        explosionX = atX
        explosionY = atY
        explosionPending = true
    }

    private fun syncBody() {
        val offset = SceneOffset(positionX.toFloat().sceneUnit, positionY.toFloat().sceneUnit)
        body.position = offset
        collisionMask.position = offset
    }

    @kotlinx.serialization.Serializable
    data class State(
        @SerialName("body") val body: SerializablePointBody = PointBody(),
        @SerialName("ownerActorId") val ownerActorId: Long = 0L,
        @SerialName("ownerKind") val ownerKind: ProjectileOwnerKind = ProjectileOwnerKind.TANK,
        @SerialName("damage") val damage: Int = 0,
        @SerialName("velocityX") val velocityX: Int = 0,
        @SerialName("velocityY") val velocityY: Int = 0,
        @SerialName("ttlTicks") val ttlTicks: Int = PROJECTILE_TTL_TICKS,
    ) : Serializable.State<ServerProjectileActor> {
        override fun restore(): ServerProjectileActor = ServerProjectileActor(this)
        override fun serialize(): String = Json.encodeToString(this)
    }
}


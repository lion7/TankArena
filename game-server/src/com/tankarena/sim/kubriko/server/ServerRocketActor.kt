package com.tankarena.sim.kubriko.server

import com.pandulapeter.kubriko.actor.body.PointBody
import com.pandulapeter.kubriko.actor.traits.Dynamic
import com.pandulapeter.kubriko.collision.Collidable
import com.pandulapeter.kubriko.collision.mask.CircleCollisionMask
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.serialization.Serializable
import com.pandulapeter.kubriko.serialization.typeSerializers.SerializablePointBody
import com.pandulapeter.kubriko.types.SceneOffset
import kotlin.math.hypot
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

internal const val ROCKET_TTL_TICKS: Int = 330
internal const val ROCKET_MAX_SPEED: Float = 10f
internal const val ROCKET_ACCELERATION: Float = 0.5f
private const val ROCKET_TURN_RATE_RAD_PER_TICK: Float = 0.10f

class ServerRocketActor internal constructor(state: State) :
    Collidable,
    Dynamic,
    Serializable<ServerRocketActor> {

    override val body: PointBody = state.body
    override val collisionMask: CircleCollisionMask = CircleCollisionMask(
        initialPosition = body.position,
        initialRadius = 2f.sceneUnit,
    )

    val ownerActorId: Long = state.ownerActorId
    val damage: Int = state.damage

    var positionX: Float = body.position.x.raw
        private set
    var positionY: Float = body.position.y.raw
        private set
    var velocityX: Float = state.velocityX
        private set
    var velocityY: Float = state.velocityY
        private set
    var targetX: Int = state.targetX
        private set
    var targetY: Int = state.targetY
        private set
    var ttlTicks: Int = state.ttlTicks
        private set
    var isDead: Boolean = false
        private set

    private var pendingExplosion: Boolean = false
    internal var ownerRef: ServerTankActor? = null

    override fun update(deltaTimeInMilliseconds: Int) {
        if (isDead) return
        steerTowardTarget()
        positionX += velocityX
        positionY += velocityY
        val offset = SceneOffset(positionX.sceneUnit, positionY.sceneUnit)
        body.position = offset
        collisionMask.position = offset
        ttlTicks -= 1
        if (ttlTicks <= 0) markDead()
    }

    private fun steerTowardTarget() {
        val dx = targetX - positionX
        val dy = targetY - positionY
        val distance = hypot(dx, dy)
        if (distance < 0.5f) return

        val desiredAngle = kotlin.math.atan2(dy, dx)
        val currentAngle = kotlin.math.atan2(velocityY, velocityX)
        var angleDelta = desiredAngle - currentAngle
        while (angleDelta > kotlin.math.PI) angleDelta = (angleDelta - 2 * kotlin.math.PI).toFloat()
        while (angleDelta < -kotlin.math.PI) angleDelta = (angleDelta + 2 * kotlin.math.PI).toFloat()
        val clamped = angleDelta.toFloat().coerceIn(-ROCKET_TURN_RATE_RAD_PER_TICK, ROCKET_TURN_RATE_RAD_PER_TICK)
        val newAngle = currentAngle + clamped

        val currentSpeed = hypot(velocityX, velocityY)
        val newSpeed = (currentSpeed + ROCKET_ACCELERATION).coerceAtMost(ROCKET_MAX_SPEED)
        velocityX = (kotlin.math.cos(newAngle) * newSpeed).toFloat()
        velocityY = (kotlin.math.sin(newAngle) * newSpeed).toFloat()
    }

    fun markOutOfBoundsIfNeeded(worldWidthPixels: Int, worldHeightPixels: Int) {
        if (isDead) return
        if (positionX < 0 || positionY < 0 ||
            positionX >= worldWidthPixels || positionY >= worldHeightPixels
        ) {
            markDead()
        }
    }

    internal fun explodeOn(victim: ServerTankActor?) {
        if (isDead) return
        victim?.queueDamage(damage)
        markDead()
    }

    private fun markDead() {
        if (isDead) return
        isDead = true
        pendingExplosion = true
    }

    fun drainExplosion(): Explosion? {
        if (!pendingExplosion) return null
        pendingExplosion = false
        return Explosion(positionX.toInt(), positionY.toInt())
    }

    data class Explosion(val x: Int, val y: Int)

    override fun save(): State = State(
        body = body,
        ownerActorId = ownerActorId,
        damage = damage,
        velocityX = velocityX,
        velocityY = velocityY,
        targetX = targetX,
        targetY = targetY,
        ttlTicks = ttlTicks,
    )

    @kotlinx.serialization.Serializable
    data class State(
        @SerialName("body") val body: SerializablePointBody = PointBody(),
        @SerialName("ownerActorId") val ownerActorId: Long = 0L,
        @SerialName("damage") val damage: Int = 0,
        @SerialName("velocityX") val velocityX: Float = 0f,
        @SerialName("velocityY") val velocityY: Float = 0f,
        @SerialName("targetX") val targetX: Int = 0,
        @SerialName("targetY") val targetY: Int = 0,
        @SerialName("ttlTicks") val ttlTicks: Int = ROCKET_TTL_TICKS,
    ) : Serializable.State<ServerRocketActor> {
        override fun restore(): ServerRocketActor = ServerRocketActor(this)
        override fun serialize(): String = Json.encodeToString(this)
    }
}

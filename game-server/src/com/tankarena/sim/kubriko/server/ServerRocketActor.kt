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

/** Legacy `rck.dist`: a rocket that has flown this far without reaching its target blows up. */
internal const val ROCKET_RANGE_PX: Int = 3300
/** Legacy `rck.v` cap in px per second; the rocket starts at 0 and gains 1 px/s every tick. */
internal const val ROCKET_MAX_SPEED_PX_PER_SECOND: Int = 150
/** Legacy control_rocket(): the rocket detonates once it is closer than this to its target. */
internal const val ROCKET_ARRIVAL_PX: Int = 7
/** Legacy `check_obj_hit_radius(..., 10, HT_ROCKET, ...)`. */
internal const val ROCKET_BLAST_RADIUS_PX: Int = 10
private const val TICKS_PER_SECOND: Float = 100f

/** Something a rocket homes on: its current centre, or null once it is destroyed. */
internal fun interface RocketTarget {
    fun position(): Pair<Int, Int>?
}

/**
 * Legacy homing missile (src/objects/rocket.c): it heads straight for its target every tick,
 * accelerating from standstill, and explodes when it arrives or runs out of range. The target
 * is whatever was under the lock-on marker when it was launched, or else the marker's spot.
 */
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
    /** Current speed in px per second (legacy `rck.v`). */
    var speed: Int = state.speed
        private set
    /** Distance left before the rocket gives up (legacy `rck.dist`). */
    var rangeLeft: Float = state.rangeLeft
        private set
    var isDead: Boolean = false
        private set

    private var pendingExplosion: Boolean = false
    internal var ownerRef: ServerTankActor? = null
    internal var target: RocketTarget? = null

    override fun update(deltaTimeInMilliseconds: Int) {
        if (isDead) return
        target?.position()?.let { (x, y) ->
            targetX = x
            targetY = y
        }
        val dx = targetX - positionX
        val dy = targetY - positionY
        val distance = hypot(dx, dy)
        val arrived = distance < ROCKET_ARRIVAL_PX
        val step = speed / TICKS_PER_SECOND
        if (distance > 0f) {
            velocityX = dx / distance * step
            velocityY = dy / distance * step
        }
        positionX += velocityX
        positionY += velocityY
        rangeLeft -= step
        if (speed < ROCKET_MAX_SPEED_PX_PER_SECOND) speed += 1
        val offset = SceneOffset(positionX.sceneUnit, positionY.sceneUnit)
        body.position = offset
        collisionMask.position = offset
        if (arrived || rangeLeft < 0f) markDead()
    }

    fun markOutOfBoundsIfNeeded(worldWidthPixels: Int, worldHeightPixels: Int) {
        if (isDead) return
        if (positionX < 0 || positionY < 0 ||
            positionX >= worldWidthPixels || positionY >= worldHeightPixels
        ) {
            markDead()
        }
    }

    private fun markDead() {
        if (isDead) return
        isDead = true
        pendingExplosion = true
    }

    /** The blast lands on the target (legacy: around the target, or at the marker's spot). */
    fun drainExplosion(): Explosion? {
        if (!pendingExplosion) return null
        pendingExplosion = false
        val onTarget = rangeLeft >= 0f
        return if (onTarget) Explosion(targetX, targetY) else Explosion(positionX.toInt(), positionY.toInt())
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
        speed = speed,
        rangeLeft = rangeLeft,
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
        @SerialName("speed") val speed: Int = 0,
        @SerialName("rangeLeft") val rangeLeft: Float = ROCKET_RANGE_PX.toFloat(),
    ) : Serializable.State<ServerRocketActor> {
        override fun restore(): ServerRocketActor = ServerRocketActor(this)
        override fun serialize(): String = Json.encodeToString(this)
    }
}

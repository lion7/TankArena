package com.tankarena.sim

import com.tankarena.core.DetMath.hypot
import kotlinx.serialization.SerialName
import kotlin.math.roundToInt
import kotlinx.serialization.json.Json

/** Legacy `rck.dist`: a rocket that has flown this far without reaching its target blows up. */
internal const val ROCKET_RANGE_PX: Int = 3300
/** Legacy `rck.v` cap in px per second; the rocket starts at 0 and gains 1 px/s every tick. */
internal const val ROCKET_MAX_SPEED_PX_PER_SECOND: Int = 150
/** Legacy control_rocket(): the rocket detonates once it is closer than this to its target. */
internal const val ROCKET_ARRIVAL_PX: Int = 7
/** Legacy `check_obj_hit_radius(..., 10, HT_ROCKET, ...)`. */
internal const val ROCKET_BLAST_RADIUS_PX: Int = 10
/** Legacy `o->r` of a rocket. */
internal const val ROCKET_RADIUS_PX: Int = 3
private const val EXHAUST_OFFSET_PX = 11f

/** Something a rocket homes on: its current centre, or null once it is destroyed. */
internal fun interface RocketTarget {
    fun position(): Pair<Int, Int>?
}

/**
 * Legacy homing missile (src/objects/rocket.c): it heads straight for its target every tick,
 * accelerating from standstill, and explodes when it arrives or runs out of range. The target
 * is whatever was under the lock-on marker when it was launched, or else the marker's spot.
 */
class Rocket internal constructor(state: State) : Steppable,
    SceneObject<Rocket>, Placed {

    override val body: PointBody = state.body

    val ownerActorId: Long = state.ownerActorId
    val damage: Int = state.damage

    var positionX: Float = body.position.x
        private set
    var positionY: Float = body.position.y
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
    internal var ownerRef: Tank? = null
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
        val step = speed * STEP_SECONDS
        if (distance > 0f) {
            velocityX = dx / distance * step
            velocityY = dy / distance * step
        }
        positionX += velocityX
        positionY += velocityY
        rangeLeft -= step
        if (speed < ROCKET_MAX_SPEED_PX_PER_SECOND) speed += 1
        // Legacy control_rocket(): a puff of exhaust 11 px behind, every 10..29 ticks.
        smokeTicks = smokeTicks.afterStep()
        if (smokeTicks == 0) {
            val (fx, fy) = com.tankarena.core.LegacyDirections.unitVector(
                com.tankarena.core.LegacyDirections.fromAngle((velocityX * 1000).toInt(), (velocityY * 1000).toInt()),
            )
            pendingTrail = (positionX - fx * EXHAUST_OFFSET_PX).roundToInt() to (positionY - fy * EXHAUST_OFFSET_PX).roundToInt()
            smokeTicks = 10 + smokeRandom.nextInt(20)
        }
        val offset = ScenePoint(positionX.toFloat(), positionY.toFloat())
        body.position = offset
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

    private var smokeTicks: Int = 0
    private var pendingTrail: Pair<Int, Int>? = null
    private val smokeRandom = kotlin.random.Random(state.ownerActorId * 31 + state.targetX * 7 + state.targetY)

    /** Where the rocket left a puff of exhaust this step, if it did. */
    fun drainTrail(): Pair<Int, Int>? = pendingTrail.also { pendingTrail = null }

    /** Legacy control_warp(): put down at a warp's exit; it keeps homing on its target. */
    internal fun teleportTo(x: Int, y: Int) {
        positionX = x.toFloat()
        positionY = y.toFloat()
        body.position = ScenePoint(positionX, positionY)
    }

    /** Legacy hit_rocket(): gone without going off. */
    internal fun removeQuietly() {
        isDead = true
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
        return Explosion(targetX, targetY, onTarget = rangeLeft >= 0f)
    }

    /** Where it goes off: at the target (or the marker); [onTarget] false when it ran out of range. */
    data class Explosion(val x: Int, val y: Int, val onTarget: Boolean)

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
        @SerialName("body") val body: PointBody = PointBody(),
        @SerialName("ownerActorId") val ownerActorId: Long = 0L,
        @SerialName("damage") val damage: Int = 0,
        @SerialName("velocityX") val velocityX: Float = 0f,
        @SerialName("velocityY") val velocityY: Float = 0f,
        @SerialName("targetX") val targetX: Int = 0,
        @SerialName("targetY") val targetY: Int = 0,
        @SerialName("speed") val speed: Int = 0,
        @SerialName("rangeLeft") val rangeLeft: Float = ROCKET_RANGE_PX.toFloat(),
    ) : SceneObject.State<Rocket> {
        override fun restore(): Rocket = Rocket(this)
        override fun serialize(): String = Json.encodeToString(this)
    }
}

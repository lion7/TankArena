package com.tankarena.sim.kubriko.server

import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.collision.Collidable
import com.pandulapeter.kubriko.collision.mask.BoxCollisionMask
import com.pandulapeter.kubriko.sceneEditor.Editable
import com.pandulapeter.kubriko.serialization.Serializable
import com.pandulapeter.kubriko.serialization.typeSerializers.SerializableBoxBody
import com.tankarena.core.LegacyDirections
import kotlinx.serialization.SerialName
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.serialization.json.Json

/** Legacy `ti[0]` (src/objects/turret.c): delay 150 ticks, power 5, radius 200. */
private const val DEFAULT_FIRE_DELAY_TICKS: Int = 150
private const val DEFAULT_RANGE_PIXELS: Int = 200
private const val DEFAULT_POWER: Int = 5
/** Legacy control_turret(): the gun turns one step every 35 ticks. */
private const val TURN_DELAY_TICKS: Int = 35
/** Legacy create_bullet(): 200 px/s, i.e. 2 px per tick. */
private const val BULLET_SPEED_PX_PER_TICK: Float = 2f

/**
 * Legacy `turretinfo ti[]` (src/objects/turret.c): what each of the nine turret kinds fires and
 * where its guns sit, as (x, y) offsets from the centre with the gun pointing north. A turret
 * with two guns fires them in turn.
 */
internal class TurretKind(val rockets: Boolean, val guns: List<Pair<Int, Int>>, val shootAt: Int)

internal val TURRET_KINDS: List<TurretKind> = listOf(
    TurretKind(rockets = false, guns = listOf(0 to -15), shootAt = 3),
    TurretKind(rockets = true, guns = listOf(0 to -6), shootAt = 4),
    TurretKind(rockets = false, guns = listOf(0 to -14), shootAt = 1),
    TurretKind(rockets = false, guns = listOf(0 to -6), shootAt = 1),
    TurretKind(rockets = false, guns = listOf(0 to -14), shootAt = 1),
    TurretKind(rockets = false, guns = listOf(0 to -9), shootAt = 1),
    TurretKind(rockets = false, guns = listOf(0 to -6), shootAt = 1),
    TurretKind(rockets = false, guns = listOf(0 to -7), shootAt = 1),
    TurretKind(rockets = false, guns = listOf(-2 to -6, 2 to -6), shootAt = 1),
)

/**
 * Legacy loc_x()/loc_y() (src/check/math.c): an offset given for a north-facing picture, turned
 * clockwise to the 16-step [direction].
 */
internal fun rotateLegacyOffset(direction: Int, x: Int, y: Int): Pair<Int, Int> {
    val a = LegacyDirections.normalize(direction) * PI / 8.0
    return Math.round(cos(a) * x - sin(a) * y).toInt() to Math.round(sin(a) * x + cos(a) * y).toInt()
}

/**
 * A gun turret on a building or a wagon (legacy TURRET_TYPE, src/objects/turret.c). It aims at
 * the closest vehicle it may shoot at ([shootAt]), turns towards it one step at a time while it
 * is in range, and fires bullets or rockets when its gun points straight at it.
 */
class ServerTurretActor internal constructor(state: State) : Collidable, Editable<ServerTurretActor> {

    override val body: BoxBody = state.body
    override val collisionMask: BoxCollisionMask = BoxCollisionMask(
        initialSize = body.size,
        initialPosition = body.position,
        initialRotation = body.rotation,
    )

    var turretDirection: Int = state.turretDirection
        private set
    val team: Int = state.team
    var armor: Int = state.armor
    var cooldownTicks: Int = state.cooldownTicks
        private set
    /** Legacy `o->gc`: goal-counter percentage applied when this turret is destroyed. */
    var goalContribution: Int = state.goalContribution
        internal set
    val isAlive: Boolean get() = armor > 0
    /** Legacy `tur.power`, `tur.delay` and `tur.radius`, stored with the turret in the map. */
    val power: Int = state.power
    val fireDelayTicks: Int = state.delay
    val rangePixels: Int = state.radius
    /** Legacy `tur.type`: which of the nine kinds in [TURRET_KINDS], and so which pictures. */
    val turretType: Int = state.turretType.coerceIn(0, TURRET_KINDS.lastIndex)
    /** Legacy `tur.shootat`: 0 any vehicle, 1 good ones, 2 evil ones, 3 all but player 1's, 4 all but player 2's. */
    val shootAt: Int = state.shootAt
    /** Legacy `tur.fixed`: the gun never turns. */
    val fixed: Boolean = state.fixed
    /** Legacy `tur.forbid_dir`: bit d set means the turret holds its fire while facing d. */
    val forbidDirections: Int = state.forbidDirections
    private var activeGun: Int = 0
    private var turnCooldownTicks: Int = 0

    private var destroyedThisTick: Boolean = false

    val positionX: Int get() = (body.position.x.raw + body.size.width.raw / 2f).toInt()
    val positionY: Int get() = (body.position.y.raw + body.size.height.raw / 2f).toInt()

    private var pendingFireRequest: FireRequest? = null

    /** A shot from the gun at ([originX], [originY]): a bullet along [direction], or a rocket at [rocketTarget]. */
    data class FireRequest(
        val originX: Int,
        val originY: Int,
        val direction: Int,
        val velocityX: Float,
        val velocityY: Float,
        val damage: Int,
        val ttlTicks: Int,
        val chain: Boolean,
        val rocketTarget: Pair<Int, Int>? = null,
    )

    /** Legacy control_turret(). */
    fun step(tanks: List<ServerTankActor>) {
        if (armor <= 0) return
        cooldownTicks = (cooldownTicks - 1).coerceAtLeast(0)
        turnCooldownTicks = (turnCooldownTicks - 1).coerceAtLeast(0)
        val target = closestTarget(tanks) ?: return
        val dx = target.positionX - positionX
        val dy = target.positionY - positionY
        val wanted = LegacyDirections.fromAngle(dx, dy)
        val inRange = dx.toLong() * dx + dy.toLong() * dy < rangePixels.toLong() * rangePixels

        if (!fixed && turnCooldownTicks == 0 && turretDirection != wanted && inRange) {
            // Legacy go_left_or_right(): the shorter way round, clockwise on a tie.
            turretDirection = LegacyDirections.normalize(turretDirection + if ((turretDirection - wanted) and 15 < 8) -1 else 1)
            turnCooldownTicks = TURN_DELAY_TICKS
        }

        if (inRange && cooldownTicks == 0 && wanted == turretDirection &&
            (fixed || (forbidDirections shr turretDirection) and 1 == 0)
        ) {
            val kind = TURRET_KINDS[turretType]
            val (gunX, gunY) = kind.guns[activeGun % kind.guns.size]
            val (offsetX, offsetY) = rotateLegacyOffset(turretDirection, gunX, gunY)
            val (fx, fy) = LegacyDirections.unitVector(turretDirection)
            pendingFireRequest = FireRequest(
                originX = positionX + offsetX,
                originY = positionY + offsetY,
                direction = turretDirection,
                velocityX = fx * BULLET_SPEED_PX_PER_TICK,
                velocityY = fy * BULLET_SPEED_PX_PER_TICK,
                damage = power,
                ttlTicks = (rangePixels / BULLET_SPEED_PX_PER_TICK).toInt(),
                // Legacy: turrets with power below 2 fire chain-gun rounds.
                chain = power < 2,
                rocketTarget = if (kind.rockets) target.positionX to target.positionY else null,
            )
            activeGun = (activeGun + 1) % kind.guns.size
            cooldownTicks = fireDelayTicks
        }
    }

    /** Legacy control_turret(): the building under the turret is gone, so the turret goes too. */
    fun fall() {
        if (armor <= 0) return
        armor = 0
        destroyedThisTick = true
        pendingFireRequest = null
    }

    /** Applies hit damage; returns true when this hit destroyed the turret. */
    fun takeDamage(amount: Int): Boolean {
        if (amount <= 0 || armor <= 0) return false
        armor = (armor - amount).coerceAtLeast(0)
        if (armor == 0) {
            destroyedThisTick = true
            pendingFireRequest = null
            return true
        }
        return false
    }

    /** True exactly once after the turret is destroyed. */
    fun drainDestroyed(): Boolean {
        if (!destroyedThisTick) return false
        destroyedThisTick = false
        return true
    }

    fun drainFireRequest(): FireRequest? {
        val request = pendingFireRequest
        pendingFireRequest = null
        return request
    }

    override fun save(): State = State(
        body = body,
        turretDirection = turretDirection,
        team = team,
        armor = armor,
        cooldownTicks = cooldownTicks,
        goalContribution = goalContribution,
        power = power,
        delay = fireDelayTicks,
        radius = rangePixels,
        ownerRef = ownerRef,
        turretType = turretType,
        shootAt = shootAt,
        fixed = fixed,
        forbidDirections = forbidDirections,
    )

    /**
     * Legacy `o->owner`: the saved pointer of the wagon this turret rides on (make_turret(..., o)
     * in src/objects/train.c), or 0 for a turret on a building.
     */
    val ownerRef: Int = state.ownerRef

    /** Legacy control_train(): a turret on a wagon moves with it. */
    internal fun moveTo(x: Int, y: Int) {
        val half = body.size.width.raw / 2f
        val offset = com.pandulapeter.kubriko.types.SceneOffset((x - half).sceneUnit, (y - half).sceneUnit)
        body.position = offset
        collisionMask.position = offset
    }

    /** Legacy closest_tank(): the nearest live, visible vehicle this turret may shoot at, at any range. */
    private fun closestTarget(tanks: List<ServerTankActor>): ServerTankActor? {
        var best: ServerTankActor? = null
        var bestDistSq = Long.MAX_VALUE
        for (tank in tanks) {
            if (tank.armor <= 0 || tank.cloaked) continue
            val skip = when (shootAt) {
                1 -> tank.team != 0
                2 -> tank.team == 0
                3 -> tank.playerIndex == 0
                4 -> tank.playerIndex == 1
                else -> false
            }
            if (skip) continue
            val dx = (tank.positionX - positionX).toLong()
            val dy = (tank.positionY - positionY).toLong()
            val distSq = dx * dx + dy * dy
            if (distSq < bestDistSq) {
                best = tank
                bestDistSq = distSq
            }
        }
        return best
    }

    @kotlinx.serialization.Serializable
    data class State(
        @SerialName("body") val body: SerializableBoxBody = BoxBody(),
        @SerialName("turretDirection") val turretDirection: Int = 0,
        @SerialName("team") val team: Int = 1,
        @SerialName("armor") val armor: Int = DEFAULT_TURRET_ARMOR,
        @SerialName("cooldownTicks") val cooldownTicks: Int = 0,
        @SerialName("goalContribution") val goalContribution: Int = 0,
        @SerialName("power") val power: Int = DEFAULT_POWER,
        @SerialName("delay") val delay: Int = DEFAULT_FIRE_DELAY_TICKS,
        @SerialName("radius") val radius: Int = DEFAULT_RANGE_PIXELS,
        @SerialName("ownerRef") val ownerRef: Int = 0,
        @SerialName("turretType") val turretType: Int = 0,
        @SerialName("shootAt") val shootAt: Int = 1,
        @SerialName("fixed") val fixed: Boolean = false,
        @SerialName("forbidDirections") val forbidDirections: Int = 0,
    ) : Serializable.State<ServerTurretActor> {
        override fun restore(): ServerTurretActor = ServerTurretActor(this)
        override fun serialize(): String = Json.encodeToString(this)
    }

    companion object {
        /**
         * Legacy turrets have no armor of their own: they fall with the structure they stand on
         * (control_turret()). Until structures are destructible a turret takes as many hits as a tank.
         */
        const val DEFAULT_TURRET_ARMOR: Int = 30
    }
}

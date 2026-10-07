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
import kotlinx.serialization.json.Json

/** Legacy `ti[0]` (src/objects/turret.c): delay 150 ticks, power 5, radius 200. */
private const val DEFAULT_FIRE_DELAY_TICKS: Int = 150
private const val DEFAULT_RANGE_PIXELS: Int = 200
private const val DEFAULT_POWER: Int = 5
/** Legacy control_turret(): the gun turns one step every 35 ticks. */
private const val TURN_DELAY_TICKS: Int = 35
/** Legacy create_bullet(): 200 px/s, i.e. 2 px per tick. */
private const val BULLET_SPEED_PX_PER_TICK: Float = 2f

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
    private var turnCooldownTicks: Int = 0

    private var destroyedThisTick: Boolean = false

    val positionX: Int get() = (body.position.x.raw + body.size.width.raw / 2f).toInt()
    val positionY: Int get() = (body.position.y.raw + body.size.height.raw / 2f).toInt()

    private var pendingFireRequest: FireRequest? = null

    data class FireRequest(
        val originX: Int,
        val originY: Int,
        val velocityX: Float,
        val velocityY: Float,
        val damage: Int,
        val ttlTicks: Int,
        val chain: Boolean,
    )

    fun step(tanks: List<ServerTankActor>) {
        if (armor <= 0) return
        cooldownTicks = (cooldownTicks - 1).coerceAtLeast(0)
        val target = nearestTargetInRange(tanks) ?: return

        val desired = LegacyDirections.fromFacing(
            facingX = (target.positionX - positionX).coerceIn(-1, 1),
            facingY = (target.positionY - positionY).coerceIn(-1, 1),
        )
        turnCooldownTicks = (turnCooldownTicks - 1).coerceAtLeast(0)
        if (turnCooldownTicks == 0 && turretDirection != desired) {
            turretDirection = LegacyDirections.stepToward(turretDirection, desired)
            turnCooldownTicks = TURN_DELAY_TICKS
        }

        if (cooldownTicks == 0 && turretDirection == desired) {
            val (fx, fy) = LegacyDirections.unitVector(turretDirection)
            pendingFireRequest = FireRequest(
                originX = positionX + (fx * 12).toInt(),
                originY = positionY + (fy * 12).toInt(),
                velocityX = fx * BULLET_SPEED_PX_PER_TICK,
                velocityY = fy * BULLET_SPEED_PX_PER_TICK,
                damage = power,
                ttlTicks = (rangePixels / BULLET_SPEED_PX_PER_TICK).toInt(),
                // Legacy: turrets with power below 2 fire chain-gun rounds.
                chain = power < 2,
            )
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

    private fun nearestTargetInRange(tanks: List<ServerTankActor>): ServerTankActor? {
        var best: ServerTankActor? = null
        var bestDistSq = Long.MAX_VALUE
        val rangeSq = rangePixels.toLong() * rangePixels.toLong()
        for (tank in tanks) {
            // Legacy closest_tank(): a cloaked vehicle is invisible to turrets.
            if (tank.armor <= 0 || tank.cloaked) continue
            if (tank.team == team) continue
            val dx = (tank.positionX - positionX).toLong()
            val dy = (tank.positionY - positionY).toLong()
            val distSq = dx * dx + dy * dy
            if (distSq <= rangeSq && distSq < bestDistSq) {
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

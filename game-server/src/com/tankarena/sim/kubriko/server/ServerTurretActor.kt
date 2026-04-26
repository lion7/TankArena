package com.tankarena.sim.kubriko.server

import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.collision.Collidable
import com.pandulapeter.kubriko.collision.mask.BoxCollisionMask
import com.pandulapeter.kubriko.sceneEditor.Editable
import com.pandulapeter.kubriko.serialization.Serializable
import com.pandulapeter.kubriko.serialization.typeSerializers.SerializableBoxBody
import com.tankarena.core.LegacyDirections
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

private const val FIRE_DELAY_TICKS: Int = 30
private const val RANGE_PIXELS: Int = 240
private const val PRIMARY_DAMAGE: Int = 25
private const val PROJECTILE_SPEED: Float = 8f

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

    val positionX: Int get() = (body.position.x.raw + body.size.width.raw / 2f).toInt()
    val positionY: Int get() = (body.position.y.raw + body.size.height.raw / 2f).toInt()

    private var pendingFireRequest: FireRequest? = null

    data class FireRequest(
        val originX: Int,
        val originY: Int,
        val velocityX: Int,
        val velocityY: Int,
        val damage: Int,
    )

    fun step(tanks: List<ServerTankActor>) {
        if (armor <= 0) return
        cooldownTicks = (cooldownTicks - 1).coerceAtLeast(0)
        val target = nearestTargetInRange(tanks) ?: return

        val desired = LegacyDirections.fromFacing(
            facingX = (target.positionX - positionX).coerceIn(-1, 1),
            facingY = (target.positionY - positionY).coerceIn(-1, 1),
        )
        turretDirection = LegacyDirections.stepToward(turretDirection, desired)

        if (cooldownTicks == 0 && turretDirection == desired) {
            val (vx, vy) = LegacyDirections.toVelocityStep(turretDirection, PROJECTILE_SPEED)
            val vxi = vx.toInt().let { if (it == 0 && vx != 0f) (if (vx > 0) 1 else -1) else it }
            val vyi = vy.toInt().let { if (it == 0 && vy != 0f) (if (vy > 0) 1 else -1) else it }
            pendingFireRequest = FireRequest(
                originX = positionX + vxi * 2,
                originY = positionY + vyi * 2,
                velocityX = vxi,
                velocityY = vyi,
                damage = PRIMARY_DAMAGE,
            )
            cooldownTicks = FIRE_DELAY_TICKS
        }
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
    )

    private fun nearestTargetInRange(tanks: List<ServerTankActor>): ServerTankActor? {
        var best: ServerTankActor? = null
        var bestDistSq = Long.MAX_VALUE
        val rangeSq = RANGE_PIXELS.toLong() * RANGE_PIXELS.toLong()
        for (tank in tanks) {
            if (tank.armor <= 0) continue
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
        @SerialName("armor") val armor: Int = 100,
        @SerialName("cooldownTicks") val cooldownTicks: Int = 0,
    ) : Serializable.State<ServerTurretActor> {
        override fun restore(): ServerTurretActor = ServerTurretActor(this)
        override fun serialize(): String = Json.encodeToString(this)
    }
}

package com.tankarena.sim.kubriko.server

import com.pandulapeter.kubriko.actor.body.PointBody
import com.pandulapeter.kubriko.actor.traits.Dynamic
import com.pandulapeter.kubriko.collision.Collidable
import com.pandulapeter.kubriko.collision.mask.CircleCollisionMask
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.serialization.Serializable
import com.pandulapeter.kubriko.serialization.typeSerializers.SerializablePointBody
import com.pandulapeter.kubriko.types.SceneOffset
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

internal const val MINE_ACTIVATION_TICKS: Int = 100
internal const val LIGHT_MINE_RADIUS_PX: Int = 30

class ServerMineActor internal constructor(state: State) :
    Collidable,
    Dynamic,
    Serializable<ServerMineActor> {

    override val body: PointBody = state.body
    override val collisionMask: CircleCollisionMask = CircleCollisionMask(
        initialPosition = body.position,
        initialRadius = state.radius.toFloat().sceneUnit,
    )

    val ownerActorId: Long = state.ownerActorId
    val damage: Int = state.damage
    val radius: Int = state.radius

    var positionX: Int = body.position.x.raw.toInt()
        private set
    var positionY: Int = body.position.y.raw.toInt()
        private set
    var activationTicksRemaining: Int = state.activationTicksRemaining
        private set
    var isDead: Boolean = false
        private set

    private var detonationPending: Boolean = false
    private var detonationVictim: ServerTankActor? = null

    val isActive: Boolean get() = activationTicksRemaining <= 0 && !isDead

    override fun update(deltaTimeInMilliseconds: Int) {
        if (activationTicksRemaining > 0) activationTicksRemaining -= 1
    }

    internal fun detonateOn(victim: ServerTankActor) {
        if (isDead) return
        detonationVictim = victim
        detonationPending = true
        isDead = true
    }

    fun drainDetonation(): Detonation? {
        if (!detonationPending) return null
        detonationPending = false
        val victim = detonationVictim
        detonationVictim = null
        return Detonation(positionX, positionY, victim)
    }

    data class Detonation(val x: Int, val y: Int, val victim: ServerTankActor?)

    override fun save(): State = State(
        body = body,
        ownerActorId = ownerActorId,
        damage = damage,
        radius = radius,
        activationTicksRemaining = activationTicksRemaining,
    )

    @kotlinx.serialization.Serializable
    data class State(
        @SerialName("body") val body: SerializablePointBody = PointBody(),
        @SerialName("ownerActorId") val ownerActorId: Long = 0L,
        @SerialName("damage") val damage: Int = 0,
        @SerialName("radius") val radius: Int = LIGHT_MINE_RADIUS_PX,
        @SerialName("activationTicksRemaining") val activationTicksRemaining: Int = MINE_ACTIVATION_TICKS,
    ) : Serializable.State<ServerMineActor> {
        override fun restore(): ServerMineActor = ServerMineActor(this)
        override fun serialize(): String = Json.encodeToString(this)
    }
}

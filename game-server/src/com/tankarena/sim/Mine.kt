package com.tankarena.sim

import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

internal const val MINE_ACTIVATION_TICKS: Int = 100
internal const val LIGHT_MINE_RADIUS_PX: Int = 30

class Mine internal constructor(state: State) : Steppable,
    SceneObject<Mine>, Placed {

    override val body: PointBody = state.body

    val ownerActorId: Long = state.ownerActorId
    val damage: Int = state.damage
    val radius: Int = state.radius

    var positionX: Int = body.position.x.toInt()
        private set
    var positionY: Int = body.position.y.toInt()
        private set
    var activationTicksRemaining: Int = state.activationTicksRemaining
        private set
    var isDead: Boolean = false
        private set

    /** True while a detonation waits to be played out (it must not be cleaned up before that). */
    var detonationPending: Boolean = false
        private set
    private var detonationVictim: Tank? = null

    val isActive: Boolean get() = activationTicksRemaining <= 0 && !isDead

    override fun update(deltaTimeInMilliseconds: Int) {
        activationTicksRemaining = activationTicksRemaining.afterStep()
    }

    internal fun detonateOn(victim: Tank?) {
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

    data class Detonation(val x: Int, val y: Int, val victim: Tank?)

    override fun save(): State = State(
        body = body,
        ownerActorId = ownerActorId,
        damage = damage,
        radius = radius,
        activationTicksRemaining = activationTicksRemaining,
    )

    @kotlinx.serialization.Serializable
    data class State(
        @SerialName("body") val body: PointBody = PointBody(),
        @SerialName("ownerActorId") val ownerActorId: Long = 0L,
        @SerialName("damage") val damage: Int = 0,
        @SerialName("radius") val radius: Int = LIGHT_MINE_RADIUS_PX,
        @SerialName("activationTicksRemaining") val activationTicksRemaining: Int = MINE_ACTIVATION_TICKS,
    ) : SceneObject.State<Mine> {
        override fun restore(): Mine = Mine(this)
        override fun serialize(): String = Json.encodeToString(this)
    }
}

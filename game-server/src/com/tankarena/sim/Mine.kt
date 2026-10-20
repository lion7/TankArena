package com.tankarena.sim

import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

internal const val MINE_ACTIVATION_TICKS: Int = 100
internal const val LIGHT_MINE_RADIUS_PX: Int = 30
/** Legacy check_obj_collide(): a heavy mine reaches 50 px further than a vehicle's radius (a light one 3). */
internal const val HEAVY_MINE_REACH_PX: Int = 50

class Mine internal constructor(state: State) : Steppable,
    SceneObject<Mine>, Placed {

    override val body: PointBody = state.body

    val ownerActorId: Long = state.ownerActorId
    val damage: Int = state.damage
    val radius: Int = state.radius
    /** Legacy `mi.type == HEAVY_MINE`: it fires eight small rockets instead of blowing up. */
    val heavy: Boolean = state.heavy

    /** How far beyond a vehicle's radius the mine is set off. */
    val triggerReach: Int get() = if (heavy) HEAVY_MINE_REACH_PX else MINE_RADIUS_PX

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
        heavy = heavy,
    )

    @kotlinx.serialization.Serializable
    data class State(
        @SerialName("body") val body: PointBody = PointBody(),
        @SerialName("ownerActorId") val ownerActorId: Long = 0L,
        @SerialName("damage") val damage: Int = 0,
        @SerialName("radius") val radius: Int = LIGHT_MINE_RADIUS_PX,
        @SerialName("activationTicksRemaining") val activationTicksRemaining: Int = MINE_ACTIVATION_TICKS,
        @SerialName("heavy") val heavy: Boolean = false,
    ) : SceneObject.State<Mine> {
        override fun restore(): Mine = Mine(this)
        override fun serialize(): String = Json.encodeToString(this)
    }
}

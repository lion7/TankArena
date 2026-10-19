package com.tankarena.sim.kubriko.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

/**
 * Conditional trigger lock.
 *
 * `activation`: 0 = structure destroyed at location, 1 = tracked object destroyed.
 * `target`: 0 = blow structure, 1 = destroy object, 2 = remove object.
 * `lockX`, `lockY`: monitored location for structure-activation.
 */
class ServerLockActor internal constructor(state: State) : SceneObject<ServerLockActor>, Placed {

    override val body: BoxBody = state.body

    val activation: Int = state.activation
    val target: Int = state.target
    val lockX: Int = state.lockX
    val lockY: Int = state.lockY
    var isFired: Boolean = state.isFired
    /** Legacy `o->gc`: applied when the lock is used up (remove_object()). */
    val goalContribution: Int = state.goalContribution

    override fun save(): State = State(
        body = body,
        activation = activation,
        target = target,
        lockX = lockX,
        lockY = lockY,
        isFired = isFired,
        goalContribution = goalContribution,
    )

    @kotlinx.serialization.Serializable
    data class State(
        @SerialName("body") val body: BoxBody = BoxBody(),
        @SerialName("activation") val activation: Int = 0,
        @SerialName("target") val target: Int = 0,
        @SerialName("lockX") val lockX: Int = 0,
        @SerialName("lockY") val lockY: Int = 0,
        @SerialName("isFired") val isFired: Boolean = false,
        @SerialName("goalContribution") val goalContribution: Int = 0,
    ) : SceneObject.State<ServerLockActor> {
        override fun restore(): ServerLockActor = ServerLockActor(this)
        override fun serialize(): String = Json.encodeToString(this)
    }
}

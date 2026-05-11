package com.tankarena.sim.kubriko.server

import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.collision.Collidable
import com.pandulapeter.kubriko.collision.mask.BoxCollisionMask
import com.pandulapeter.kubriko.sceneEditor.Editable
import com.pandulapeter.kubriko.serialization.Serializable
import com.pandulapeter.kubriko.serialization.typeSerializers.SerializableBoxBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

/**
 * Conditional trigger lock.
 *
 * `activation`: 0 = structure destroyed at location, 1 = tracked object destroyed.
 * `target`: 0 = blow structure, 1 = destroy object, 2 = remove object.
 * `lockX`, `lockY`: monitored location for structure-activation.
 */
class ServerLockActor internal constructor(state: State) : Collidable, Editable<ServerLockActor> {

    override val body: BoxBody = state.body
    override val collisionMask: BoxCollisionMask = BoxCollisionMask(
        initialSize = body.size,
        initialPosition = body.position,
        initialRotation = body.rotation,
    )

    val activation: Int = state.activation
    val target: Int = state.target
    val lockX: Int = state.lockX
    val lockY: Int = state.lockY
    var isFired: Boolean = state.isFired

    override fun save(): State = State(
        body = body,
        activation = activation,
        target = target,
        lockX = lockX,
        lockY = lockY,
        isFired = isFired,
    )

    @kotlinx.serialization.Serializable
    data class State(
        @SerialName("body") val body: SerializableBoxBody = BoxBody(),
        @SerialName("activation") val activation: Int = 0,
        @SerialName("target") val target: Int = 0,
        @SerialName("lockX") val lockX: Int = 0,
        @SerialName("lockY") val lockY: Int = 0,
        @SerialName("isFired") val isFired: Boolean = false,
    ) : Serializable.State<ServerLockActor> {
        override fun restore(): ServerLockActor = ServerLockActor(this)
        override fun serialize(): String = Json.encodeToString(this)
    }
}

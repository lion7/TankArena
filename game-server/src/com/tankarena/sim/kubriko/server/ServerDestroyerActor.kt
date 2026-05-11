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
 * Area destroyer trigger.
 *
 * `radius`: destruction radius.
 * `immediate`: if true, activates on map load.
 * `what`: 1=walls, 2=objects, 3=both.
 */
class ServerDestroyerActor internal constructor(state: State) : Collidable, Editable<ServerDestroyerActor> {

    override val body: BoxBody = state.body
    override val collisionMask: BoxCollisionMask = BoxCollisionMask(
        initialSize = body.size,
        initialPosition = body.position,
        initialRotation = body.rotation,
    )

    val radius: Int = state.radius
    val what: Int = state.what
    val immediate: Boolean = state.immediate
    var isFired: Boolean = state.isFired

    override fun save(): State = State(
        body = body,
        radius = radius,
        what = what,
        immediate = immediate,
        isFired = isFired,
    )

    @kotlinx.serialization.Serializable
    data class State(
        @SerialName("body") val body: SerializableBoxBody = BoxBody(),
        @SerialName("radius") val radius: Int = 0,
        @SerialName("what") val what: Int = 3,
        @SerialName("immediate") val immediate: Boolean = false,
        @SerialName("isFired") val isFired: Boolean = false,
    ) : Serializable.State<ServerDestroyerActor> {
        override fun restore(): ServerDestroyerActor = ServerDestroyerActor(this)
        override fun serialize(): String = Json.encodeToString(this)
    }
}

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
 * Train engine or wagon. Follows rails at max speed 50.
 * Engine armor=60, wagon armor=40.
 */
class ServerTrainActor internal constructor(state: State) : Collidable, Editable<ServerTrainActor> {

    override val body: BoxBody = state.body
    override val collisionMask: BoxCollisionMask = BoxCollisionMask(
        initialSize = body.size,
        initialPosition = body.position,
        initialRotation = body.rotation,
    )

    val isEngine: Boolean = state.isEngine
    var armor: Int = state.armor
    var alive: Boolean = state.alive
    var positionX: Int = state.x
    var positionY: Int = state.y

    override fun save(): State = State(
        body = body,
        isEngine = isEngine,
        armor = armor,
        alive = alive,
        x = positionX,
        y = positionY,
    )

    @kotlinx.serialization.Serializable
    data class State(
        @SerialName("body") val body: SerializableBoxBody = BoxBody(),
        @SerialName("isEngine") val isEngine: Boolean = true,
        @SerialName("armor") val armor: Int = 60,
        @SerialName("alive") val alive: Boolean = true,
        @SerialName("x") val x: Int = 0,
        @SerialName("y") val y: Int = 0,
    ) : Serializable.State<ServerTrainActor> {
        override fun restore(): ServerTrainActor = ServerTrainActor(this)
        override fun serialize(): String = Json.encodeToString(this)
    }
}

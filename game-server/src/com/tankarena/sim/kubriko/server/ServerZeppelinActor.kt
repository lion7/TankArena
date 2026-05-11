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
 * Zeppelin. Flies across screen at height 20, speed 10. Wraps around map edges.
 */
class ServerZeppelinActor internal constructor(state: State) : Collidable, Editable<ServerZeppelinActor> {

    override val body: BoxBody = state.body
    override val collisionMask: BoxCollisionMask = BoxCollisionMask(
        initialSize = body.size,
        initialPosition = body.position,
        initialRotation = body.rotation,
    )

    var alive: Boolean = state.alive
    var positionX: Int = state.x
    var positionY: Int = state.y

    override fun save(): State = State(
        body = body,
        alive = alive,
        x = positionX,
        y = positionY,
    )

    @kotlinx.serialization.Serializable
    data class State(
        @SerialName("body") val body: SerializableBoxBody = BoxBody(),
        @SerialName("alive") val alive: Boolean = true,
        @SerialName("x") val x: Int = 0,
        @SerialName("y") val y: Int = 0,
    ) : Serializable.State<ServerZeppelinActor> {
        override fun restore(): ServerZeppelinActor = ServerZeppelinActor(this)
        override fun serialize(): String = Json.encodeToString(this)
    }
}

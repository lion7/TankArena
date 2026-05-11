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
 * B52 bomber. Spawns at map edge, flies across screen, drops bombs.
 * Armor=30, 10 bombs per run, 500-tick interval.
 */
class ServerB52Actor internal constructor(state: State) : Collidable, Editable<ServerB52Actor> {

    override val body: BoxBody = state.body
    override val collisionMask: BoxCollisionMask = BoxCollisionMask(
        initialSize = body.size,
        initialPosition = body.position,
        initialRotation = body.rotation,
    )

    var armor: Int = state.armor
    var alive: Boolean = state.alive
    var positionX: Int = state.x
    var positionY: Int = state.y
    var bombCount: Int = state.bombCount
    var bombTimer: Int = 0

    override fun save(): State = State(
        body = body,
        armor = armor,
        alive = alive,
        x = positionX,
        y = positionY,
        bombCount = bombCount,
    )

    @kotlinx.serialization.Serializable
    data class State(
        @SerialName("body") val body: SerializableBoxBody = BoxBody(),
        @SerialName("armor") val armor: Int = 30,
        @SerialName("alive") val alive: Boolean = true,
        @SerialName("x") val x: Int = 0,
        @SerialName("y") val y: Int = 0,
        @SerialName("bombCount") val bombCount: Int = 0,
    ) : Serializable.State<ServerB52Actor> {
        override fun restore(): ServerB52Actor = ServerB52Actor(this)
        override fun serialize(): String = Json.encodeToString(this)
    }
}

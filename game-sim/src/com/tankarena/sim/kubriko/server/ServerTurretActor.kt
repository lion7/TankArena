package com.tankarena.sim.kubriko.server

import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.collision.Collidable
import com.pandulapeter.kubriko.collision.mask.BoxCollisionMask
import com.pandulapeter.kubriko.serialization.Serializable
import com.pandulapeter.kubriko.serialization.typeSerializers.SerializableBoxBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

class ServerTurretActor internal constructor(state: State) : Collidable, Serializable<ServerTurretActor> {

    override val body: BoxBody = state.body
    override val collisionMask: BoxCollisionMask = BoxCollisionMask(
        initialSize = body.size,
        initialPosition = body.position,
        initialRotation = body.rotation,
    )

    var turretDirection: Int = state.turretDirection
    val team: Int = state.team
    var armor: Int = state.armor

    override fun save(): State = State(
        body = body,
        turretDirection = turretDirection,
        team = team,
        armor = armor,
    )

    @kotlinx.serialization.Serializable
    data class State(
        @SerialName("body") val body: SerializableBoxBody = BoxBody(),
        @SerialName("turretDirection") val turretDirection: Int = 0,
        @SerialName("team") val team: Int = 1,
        @SerialName("armor") val armor: Int = 100,
    ) : Serializable.State<ServerTurretActor> {
        override fun restore(): ServerTurretActor = ServerTurretActor(this)
        override fun serialize(): String = Json.encodeToString(this)
    }
}

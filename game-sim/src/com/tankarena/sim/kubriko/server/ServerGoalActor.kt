package com.tankarena.sim.kubriko.server

import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.collision.Collidable
import com.pandulapeter.kubriko.collision.mask.BoxCollisionMask
import com.pandulapeter.kubriko.serialization.Serializable
import com.pandulapeter.kubriko.serialization.typeSerializers.SerializableBoxBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

class ServerGoalActor internal constructor(state: State) : Collidable, Serializable<ServerGoalActor> {

    override val body: BoxBody = state.body
    override val collisionMask: BoxCollisionMask = BoxCollisionMask(
        initialSize = body.size,
        initialPosition = body.position,
        initialRotation = body.rotation,
    )

    val who: Int = state.who
    val contribution: Int = state.contribution
    val radius: Int = state.radius
    var isClaimed: Boolean = state.isClaimed

    override fun save(): State = State(
        body = body,
        who = who,
        contribution = contribution,
        radius = radius,
        isClaimed = isClaimed,
    )

    @kotlinx.serialization.Serializable
    data class State(
        @SerialName("body") val body: SerializableBoxBody = BoxBody(),
        @SerialName("who") val who: Int = 0,
        @SerialName("contribution") val contribution: Int = 100,
        @SerialName("radius") val radius: Int = 16,
        @SerialName("isClaimed") val isClaimed: Boolean = false,
    ) : Serializable.State<ServerGoalActor> {
        override fun restore(): ServerGoalActor = ServerGoalActor(this)
        override fun serialize(): String = Json.encodeToString(this)
    }
}

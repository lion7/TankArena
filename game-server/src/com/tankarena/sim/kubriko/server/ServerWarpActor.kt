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
 * Teleport warp point. Pairs input and output locations.
 * Radius: 15 pixels. Cooldown prevents teleport loops.
 */
class ServerWarpActor internal constructor(state: State) : Collidable, Editable<ServerWarpActor> {

    override val body: BoxBody = state.body
    override val collisionMask: BoxCollisionMask = BoxCollisionMask(
        initialSize = body.size,
        initialPosition = body.position,
        initialRotation = body.rotation,
    )

    val targetX: Int = state.targetX
    val targetY: Int = state.targetY
    var cooldownTicks: Int = state.cooldownTicks

    override fun save(): State = State(
        body = body,
        targetX = targetX,
        targetY = targetY,
        cooldownTicks = cooldownTicks,
    )

    @kotlinx.serialization.Serializable
    data class State(
        @SerialName("body") val body: SerializableBoxBody = BoxBody(),
        @SerialName("targetX") val targetX: Int = 0,
        @SerialName("targetY") val targetY: Int = 0,
        @SerialName("cooldownTicks") val cooldownTicks: Int = 0,
    ) : Serializable.State<ServerWarpActor> {
        override fun restore(): ServerWarpActor = ServerWarpActor(this)
        override fun serialize(): String = Json.encodeToString(this)
    }
}

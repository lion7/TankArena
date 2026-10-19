package com.tankarena.sim.kubriko.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

/**
 * Teleport warp point. Pairs input and output locations.
 * Radius: 15 pixels. Cooldown prevents teleport loops.
 */
class ServerWarpActor internal constructor(state: State) : SceneObject<ServerWarpActor>, Placed {

    override val body: BoxBody = state.body

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
        @SerialName("body") val body: BoxBody = BoxBody(),
        @SerialName("targetX") val targetX: Int = 0,
        @SerialName("targetY") val targetY: Int = 0,
        @SerialName("cooldownTicks") val cooldownTicks: Int = 0,
    ) : SceneObject.State<ServerWarpActor> {
        override fun restore(): ServerWarpActor = ServerWarpActor(this)
        override fun serialize(): String = Json.encodeToString(this)
    }
}

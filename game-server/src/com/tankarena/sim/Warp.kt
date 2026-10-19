package com.tankarena.sim

import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

/**
 * Teleport warp point. Pairs input and output locations.
 * Radius: 15 pixels. Cooldown prevents teleport loops.
 */
class Warp internal constructor(state: State) : SceneObject<Warp>, Placed {

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
    ) : SceneObject.State<Warp> {
        override fun restore(): Warp = Warp(this)
        override fun serialize(): String = Json.encodeToString(this)
    }
}

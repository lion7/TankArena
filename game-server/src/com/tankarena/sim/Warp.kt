package com.tankarena.sim

import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

/**
 * Legacy WARP_TYPE (src/objects/warp.c): whatever comes within 15 px is put down at the exit
 * ([targetX], [targetY]).
 */
class Warp internal constructor(state: State) : SceneObject<Warp>, Placed {

    override val body: BoxBody = state.body

    val targetX: Int = state.targetX
    val targetY: Int = state.targetY

    override fun save(): State = State(
        body = body,
        targetX = targetX,
        targetY = targetY,
    )

    @kotlinx.serialization.Serializable
    data class State(
        @SerialName("body") val body: BoxBody = BoxBody(),
        @SerialName("targetX") val targetX: Int = 0,
        @SerialName("targetY") val targetY: Int = 0,
    ) : SceneObject.State<Warp> {
        override fun restore(): Warp = Warp(this)
        override fun serialize(): String = Json.encodeToString(this)
    }
}

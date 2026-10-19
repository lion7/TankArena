package com.tankarena.sim

import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

class Wall internal constructor(state: State) : SceneObject<Wall>, Placed {

    override val body: BoxBody = state.body

    override fun save(): State = State(body = body)

    @kotlinx.serialization.Serializable
    data class State(
        @SerialName("body") val body: BoxBody = BoxBody(),
    ) : SceneObject.State<Wall> {
        override fun restore(): Wall = Wall(this)
        override fun serialize(): String = Json.encodeToString(this)
    }
}

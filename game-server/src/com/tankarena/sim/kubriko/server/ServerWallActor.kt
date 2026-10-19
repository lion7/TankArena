package com.tankarena.sim.kubriko.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

class ServerWallActor internal constructor(state: State) : SceneObject<ServerWallActor>, Placed {

    override val body: BoxBody = state.body

    override fun save(): State = State(body = body)

    @kotlinx.serialization.Serializable
    data class State(
        @SerialName("body") val body: BoxBody = BoxBody(),
    ) : SceneObject.State<ServerWallActor> {
        override fun restore(): ServerWallActor = ServerWallActor(this)
        override fun serialize(): String = Json.encodeToString(this)
    }
}

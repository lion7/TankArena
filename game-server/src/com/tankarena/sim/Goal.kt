package com.tankarena.sim

import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

class Goal internal constructor(state: State) : SceneObject<Goal>, Placed {

    override val body: BoxBody = state.body

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
        @SerialName("body") val body: BoxBody = BoxBody(),
        @SerialName("who") val who: Int = 0,
        @SerialName("contribution") val contribution: Int = 100,
        @SerialName("radius") val radius: Int = 16,
        @SerialName("isClaimed") val isClaimed: Boolean = false,
    ) : SceneObject.State<Goal> {
        override fun restore(): Goal = Goal(this)
        override fun serialize(): String = Json.encodeToString(this)
    }
}

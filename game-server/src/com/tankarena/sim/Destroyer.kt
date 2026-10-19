package com.tankarena.sim

import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

/**
 * Area destroyer trigger.
 *
 * `radius`: destruction radius.
 * `immediate`: if true, activates on map load.
 * `what`: 1=walls, 2=objects, 3=both.
 */
class Destroyer internal constructor(state: State) : SceneObject<Destroyer>, Placed {

    override val body: BoxBody = state.body

    val radius: Int = state.radius
    val what: Int = state.what
    val immediate: Boolean = state.immediate
    var isFired: Boolean = state.isFired
    /** Legacy `o->gc`: applied when the destroyer goes off (remove_object()). */
    val goalContribution: Int = state.goalContribution

    override fun save(): State = State(
        body = body,
        radius = radius,
        what = what,
        immediate = immediate,
        isFired = isFired,
        goalContribution = goalContribution,
    )

    @kotlinx.serialization.Serializable
    data class State(
        @SerialName("body") val body: BoxBody = BoxBody(),
        @SerialName("radius") val radius: Int = 0,
        @SerialName("what") val what: Int = 3,
        @SerialName("immediate") val immediate: Boolean = false,
        @SerialName("isFired") val isFired: Boolean = false,
        @SerialName("goalContribution") val goalContribution: Int = 0,
    ) : SceneObject.State<Destroyer> {
        override fun restore(): Destroyer = Destroyer(this)
        override fun serialize(): String = Json.encodeToString(this)
    }
}

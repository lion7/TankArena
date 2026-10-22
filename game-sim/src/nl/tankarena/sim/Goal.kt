package nl.tankarena.sim

import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

class Goal internal constructor(state: State) : SceneObject<Goal>, Placed {

    override val body: BoxBody = state.body

    val who: Int = state.who
    val contribution: Int = state.contribution
    val radius: Int = state.radius
    var isClaimed: Boolean = state.isClaimed
    /** Legacy `o->target` of a `who` 3 goal: the [Tank.ref] of the vehicle that reaches it. */
    val targetRef: Int = state.targetRef
    /** The linked vehicle, while it is on the map (legacy remove_object() clears the pointer). */
    internal var target: Tank? = null

    override fun save(): State = State(
        body = body,
        who = who,
        contribution = contribution,
        radius = radius,
        isClaimed = isClaimed,
        targetRef = targetRef,
    )

    @kotlinx.serialization.Serializable
    data class State(
        @SerialName("body") val body: BoxBody = BoxBody(),
        @SerialName("who") val who: Int = 0,
        @SerialName("contribution") val contribution: Int = 100,
        @SerialName("radius") val radius: Int = 16,
        @SerialName("isClaimed") val isClaimed: Boolean = false,
        @SerialName("targetRef") val targetRef: Int = 0,
    ) : SceneObject.State<Goal> {
        override fun restore(): Goal = Goal(this)
        override fun serialize(): String = Json.encodeToString(this)
    }
}

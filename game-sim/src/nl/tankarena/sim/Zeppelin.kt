package nl.tankarena.sim

import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

/**
 * The zeppelin of the unregistered original (legacy make_zeppelin(), original/src/objects/zeppelin.c):
 * an 8×4-tile airship at height 20 drifting west at 10 px/s and coming back in from the east
 * edge at a random height on the map. Its picture alternates every 100 ticks.
 */
class Zeppelin internal constructor(state: State) : SceneObject<Zeppelin>, Placed {

    override val body: BoxBody = state.body

    var alive: Boolean = state.alive
    var positionX: Int = state.x
    var positionY: Int = state.y
    internal var exactX: Float = state.x.toFloat()
    /** Legacy `z.img`: which of the two pictures is shown. */
    var frame: Int = 0
    internal var frameCooldownTicks: Int = 100

    override fun save(): State = State(
        body = body,
        alive = alive,
        x = positionX,
        y = positionY,
    )

    @kotlinx.serialization.Serializable
    data class State(
        @SerialName("body") val body: BoxBody = BoxBody(),
        @SerialName("alive") val alive: Boolean = true,
        @SerialName("x") val x: Int = 0,
        @SerialName("y") val y: Int = 0,
    ) : SceneObject.State<Zeppelin> {
        override fun restore(): Zeppelin = Zeppelin(this)
        override fun serialize(): String = Json.encodeToString(this)
    }
}

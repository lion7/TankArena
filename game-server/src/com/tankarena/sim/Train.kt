package com.tankarena.sim

import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

/** Legacy `railstype` (src/include/types.h). */
internal const val RAILS_HORIZONTAL: Int = 0
internal const val RAILS_VERTICAL: Int = 1
internal const val RAILS_CIRCLE: Int = 2

/**
 * A train engine or wagon (legacy TRAIN_TYPE / WAGON_TYPE, src/objects/train.c). The map stores
 * each car's whole `trainstruct`: the rail segment it is on (`rails`), the one it heads for
 * (`end`), and its neighbours in the train. The match drives the cars (Match
 * stepTrains()); the engine pulls the cars behind it, which keep 31 px apart.
 */
class Train internal constructor(state: State) : SceneObject<Train>, Placed {

    override val body: BoxBody = state.body

    val isEngine: Boolean = state.isEngine
    var armor: Int = state.armor
    var alive: Boolean = state.alive
    /** Centre of the car; the legacy code works with the top-left corner, 16 px up and left. */
    var positionX: Int = state.x
    var positionY: Int = state.y

    /** Legacy `this_structure`, `info.front`, `info.rear`: saved pointers that tie a train together. */
    val ref: Int = state.ref
    val frontRef: Int = state.frontRef
    val rearRef: Int = state.rearRef
    internal var front: Train? = null
    internal var rear: Train? = null
    /** Legacy `info.turret`: the turret riding this wagon. */
    internal var turret: Turret? = null
    /** Legacy `funct.control != NULL`: this car leads (an engine, or a wagon that lost its front). */
    var driving: Boolean = state.driving
    /** Legacy `motion.v` in px/s and `motion.count`, the tick the current segment started. */
    var speed: Int = state.speed
    var segmentStartTick: Long = 0L
    /** Legacy `motion.dir` (16 steps). */
    var direction: Int = state.direction
    /** Legacy `info.picture`: 0 the engine, 1..8 the wagons (7 carries an A-bomb, 8 a turret). */
    val picture: Int = state.picture
    /** Legacy `gen.nsmoke != tnever`: only engines smoke. */
    val smokes: Boolean = state.smokes
    var smokeCooldownTicks: Int = 0
    /** Legacy `gen.nv`: the leader changes speed every 100 ticks. */
    var speedChangeCooldownTicks: Int = SPEED_CHANGE_TICKS

    var railsStart: Float = state.railsStart
    var railsType: Int = state.railsType
    var railsDir: Int = state.railsDir
    var railsX: Int = state.railsX
    var railsY: Int = state.railsY
    var endType: Int = state.endType
    var endDir: Int = state.endDir
    var endX: Int = state.endX
    var endY: Int = state.endY

    /** Legacy `o->gc`: applied when the car is destroyed (remove_object()). */
    var goalContribution: Int = state.goalContribution

    /** Legacy `o->x`/`o->y`: the top-left corner. */
    var left: Int
        get() = positionX - HALF
        set(value) {
            positionX = value + HALF
        }
    var top: Int
        get() = positionY - HALF
        set(value) {
            positionY = value + HALF
        }

    internal fun syncBody() {
        val offset = ScenePoint((positionX - body.size.width / 2f), (positionY - body.size.height / 2f))
        body.position = offset
    }

    override fun save(): State = State(
        body = body,
        isEngine = isEngine,
        armor = armor,
        alive = alive,
        x = positionX,
        y = positionY,
        ref = ref,
        frontRef = frontRef,
        rearRef = rearRef,
        driving = driving,
        speed = speed,
        direction = direction,
        picture = picture,
        smokes = smokes,
        railsStart = railsStart,
        railsType = railsType,
        railsDir = railsDir,
        railsX = railsX,
        railsY = railsY,
        endType = endType,
        endDir = endDir,
        endX = endX,
        endY = endY,
        goalContribution = goalContribution,
    )

    @kotlinx.serialization.Serializable
    data class State(
        @SerialName("body") val body: BoxBody = BoxBody(),
        @SerialName("isEngine") val isEngine: Boolean = true,
        @SerialName("armor") val armor: Int = 60,
        @SerialName("alive") val alive: Boolean = true,
        @SerialName("x") val x: Int = 0,
        @SerialName("y") val y: Int = 0,
        @SerialName("ref") val ref: Int = 0,
        @SerialName("frontRef") val frontRef: Int = 0,
        @SerialName("rearRef") val rearRef: Int = 0,
        @SerialName("driving") val driving: Boolean = true,
        @SerialName("speed") val speed: Int = 0,
        @SerialName("direction") val direction: Int = 0,
        @SerialName("picture") val picture: Int = 0,
        @SerialName("smokes") val smokes: Boolean = true,
        @SerialName("railsStart") val railsStart: Float = 0f,
        @SerialName("railsType") val railsType: Int = RAILS_HORIZONTAL,
        @SerialName("railsDir") val railsDir: Int = 1,
        @SerialName("railsX") val railsX: Int = 0,
        @SerialName("railsY") val railsY: Int = 0,
        @SerialName("endType") val endType: Int = RAILS_HORIZONTAL,
        @SerialName("endDir") val endDir: Int = 1,
        @SerialName("endX") val endX: Int = 0,
        @SerialName("endY") val endY: Int = 0,
        @SerialName("goalContribution") val goalContribution: Int = 0,
    ) : SceneObject.State<Train> {
        override fun restore(): Train = Train(this)
        override fun serialize(): String = Json.encodeToString(this)
    }

    companion object {
        private const val HALF = 16
        internal const val SPEED_CHANGE_TICKS = 100
        /** Legacy create_object(TRAIN_TYPE, ..., r = 12). */
        internal const val RADIUS = 12
    }
}

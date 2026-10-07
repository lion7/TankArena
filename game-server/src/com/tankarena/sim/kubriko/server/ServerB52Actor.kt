package com.tankarena.sim.kubriko.server

import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.collision.Collidable
import com.pandulapeter.kubriko.collision.mask.BoxCollisionMask
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.sceneEditor.Editable
import com.pandulapeter.kubriko.serialization.Serializable
import com.pandulapeter.kubriko.serialization.typeSerializers.SerializableBoxBody
import com.pandulapeter.kubriko.types.SceneOffset
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

/**
 * A B52 bomber (legacy B52_TYPE, src/objects/b52.c). It waits on the ground, takes off along
 * its heading ([Phase.LIFTING]), then crosses the map at height 19 dropping a stick of ten
 * bombs every 650 ticks, re-entering from a random edge each time it leaves ([Phase.FLYING]).
 * Shot down it sinks, burning, and lies as a wreck. The match drives it (stepB52s()).
 */
class ServerB52Actor internal constructor(state: State) : Collidable, Editable<ServerB52Actor> {

    /** Legacy control functions: lift_b52, fly_b52, crash_b52, control_b52_wreck. */
    enum class Phase { LIFTING, FLYING, CRASHING, WRECK }

    override val body: BoxBody = state.body
    override val collisionMask: BoxCollisionMask = BoxCollisionMask(
        initialSize = body.size,
        initialPosition = body.position,
        initialRotation = body.rotation,
    )

    var armor: Int = state.armor
    var alive: Boolean = state.alive
    var positionX: Int = state.x
    var positionY: Int = state.y
    var bombCount: Int = state.bombCount
    /** Legacy `b52.dir`: 0 north, 1 east, 2 south, 3 west. */
    var heading: Int = state.heading
    /** Legacy `o->goal`: applied when the bomber hits the ground. */
    var goalContribution: Int = state.goalContribution

    var phase: Phase = Phase.LIFTING
    /** Legacy `o->h`: 1 on the ground, 19 cruising. */
    var height: Int = 1
    /** Legacy `b52.v`, px/s. */
    var speed: Int = 0
    /** Legacy `b52.bomb`: 0..3 bay frames while a stick is coming, 4 doors shut. */
    var bomb: Int = 4
    internal var exactX: Float = positionX.toFloat()
    internal var exactY: Float = positionY.toFloat()
    internal var speedCooldownTicks: Int = 0
    internal var heightCooldownTicks: Int = 0
    internal var bombCooldownTicks: Int = 0
    internal var explosionCooldownTicks: Int = 0

    internal fun moveTo(x: Float, y: Float) {
        exactX = x
        exactY = y
        positionX = Math.round(x)
        positionY = Math.round(y)
        val offset = SceneOffset((positionX - body.size.width.raw / 2f).sceneUnit, (positionY - body.size.height.raw / 2f).sceneUnit)
        body.position = offset
        collisionMask.position = offset
    }

    override fun save(): State = State(
        body = body,
        armor = armor,
        alive = alive,
        x = positionX,
        y = positionY,
        bombCount = bombCount,
        heading = heading,
        goalContribution = goalContribution,
    )

    @kotlinx.serialization.Serializable
    data class State(
        @SerialName("body") val body: SerializableBoxBody = BoxBody(),
        @SerialName("armor") val armor: Int = 30,
        @SerialName("alive") val alive: Boolean = true,
        @SerialName("x") val x: Int = 0,
        @SerialName("y") val y: Int = 0,
        @SerialName("bombCount") val bombCount: Int = 0,
        @SerialName("heading") val heading: Int = 0,
        @SerialName("goalContribution") val goalContribution: Int = 0,
    ) : Serializable.State<ServerB52Actor> {
        override fun restore(): ServerB52Actor = ServerB52Actor(this)
        override fun serialize(): String = Json.encodeToString(this)
    }

    companion object {
        /** Legacy create_object(B52_TYPE, ..., r = 20). */
        internal const val RADIUS = 20
        const val CRUISE_HEIGHT = 19
    }
}

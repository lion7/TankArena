package com.tankarena.sim.kubriko.server

import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.collision.Collidable
import com.pandulapeter.kubriko.collision.mask.BoxCollisionMask
import com.pandulapeter.kubriko.sceneEditor.Editable
import com.pandulapeter.kubriko.serialization.Serializable
import com.pandulapeter.kubriko.serialization.typeSerializers.SerializableBoxBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

/**
 * AI weapon enforcer. Forces AI tanks within radius to use a specific weapon.
 *
 * `radius`: enforcement radius.
 * `good`: target good AI tanks.
 * `bad`: target evil AI tanks.
 * `weapon`: weapon index to enforce.
 * `delay`: auto-fire delay in ticks.
 */
class ServerEnforcerActor internal constructor(state: State) : Collidable, Editable<ServerEnforcerActor> {

    override val body: BoxBody = state.body
    override val collisionMask: BoxCollisionMask = BoxCollisionMask(
        initialSize = body.size,
        initialPosition = body.position,
        initialRotation = body.rotation,
    )

    val radius: Int = state.radius
    val weapon: Int = state.weapon
    val delay: Int = state.delay
    val good: Boolean = state.good
    val bad: Boolean = state.bad

    override fun save(): State = State(
        body = body,
        radius = radius,
        weapon = weapon,
        delay = delay,
        good = good,
        bad = bad,
    )

    @kotlinx.serialization.Serializable
    data class State(
        @SerialName("body") val body: SerializableBoxBody = BoxBody(),
        @SerialName("radius") val radius: Int = 0,
        @SerialName("weapon") val weapon: Int = 0,
        @SerialName("delay") val delay: Int = 0,
        @SerialName("good") val good: Boolean = false,
        @SerialName("bad") val bad: Boolean = false,
    ) : Serializable.State<ServerEnforcerActor> {
        override fun restore(): ServerEnforcerActor = ServerEnforcerActor(this)
        override fun serialize(): String = Json.encodeToString(this)
    }
}

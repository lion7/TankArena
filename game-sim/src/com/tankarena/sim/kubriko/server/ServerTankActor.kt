package com.tankarena.sim.kubriko.server

import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.collision.Collidable
import com.pandulapeter.kubriko.collision.mask.BoxCollisionMask
import com.pandulapeter.kubriko.serialization.Serializable
import com.pandulapeter.kubriko.serialization.typeSerializers.SerializableBoxBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

class ServerTankActor internal constructor(state: State) : Collidable, Serializable<ServerTankActor> {

    override val body: BoxBody = state.body
    override val collisionMask: BoxCollisionMask = BoxCollisionMask(
        initialSize = body.size,
        initialPosition = body.position,
        initialRotation = body.rotation,
    )

    var bodyDirection: Int = state.bodyDirection
    var turretDirection: Int = state.turretDirection
    val playerIndex: Int = state.playerIndex
    val tankType: Int = state.tankType
    var armor: Int = state.armor
    var fuel: Int = state.fuel
    var lives: Int = state.lives
    val team: Int = state.team

    override fun save(): State = State(
        body = body,
        bodyDirection = bodyDirection,
        turretDirection = turretDirection,
        playerIndex = playerIndex,
        tankType = tankType,
        armor = armor,
        fuel = fuel,
        lives = lives,
        team = team,
    )

    @kotlinx.serialization.Serializable
    data class State(
        @SerialName("body") val body: SerializableBoxBody = BoxBody(),
        @SerialName("bodyDirection") val bodyDirection: Int = 0,
        @SerialName("turretDirection") val turretDirection: Int = 0,
        @SerialName("playerIndex") val playerIndex: Int = -1,
        @SerialName("tankType") val tankType: Int = 0,
        @SerialName("armor") val armor: Int = 100,
        @SerialName("fuel") val fuel: Int = 100,
        @SerialName("lives") val lives: Int = 1,
        @SerialName("team") val team: Int = 0,
    ) : Serializable.State<ServerTankActor> {
        override fun restore(): ServerTankActor = ServerTankActor(this)
        override fun serialize(): String = Json.encodeToString(this)
    }
}

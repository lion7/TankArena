package com.tankarena.sim

import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

/**
 * Capture-the-flag object.
 *
 * `flagType`: 0 = Player 1, 1 = Player 2.
 * `number`: sequential index for maps with multiple flags.
 *
 * The flag follows its carrier tank. Game ends when an enemy carries
 * the flag into the carrier's base zone (handled in Match).
 */
class Flag internal constructor(state: State) : SceneObject<Flag>, Placed {

    override val body: BoxBody = state.body

    val flagType: Int = state.flagType
    val number: Int = state.number
    var isCarried: Boolean = state.isCarried
    var carrierActorId: Long? = state.carrierActorId
    var homeX: Int = state.homeX
    var homeY: Int = state.homeY

    override fun save(): State = State(
        body = body,
        flagType = flagType,
        number = number,
        isCarried = isCarried,
        carrierActorId = carrierActorId,
        homeX = homeX,
        homeY = homeY,
    )

    @kotlinx.serialization.Serializable
    data class State(
        @SerialName("body") val body: BoxBody = BoxBody(),
        @SerialName("flagType") val flagType: Int = 0,
        @SerialName("number") val number: Int = 0,
        @SerialName("isCarried") val isCarried: Boolean = false,
        @SerialName("carrierActorId") val carrierActorId: Long? = null,
        @SerialName("homeX") val homeX: Int = 0,
        @SerialName("homeY") val homeY: Int = 0,
    ) : SceneObject.State<Flag> {
        override fun restore(): Flag = Flag(this)
        override fun serialize(): String = Json.encodeToString(this)
    }
}

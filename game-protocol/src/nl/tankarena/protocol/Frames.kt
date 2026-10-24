package nl.tankarena.protocol

import kotlinx.serialization.Serializable

@Serializable
data class InputFrame(
    val playerId: Int,
    val inputSequence: Long,
    val forward: Boolean = false,
    val reverse: Boolean = false,
    val turnLeft: Boolean = false,
    val turnRight: Boolean = false,
    val aimLeft: Boolean = false,
    val aimRight: Boolean = false,
    val firePrimary: Boolean = false,
    val fireSecondary: Boolean = false,
    val shield: Boolean = false,
    val cycleWeaponLeft: Boolean = false,
    val cycleWeaponRight: Boolean = false,
    /** Legacy F1–F10: switch control to the friendly tank with this id (0 = no request). */
    val selectTankId: Int = 0,
    /** Extended: switch control to the next friendly tank, as F1–F10 would by its id. */
    val nextTank: Boolean = false,
    /**
     * Extended (mouse): the map position the player points at, in world pixels, while it matters
     * (the rocket marker); -1 for none.
     */
    val pointX: Int = -1,
    val pointY: Int = -1,
)

@Serializable
enum class Team { PLAYER, ENEMY, NEUTRAL }

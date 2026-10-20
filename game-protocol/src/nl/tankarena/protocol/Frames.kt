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
)

@Serializable
enum class Team { PLAYER, ENEMY, NEUTRAL }

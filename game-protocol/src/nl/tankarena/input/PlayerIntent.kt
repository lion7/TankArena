package nl.tankarena.input

import kotlinx.serialization.Serializable

@Serializable
data class PlayerIntentFrame(
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

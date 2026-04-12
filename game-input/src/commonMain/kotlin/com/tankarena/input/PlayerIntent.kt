package com.tankarena.input

import kotlinx.serialization.Serializable

@Serializable
data class PlayerIntentFrame(
    val throttle: Int = 0,
    val steer: Int = 0,
    val aimX: Int = 0,
    val aimY: Int = 0,
    val firePrimary: Boolean = false,
    val fireSecondary: Boolean = false,
    val shield: Boolean = false,
    val cycleWeaponLeft: Boolean = false,
    val cycleWeaponRight: Boolean = false,
)


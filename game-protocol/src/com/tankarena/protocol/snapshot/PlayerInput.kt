package com.tankarena.protocol.snapshot

import kotlinx.serialization.Serializable

/**
 * Device-agnostic player input for one tick. Mirror of `com.tankarena.input.PlayerIntentFrame`;
 * duplicated here as part of collapsing `:game-input` into `:game-protocol`. The original
 * lives on until consumers migrate, then it is deleted.
 */
@Serializable
data class PlayerIntent(
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
)

@Serializable
data class ClientInput(
    val playerId: Int,
    val inputSequence: Long,
    val intent: PlayerIntent,
)

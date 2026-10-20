package com.tankarena.ui.compose.menu

import androidx.compose.ui.input.key.Key
import kotlinx.serialization.Serializable

/**
 * Legacy setup.c "Select Controls": RELATIVE turns the vehicle with LEFT/RIGHT and drives with
 * UP/DOWN; DIRECT drives the way the keys point.
 */
@Serializable
enum class ControlMode { RELATIVE, DIRECT }

/** Legacy `keys[i][...]`: the ten keys of a player, in the setup program's order. */
enum class ControlAction(val label: String) {
    UP("UP"),
    DOWN("DOWN"),
    LEFT("LEFT"),
    RIGHT("RIGHT"),
    FIRE("FIRE"),
    AIM_LEFT("AIM LEFT"),
    AIM_RIGHT("AIM RIGHT"),
    WEAPON_LEFT("WEAPON LEFT"),
    WEAPON_RIGHT("WEAPON RIGHT"),
    SHIELD("SHIELD"),
}

/** One player's controls (legacy TANK.CFG `plN_mode` and `keyN_*`); [keys] are key codes, 0 for none. */
@Serializable
data class PlayerControls(
    val mode: ControlMode = ControlMode.RELATIVE,
    val keys: List<Long>,
) {
    fun key(action: ControlAction): Key? = keys.getOrNull(action.ordinal)?.takeIf { it != 0L }?.let(::Key)
}

/**
 * The default keys. Player one: K , M . drive, Z fire, X/C turret, S/D weapon, A shield (legacy
 * controls.c). Player two (and the single player): the grey cursor block, as DATA/KEYS.HLP shows.
 */
val DEFAULT_CONTROLS: List<PlayerControls> = listOf(
    PlayerControls(
        keys = listOf(Key.K, Key.Comma, Key.M, Key.Period, Key.Z, Key.X, Key.C, Key.S, Key.D, Key.A).map { it.keyCode },
    ),
    PlayerControls(
        keys = listOf(
            Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight, Key.Delete,
            Key.MoveEnd, Key.PageDown, Key.MoveHome, Key.PageUp, Key.Insert,
        ).map { it.keyCode },
    ),
)

/**
 * Legacy setup.c "Configurate Keys": [key] goes to [player]'s [action] and is taken from every other
 * slot of both players; null clears the slot (DEL).
 */
fun List<PlayerControls>.withKey(player: Int, action: ControlAction, key: Key?): List<PlayerControls> {
    val code = key?.keyCode ?: 0L
    return mapIndexed { index, controls ->
        controls.copy(
            keys = controls.keys.mapIndexed { slot, existing ->
                when {
                    index == player && slot == action.ordinal -> code
                    code != 0L && existing == code -> 0L
                    else -> existing
                }
            },
        )
    }
}

fun List<PlayerControls>.withMode(player: Int, mode: ControlMode): List<PlayerControls> =
    mapIndexed { index, controls -> if (index == player) controls.copy(mode = mode) else controls }

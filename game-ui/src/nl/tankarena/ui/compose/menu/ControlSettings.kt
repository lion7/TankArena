package nl.tankarena.ui.compose.menu

import androidx.compose.ui.input.key.Key
import kotlinx.serialization.Serializable

/**
 * Legacy setup.c "Select Controls": RELATIVE turns the vehicle with LEFT/RIGHT and drives with
 * UP/DOWN; DIRECT drives the way the keys point.
 */
@Serializable
enum class ControlMode { RELATIVE, DIRECT }

/** Legacy `key_or_joy[i]`: what a player steers with. A joystick is a gamepad today (ADR 0026). */
@Serializable
enum class ControlDevice { KEYBOARD, JOYSTICK }

/** Legacy setup.c "Configure joystick": the four things a joystick's buttons do. */
enum class JoystickAction(val label: String) { FIRE("FIRE"), AIM("AIM"), WEAPON("WEAPON"), SHIELD("SHIELD") }

/** A gamepad's buttons in the order of their bits in a [PlayerControls.joystickButtons] mask. */
val PAD_BUTTONS: List<String> = listOf("A", "B", "X", "Y", "LB", "RB", "BACK", "START")

/** A button mask as the setup shows it: `A`, `A+X`, or `-` for none (legacy "1-3-"). */
fun padButtonsText(mask: Int): String =
    PAD_BUTTONS.indices.filter { mask and (1 shl it) != 0 }.joinToString("+") { PAD_BUTTONS[it] }.ifEmpty { "-" }

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

/**
 * One player's controls (legacy TANK.CFG `plN_mode`, `keyN_*` and `joy[i]`); [keys] are key codes, 0
 * for none. With a [device] of JOYSTICK a gamepad steers too: its stick has a dead zone of
 * [sensitivity] (0..100, legacy default 50) and [joystickButtons] are the button masks for FIRE, AIM,
 * WEAPON and SHIELD (a mask of several buttons means pressing them together).
 */
@Serializable
data class PlayerControls(
    val mode: ControlMode = ControlMode.RELATIVE,
    val keys: List<Long>,
    val device: ControlDevice = ControlDevice.KEYBOARD,
    val sensitivity: Int = DEFAULT_SENSITIVITY,
    val joystickButtons: List<Int> = DEFAULT_JOYSTICK_BUTTONS,
) {
    fun buttons(action: JoystickAction): Int = joystickButtons.getOrElse(action.ordinal) { 0 }

    companion object {
        const val DEFAULT_SENSITIVITY = 50

        /** Legacy `joy[1].but`: buttons 1, 2, 3 and 4 (A, B, X, Y). */
        val DEFAULT_JOYSTICK_BUTTONS = listOf(1, 2, 4, 8)
    }

    fun key(action: ControlAction): Key? = keys.getOrNull(action.ordinal)?.takeIf { it != 0L }?.let(::Key)
}

/**
 * The default keys. Player one: K , M . drive, Z fire, X/C turret, S/D weapon, A shield (legacy
 * controls.c). Player two (and the single player): the grey cursor block, as original/DATA/KEYS.HLP shows.
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
 * Extended's player one (keyboard and mouse): WASD drives the way it is pressed, Space fires, Shift
 * shields, Q and E switch weapons, Z and C turn the turret for those without a mouse.
 */
val MOUSE_PLAYER_CONTROLS: PlayerControls = PlayerControls(
    keys = listOf(Key.W, Key.S, Key.A, Key.D, Key.Spacebar, Key.Z, Key.C, Key.Q, Key.E, Key.ShiftLeft).map { it.keyCode },
    mode = ControlMode.DIRECT,
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

fun List<PlayerControls>.withDevice(player: Int, device: ControlDevice): List<PlayerControls> =
    mapIndexed { index, controls -> if (index == player) controls.copy(device = device) else controls }

/** Legacy setup.c: the sensitivity goes in steps of 5 between 0 and 100. */
fun List<PlayerControls>.withSensitivity(player: Int, sensitivity: Int): List<PlayerControls> =
    mapIndexed { index, controls -> if (index == player) controls.copy(sensitivity = sensitivity.coerceIn(0, 100)) else controls }

/**
 * Legacy configure_joy(): [mask] goes to [player]'s [action] and is taken from every slot of both
 * players that had the same buttons; 0 clears the slot (DEL).
 */
fun List<PlayerControls>.withJoystickButtons(player: Int, action: JoystickAction, mask: Int): List<PlayerControls> =
    mapIndexed { index, controls ->
        controls.copy(
            joystickButtons = JoystickAction.entries.map { slot ->
                val existing = controls.buttons(slot)
                when {
                    index == player && slot == action -> mask
                    mask != 0 && existing == mask -> 0
                    else -> existing
                }
            },
        )
    }

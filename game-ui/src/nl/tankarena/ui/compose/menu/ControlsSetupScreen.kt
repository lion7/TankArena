package nl.tankarena.ui.compose.menu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import nl.tankarena.input.keyName
import nl.tankarena.ui.compose.legacy.LegacyColors
import nl.tankarena.ui.compose.legacy.Mode13View

/**
 * Legacy setup.c "Select Controls", "Configurate Keys" and "Configure joystick" (TANK -SETUP): each
 * player's controller (keyboard or joystick, a gamepad today), control mode and ten keys; Tab shows
 * the joystick page with its four buttons and sensitivity. ENTER changes a setting or waits for a key
 * or buttons, which are then taken from every other slot; DEL clears; ESC asks whether to save.
 */
@Composable
fun ControlsSetupScreen(
    initial: List<PlayerControls>,
    onDone: (List<PlayerControls>?) -> Unit,
    modifier: Modifier = Modifier,
    /** The gamepads' buttons held now (a mask per pad); polled while waiting for joystick buttons. */
    readPadButtons: () -> List<Int> = { emptyList() },
) {
    var controls by remember { mutableStateOf(initial) }
    var player by remember { mutableIntStateOf(0) }
    var joystickPage by remember { mutableStateOf(false) }
    /** Keys page: 0 the controller, 1 the mode, 2..11 the keys. Joystick page: 0..3 the buttons, 4 the sensitivity. */
    var row by remember { mutableIntStateOf(0) }
    var waitingForKey by remember { mutableStateOf(false) }
    var waitingForButtons by remember { mutableStateOf(false) }
    var adjusting by remember { mutableStateOf(false) }
    var askingToSave by remember { mutableStateOf(false) }
    val rows = if (joystickPage) JoystickAction.entries.size + 1 else ControlAction.entries.size + 2

    // Legacy configure_joy(): the buttons pressed together, taken once they are all let go.
    if (waitingForButtons) {
        LaunchedEffect(player, row) {
            var held = 0
            while (true) {
                val now = withFrameNanos { readPadButtons().fold(0) { mask, pad -> mask or pad } }
                held = held or now
                if (held != 0 && now == 0) {
                    controls = controls.withJoystickButtons(player, JoystickAction.entries[row], held)
                    waitingForButtons = false
                    break
                }
            }
        }
    }

    Mode13View(
        modifier = modifier,
        onKey = { event ->
            when {
                askingToSave -> when (event.key) {
                    Key.Y -> onDone(controls)
                    Key.N -> onDone(null)
                    else -> return@Mode13View false
                }
                waitingForKey -> {
                    if (event.key != Key.Escape) {
                        controls = controls.withKey(player, ControlAction.entries[row - 2], event.key)
                    }
                    waitingForKey = false
                }
                waitingForButtons -> if (event.key == Key.Escape) waitingForButtons = false
                adjusting -> when (event.key) {
                    // Legacy: shown as 100 - sensitivity, so RIGHT makes the stick more sensitive.
                    Key.DirectionLeft -> controls = controls.withSensitivity(player, controls[player].sensitivity + 5)
                    Key.DirectionRight -> controls = controls.withSensitivity(player, controls[player].sensitivity - 5)
                    Key.Enter, Key.NumPadEnter, Key.Escape -> adjusting = false
                    else -> return@Mode13View false
                }
                else -> when (event.key) {
                    Key.DirectionUp -> row = (row - 1 + rows) % rows
                    Key.DirectionDown -> row = (row + 1) % rows
                    Key.DirectionLeft, Key.DirectionRight -> player = 1 - player
                    Key.Tab -> {
                        joystickPage = !joystickPage
                        row = 0
                    }
                    Key.Enter, Key.NumPadEnter -> when {
                        joystickPage && row == JoystickAction.entries.size -> adjusting = true
                        joystickPage -> waitingForButtons = true
                        row == 0 -> {
                            val device = controls[player].device
                            controls = controls.withDevice(player, if (device == ControlDevice.KEYBOARD) ControlDevice.JOYSTICK else ControlDevice.KEYBOARD)
                        }
                        row == 1 -> {
                            val mode = controls[player].mode
                            controls = controls.withMode(player, if (mode == ControlMode.RELATIVE) ControlMode.DIRECT else ControlMode.RELATIVE)
                        }
                        else -> waitingForKey = true
                    }
                    Key.Delete -> when {
                        joystickPage && row < JoystickAction.entries.size -> controls = controls.withJoystickButtons(player, JoystickAction.entries[row], 0)
                        !joystickPage && row >= 2 -> controls = controls.withKey(player, ControlAction.entries[row - 2], null)
                    }
                    Key.Escape -> askingToSave = true
                    else -> return@Mode13View false
                }
            }
            true
        },
    ) {
        panel()
        addBar(8, 8, 304, 184, LegacyColors.BAR_ADD)
        bar(13, 13, 294, 174, LegacyColors.BLACK)
        bigTextCentre(if (joystickPage) "JOYSTICK" else "CONTROLS", 160, 16, LegacyColors.HIGH)
        for (p in 0..1) {
            val left = 18 + p * 140
            writeText(left, 38, LegacyColors.HIGH, "PLAYER ${p + 1}")
            for (r in 0 until rows) {
                val y = 50 + r * 9
                val selected = p == player && r == row
                val c = controls[p]
                val (label, value) = if (joystickPage) {
                    if (r < JoystickAction.entries.size) {
                        val action = JoystickAction.entries[r]
                        action.label to if (selected && waitingForButtons) "PRESS.." else padButtonsText(c.buttons(action))
                    } else {
                        "SENSITIVITY" to (if (selected && adjusting) "< ${100 - c.sensitivity} >" else "${100 - c.sensitivity}")
                    }
                } else {
                    when (r) {
                        0 -> "CONTROL" to c.device.name
                        1 -> "MODE" to c.mode.name
                        else -> ControlAction.entries[r - 2].label to
                            if (selected && waitingForKey) "PRESS.." else c.key(ControlAction.entries[r - 2])?.let(::legacyKeyName) ?: "-"
                    }
                }
                val color = if (selected) LegacyColors.HIGH else LegacyColors.NORMAL
                writeText(left, y, color, (if (selected) "> " else "  ") + label)
                writeText(left + 92, y, color, value)
            }
        }
        val help = when {
            askingToSave -> listOf("SAVE SETTINGS? (Y/N)")
            joystickPage -> listOf(
                "ENTER: SET   DEL: NO BUTTON   TAB: KEYS",
                "BUTTONS PRESSED TOGETHER COUNT AS ONE",
                "SENSITIVITY: ENTER, THEN LEFT/RIGHT",
            )
            else -> listOf(
                "ENTER: CHANGE  DEL: NO KEY  TAB: JOYSTICK",
                "RELATIVE: LEFT/RIGHT TURN, UP/DOWN DRIVE",
                "DIRECT: THE VEHICLE DRIVES THE WAY YOU PRESS",
            )
        }
        help.forEachIndexed { i, line -> writeText((320 - line.length * 6) / 2, 158 + i * 8, LegacyColors.HIGH, line) }
    }
}

/** A key's name as the setup program would print it, at most 8 characters. */
private fun legacyKeyName(key: Key): String = keyName(key).uppercase().take(8)

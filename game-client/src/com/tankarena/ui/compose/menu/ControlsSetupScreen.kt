package com.tankarena.ui.compose.menu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import com.tankarena.ui.compose.legacy.LegacyColors
import com.tankarena.ui.compose.legacy.Mode13View

/**
 * Legacy setup.c "Select Controls" and "Configurate Keys" (TANK -SETUP): both players' control mode
 * and ten keys. ENTER changes the mode or waits for a key, which is then taken from every other slot;
 * DEL clears a key; ESC asks whether to save.
 */
@Composable
fun ControlsSetupScreen(
    initial: List<PlayerControls>,
    onDone: (List<PlayerControls>?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var controls by remember { mutableStateOf(initial) }
    var player by remember { mutableIntStateOf(0) }
    /** 0 is the mode, 1..10 the keys. */
    var row by remember { mutableIntStateOf(0) }
    var waitingForKey by remember { mutableStateOf(false) }
    var askingToSave by remember { mutableStateOf(false) }
    val rows = ControlAction.entries.size + 1

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
                        controls = controls.withKey(player, ControlAction.entries[row - 1], event.key)
                    }
                    waitingForKey = false
                }
                else -> when (event.key) {
                    Key.DirectionUp -> row = (row - 1 + rows) % rows
                    Key.DirectionDown -> row = (row + 1) % rows
                    Key.DirectionLeft, Key.DirectionRight -> player = 1 - player
                    Key.Enter, Key.NumPadEnter -> if (row == 0) {
                        val mode = controls[player].mode
                        controls = controls.withMode(player, if (mode == ControlMode.RELATIVE) ControlMode.DIRECT else ControlMode.RELATIVE)
                    } else {
                        waitingForKey = true
                    }
                    Key.Delete -> if (row > 0) controls = controls.withKey(player, ControlAction.entries[row - 1], null)
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
        bigTextCentre("CONTROLS", 160, 16, LegacyColors.HIGH)
        for (p in 0..1) {
            val left = 18 + p * 140
            writeText(left, 38, LegacyColors.HIGH, "PLAYER ${p + 1}")
            for (r in 0 until rows) {
                val y = 50 + r * 9
                val selected = p == player && r == row
                val label = if (r == 0) "MODE" else ControlAction.entries[r - 1].label
                val value = when {
                    selected && waitingForKey -> "PRESS.."
                    r == 0 -> controls[p].mode.name
                    else -> controls[p].key(ControlAction.entries[r - 1])?.let(::keyName) ?: "-"
                }
                val color = if (selected) LegacyColors.HIGH else LegacyColors.NORMAL
                writeText(left, y, color, (if (selected) "> " else "  ") + label)
                writeText(left + 92, y, color, value)
            }
        }
        val help = if (askingToSave) {
            listOf("SAVE SETTINGS? (Y/N)")
        } else {
            listOf(
                "ENTER: CHANGE   DEL: NO KEY   ESC: DONE",
                "RELATIVE: LEFT/RIGHT TURN, UP/DOWN DRIVE",
                "DIRECT: THE VEHICLE DRIVES THE WAY YOU PRESS",
            )
        }
        help.forEachIndexed { i, line -> writeText((320 - line.length * 6) / 2, 158 + i * 8, LegacyColors.HIGH, line) }
    }
}

/** A key's name as the setup program would print it, at most 8 characters. */
private fun keyName(key: Key): String = java.awt.event.KeyEvent.getKeyText(key.nativeKeyCode).uppercase().take(8)

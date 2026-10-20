package nl.tankarena.ui.compose.extended

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.tankarena.sim.Ruleset
import nl.tankarena.ui.compose.menu.ControlAction
import nl.tankarena.ui.compose.menu.ControlDevice
import nl.tankarena.ui.compose.menu.ControlMode
import nl.tankarena.ui.compose.menu.JoystickAction
import nl.tankarena.ui.compose.menu.PlayerControls
import nl.tankarena.ui.compose.menu.padButtonsText
import nl.tankarena.ui.compose.menu.withDevice
import nl.tankarena.ui.compose.menu.withJoystickButtons
import nl.tankarena.ui.compose.menu.withKey
import nl.tankarena.ui.compose.menu.withMode
import nl.tankarena.ui.compose.menu.withSensitivity
import kotlinx.coroutines.launch

/**
 * A mission's briefing, or its success or failure text, in the Extended look. Enter, Space or ESC
 * goes on; the arrow keys and Page Up/Down scroll.
 */
@Composable
fun ExtendedTextPage(title: String, text: String, onClose: () -> Unit, footer: String = "Enter goes on") {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    ExtendedPage(
        title = title,
        onBack = onClose,
        footer = footer,
        onKey = { event ->
            when (event.key) {
                Key.Enter, Key.NumPadEnter, Key.Spacebar -> onClose()
                Key.DirectionDown -> scope.launch { scroll.animateScrollBy(60f) }
                Key.DirectionUp -> scope.launch { scroll.animateScrollBy(-60f) }
                Key.PageDown -> scope.launch { scroll.animateScrollBy(400f) }
                Key.PageUp -> scope.launch { scroll.animateScrollBy(-400f) }
                else -> return@ExtendedPage false
            }
            true
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(scroll)) {
            Text(text.trim(), color = ExtendedColors.text, fontSize = 18.sp, lineHeight = 28.sp)
        }
    }
}

private suspend fun androidx.compose.foundation.ScrollState.animateScrollBy(by: Float) = animateScrollTo((value + by.toInt()).coerceIn(0, maxValue))

/**
 * The first start (ADR 0023): Classic plays and looks like the original, Extended has this look and
 * more. The choice is kept and can be changed later.
 */
@Composable
fun RulesetChoicePage(onChoose: (Ruleset) -> Unit, onExit: () -> Unit) {
    val keys = remember { KeyRouter() }
    val choices = listOf(
        ListEntry("Classic", "the original game of 1997, as it was") to Ruleset.CLASSIC,
        ListEntry("Extended", "new menus, replays, a wider view and more to come") to Ruleset.EXTENDED,
    )
    ExtendedPage(
        title = "TANK ARENA",
        subtitle = "How would you like to play?",
        onBack = onExit,
        footer = "You can switch later: to Classic in Extended's settings, to Extended by starting with --extended",
        onKey = keys::handle,
    ) {
        ExtendedList(choices.map { it.first }, onPick = { onChoose(choices[it].second) }, keys = keys)
    }
}

/** The rows of Extended's controls page for one player. */
private sealed interface ControlRow {
    data object Steering : ControlRow
    data object Device : ControlRow
    data class KeyRow(val action: ControlAction) : ControlRow
    data class Buttons(val action: JoystickAction) : ControlRow
    data object Sensitivity : ControlRow
}

private fun rowsFor(controls: PlayerControls): List<ControlRow> = buildList {
    add(ControlRow.Steering)
    add(ControlRow.Device)
    ControlAction.entries.forEach { add(ControlRow.KeyRow(it)) }
    if (controls.device == ControlDevice.JOYSTICK) {
        JoystickAction.entries.forEach { add(ControlRow.Buttons(it)) }
        add(ControlRow.Sensitivity)
    }
}

/**
 * The controls of both players in the Extended look: a column per player with the steering, keyboard
 * or gamepad, the ten keys, and for a gamepad its four buttons and the stick's dead zone. Arrows move
 * (Left/Right change the dead zone on its row), Enter changes the steering or device or waits for a
 * key or buttons (taken from every other place), Delete clears; changes count at once.
 */
@Composable
fun ExtendedControlsPage(
    controls: List<PlayerControls>,
    onChange: (List<PlayerControls>) -> Unit,
    onBack: () -> Unit,
    readPadButtons: () -> List<Int> = { emptyList() },
) {
    var player by remember { mutableIntStateOf(0) }
    var index by remember { mutableIntStateOf(0) }
    var waiting by remember { mutableStateOf(false) }
    val rows = rowsFor(controls[player])
    val row = rows[index.coerceIn(0, rows.lastIndex)]
    if (waiting && row is ControlRow.Buttons) {
        LaunchedEffect(player, row) {
            var held = 0
            while (true) {
                val now = withFrameNanos { readPadButtons().fold(0) { mask, pad -> mask or pad } }
                held = held or now
                if (held != 0 && now == 0) {
                    onChange(controls.withJoystickButtons(player, row.action, held))
                    waiting = false
                    break
                }
            }
        }
    }
    ExtendedPage(
        title = "Controls",
        onBack = { if (waiting) waiting = false else onBack() },
        footer = when {
            waiting && row is ControlRow.KeyRow -> "Press the key for ${row.action.label.lowercase()}, or ESC"
            waiting && row is ControlRow.Buttons -> "Press the gamepad button(s) for ${row.action.label.lowercase()} and let go, or ESC"
            else -> "Arrows move, Enter changes, Delete clears. Direct steering: the vehicle drives the way you press. " +
                "A gamepad's buttons pressed together count as one."
        },
        onKey = { event ->
            if (waiting) {
                if (event.key == Key.Escape) return@ExtendedPage false
                if (row is ControlRow.KeyRow) {
                    onChange(controls.withKey(player, row.action, event.key))
                    waiting = false
                }
                return@ExtendedPage true
            }
            when (event.key) {
                Key.DirectionUp -> index = (index - 1 + rows.size) % rows.size
                Key.DirectionDown -> index = (index + 1) % rows.size
                Key.DirectionLeft, Key.DirectionRight -> if (row is ControlRow.Sensitivity) {
                    // Shown as 100 - the dead zone, as the original: right makes the stick quicker.
                    onChange(controls.withSensitivity(player, controls[player].sensitivity + if (event.key == Key.DirectionRight) -5 else 5))
                } else {
                    player = 1 - player
                    index = index.coerceAtMost(rowsFor(controls[player]).lastIndex)
                }
                Key.Enter, Key.NumPadEnter -> when (row) {
                    ControlRow.Steering -> {
                        val mode = controls[player].mode
                        onChange(controls.withMode(player, if (mode == ControlMode.RELATIVE) ControlMode.DIRECT else ControlMode.RELATIVE))
                    }
                    ControlRow.Device -> {
                        val device = controls[player].device
                        onChange(controls.withDevice(player, if (device == ControlDevice.KEYBOARD) ControlDevice.JOYSTICK else ControlDevice.KEYBOARD))
                    }
                    ControlRow.Sensitivity -> Unit
                    else -> waiting = true
                }
                Key.Delete -> when (row) {
                    is ControlRow.KeyRow -> onChange(controls.withKey(player, row.action, null))
                    is ControlRow.Buttons -> onChange(controls.withJoystickButtons(player, row.action, 0))
                    else -> Unit
                }
                else -> return@ExtendedPage false
            }
            true
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            for (p in 0..1) {
                Column(modifier = Modifier.width(420.dp)) {
                    Text(if (p == 0) "Player one" else "Player two (and a single player)", color = ExtendedColors.accent, fontSize = 20.sp)
                    for ((r, item) in rowsFor(controls[p]).withIndex()) {
                        val selected = p == player && r == index
                        val c = controls[p]
                        val (label, value) = when (item) {
                            ControlRow.Steering -> "Steering" to if (c.mode == ControlMode.RELATIVE) "turn and drive" else "direct"
                            ControlRow.Device -> "Steer with" to if (c.device == ControlDevice.KEYBOARD) "the keyboard" else "keyboard and a gamepad"
                            is ControlRow.KeyRow -> item.action.label.lowercase().replaceFirstChar { it.uppercase() } to
                                (if (selected && waiting) "press a key…" else c.key(item.action)?.let { java.awt.event.KeyEvent.getKeyText(it.nativeKeyCode) } ?: "—")
                            is ControlRow.Buttons -> "Pad: ${item.action.label.lowercase()}" to
                                (if (selected && waiting) "press…" else padButtonsText(c.buttons(item.action)))
                            ControlRow.Sensitivity -> "Pad: stick sensitivity" to "${100 - c.sensitivity}"
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (selected) Modifier.background(ExtendedColors.selected) else Modifier)
                                .padding(horizontal = 12.dp, vertical = 3.dp),
                        ) {
                            val color = if (selected) ExtendedColors.accent else ExtendedColors.text
                            Text(label, color = color, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            Text(value, color = if (selected) ExtendedColors.accent else ExtendedColors.dim, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

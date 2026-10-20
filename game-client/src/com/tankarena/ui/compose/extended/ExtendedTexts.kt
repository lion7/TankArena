package com.tankarena.ui.compose.extended

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tankarena.sim.Ruleset
import com.tankarena.ui.compose.menu.ControlAction
import com.tankarena.ui.compose.menu.ControlMode
import com.tankarena.ui.compose.menu.PlayerControls
import com.tankarena.ui.compose.menu.withKey
import com.tankarena.ui.compose.menu.withMode
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

/**
 * The controls of both players in the Extended look: a column per player with the steering and
 * the ten keys. Arrows move, Enter changes the steering or waits for a key (which is taken from every
 * other place), Delete clears a key; changes count at once.
 */
@Composable
fun ExtendedControlsPage(controls: List<PlayerControls>, onChange: (List<PlayerControls>) -> Unit, onBack: () -> Unit) {
    var player by remember { mutableIntStateOf(0) }
    var row by remember { mutableIntStateOf(0) }
    var waiting by remember { mutableStateOf(false) }
    val rows = ControlAction.entries.size + 1
    ExtendedPage(
        title = "Controls",
        onBack = { if (waiting) waiting = false else onBack() },
        footer = if (waiting) {
            "Press the key for ${ControlAction.entries[row - 1].label.lowercase()}, or ESC"
        } else {
            "Arrows move, Enter changes, Delete clears a key. Direct steering: the vehicle drives the way you press."
        },
        onKey = { event ->
            if (waiting) {
                if (event.key == Key.Escape) return@ExtendedPage false
                onChange(controls.withKey(player, ControlAction.entries[row - 1], event.key))
                waiting = false
                return@ExtendedPage true
            }
            when (event.key) {
                Key.DirectionUp -> row = (row - 1 + rows) % rows
                Key.DirectionDown -> row = (row + 1) % rows
                Key.DirectionLeft, Key.DirectionRight -> player = 1 - player
                Key.Enter, Key.NumPadEnter -> if (row == 0) {
                    val mode = controls[player].mode
                    onChange(controls.withMode(player, if (mode == ControlMode.RELATIVE) ControlMode.DIRECT else ControlMode.RELATIVE))
                } else {
                    waiting = true
                }
                Key.Delete -> if (row > 0) onChange(controls.withKey(player, ControlAction.entries[row - 1], null))
                else -> return@ExtendedPage false
            }
            true
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            for (p in 0..1) {
                Column(modifier = Modifier.width(420.dp)) {
                    Text(if (p == 0) "Player one" else "Player two (and a single player)", color = ExtendedColors.accent, fontSize = 20.sp)
                    for (r in 0 until rows) {
                        val selected = p == player && r == row
                        val label = if (r == 0) "Steering" else ControlAction.entries[r - 1].label.lowercase().replaceFirstChar { it.uppercase() }
                        val value = when {
                            selected && waiting -> "press a key…"
                            r == 0 -> if (controls[p].mode == ControlMode.RELATIVE) "turn and drive" else "direct"
                            else -> controls[p].key(ControlAction.entries[r - 1])?.let { java.awt.event.KeyEvent.getKeyText(it.nativeKeyCode) } ?: "—"
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (selected) Modifier.background(ExtendedColors.selected) else Modifier)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            val color = if (selected) ExtendedColors.accent else ExtendedColors.text
                            Text(label, color = color, fontSize = 17.sp, modifier = Modifier.weight(1f))
                            Text(value, color = if (selected) ExtendedColors.accent else ExtendedColors.dim, fontSize = 17.sp)
                        }
                    }
                }
            }
        }
    }
}

package nl.tankarena.ui.compose.extended

import nl.tankarena.ui.compose.menu.GameOptions
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import kotlinx.coroutines.launch
import nl.tankarena.input.keyName
import nl.tankarena.sim.Ruleset
import nl.tankarena.ui.compose.menu.ControlAction
import nl.tankarena.ui.compose.menu.ControlMode
import nl.tankarena.ui.compose.menu.PlayerControls
import nl.tankarena.ui.compose.menu.withKey
import nl.tankarena.ui.compose.menu.withMode
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Alignment
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect

/**
 * A mission's briefing, or its success or failure text, in the Extended look. Enter, Space or ESC
 * goes on; the arrow keys and Page Up/Down scroll.
 */
@Composable
fun ExtendedTextPage(title: String, text: String, onClose: () -> Unit, footer: String = "Enter or a tap goes on") {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    ExtendedPage(
        title = title,
        onBack = onClose,
        backLabel = "Go on",
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
        // A tap anywhere on the page goes on, as Enter does.
        Box(modifier = Modifier.fillMaxSize().clickable(onClick = onClose)) {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(scroll)) {
                RetroText(text.trim())
            }
        }
    }
}

private suspend fun androidx.compose.foundation.ScrollState.animateScrollBy(by: Float) = animateScrollTo((value + by.toInt()).coerceIn(0, maxValue))

/** The rows of Extended's controls page. */
private sealed interface ControlRow {
    data object Plays : ControlRow
    data object Steering : ControlRow
    data class KeyRow(val action: ControlAction) : ControlRow
    data object Gamepads : ControlRow
}

/** Player one (keyboard and mouse) and, in the second column, the second keyboard player. */
private val COLUMNS: List<List<ControlRow>> = listOf(
    listOf(ControlRow.Steering) + ControlAction.entries.map { ControlRow.KeyRow(it) } + ControlRow.Gamepads,
    listOf(ControlRow.Plays, ControlRow.Steering) + ControlAction.entries.map { ControlRow.KeyRow(it) },
)

/** What player one's mouse does (ADR 0029). */
private val MOUSE: List<Pair<String, String>> = listOf(
    "Turret" to "turns to the pointer",
    "Fire" to "left button",
    "Shield" to "right button",
    "Weapons" to "the wheel",
    "Rocket marker" to "the pointer, while fire is held",
)

/**
 * Extended's controls: player one plays with the keyboard and the mouse, the others with gamepads
 * (twin sticks, or the original's joystick); a second player on the keyboard can be switched on for
 * those without a gamepad. Arrows move, Enter changes or waits for a key (taken from the other
 * player), Delete clears; changes count at once.
 */
@Composable
fun ExtendedControlsPage(
    options: GameOptions,
    onChange: (GameOptions) -> Unit,
    onBack: () -> Unit,
) {
    var column by remember { mutableIntStateOf(0) }
    var index by remember { mutableIntStateOf(0) }
    var waiting by remember { mutableStateOf(false) }
    val rows = COLUMNS[column]
    val row = rows[index.coerceIn(0, rows.lastIndex)]
    // The two players' keys, kept apart like the original's setup does for its two players.
    val pair = listOf(options.mouseControls, options.controls[1])
    fun changed(players: List<PlayerControls>) = onChange(
        options.copy(mouseControls = players[0], controls = options.controls.mapIndexed { i, c -> if (i == 1) players[1] else c }),
    )
    // Enter, or a tap on the selected row: change it, or wait for a key.
    fun activate() {
        when (row) {
            ControlRow.Plays -> onChange(options.copy(keyboardSecond = !options.keyboardSecond))
            ControlRow.Steering -> {
                val mode = pair[column].mode
                changed(pair.withMode(column, if (mode == ControlMode.RELATIVE) ControlMode.DIRECT else ControlMode.RELATIVE))
            }
            ControlRow.Gamepads -> onChange(options.copy(twinSticks = !options.twinSticks))
            is ControlRow.KeyRow -> waiting = true
        }
    }
    ExtendedPage(
        title = "Controls",
        subtitle = "Gamepads join as the other players",
        onBack = { if (waiting) waiting = false else onBack() },
        note = if (waiting && row is ControlRow.KeyRow) {
            "Press the key for ${row.action.label.lowercase()}, or ESC"
        } else {
            "Direct steering: the vehicle drives the way you press"
        },
        footer = "Arrows move, Enter changes, Delete clears",
        actions = if (row is ControlRow.KeyRow && !waiting) listOf(PageAction("Clear") { changed(pair.withKey(column, row.action, null)) }) else emptyList(),
        onKey = { event ->
            if (waiting) {
                if (event.key == Key.Escape) return@ExtendedPage false
                if (row is ControlRow.KeyRow) {
                    changed(pair.withKey(column, row.action, event.key))
                    waiting = false
                }
                return@ExtendedPage true
            }
            when (event.key) {
                Key.DirectionUp -> index = (index - 1 + rows.size) % rows.size
                Key.DirectionDown -> index = (index + 1) % rows.size
                Key.DirectionLeft, Key.DirectionRight -> {
                    column = 1 - column
                    index = index.coerceAtMost(COLUMNS[column].lastIndex)
                }
                Key.Enter, Key.NumPadEnter -> activate()
                Key.Delete -> if (row is ControlRow.KeyRow) changed(pair.withKey(column, row.action, null))
                else -> return@ExtendedPage false
            }
            true
        },
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // Side by side, or one under the other on a screen higher than wide.
            val upright = maxWidth < maxHeight
            val scroll = rememberScrollState()
            // Keys and pads bring the selected row into view; a finger or the wheel scrolls freely.
            val selectedRow = remember { BringIntoViewRequester() }
            LaunchedEffect(column, index) { selectedRow.bringIntoView() }
            val columns: @Composable (Modifier) -> Unit = { each ->
                for (c in 0..1) {
                    Column(modifier = each) {
                        RetroText(if (c == 0) "Player one: keyboard and mouse" else "Second keyboard player", color = RetroColors.accent, maxLines = 1)
                        val controls = pair[c]
                        for ((r, item) in COLUMNS[c].withIndex()) {
                            val selected = c == column && r == index
                            val (label, value) = when (item) {
                                ControlRow.Plays -> "Plays" to if (options.keyboardSecond) "yes, joins with its fire key" else "no"
                                ControlRow.Steering -> "Steering" to if (controls.mode == ControlMode.RELATIVE) "turn and drive" else "direct"
                                is ControlRow.KeyRow -> item.action.label.lowercase().replaceFirstChar { it.uppercase() } to
                                    (if (selected && waiting) "press a key…" else controls.key(item.action)?.let { keyName(it) } ?: "-")
                                ControlRow.Gamepads -> "Gamepads" to if (options.twinSticks) "twin sticks" else "the original's joystick"
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 36.dp)
                                    .then(if (selected) Modifier.retroFrame(2).bringIntoViewRequester(selectedRow) else Modifier)
                                    .clickable(indication = null, interactionSource = null) {
                                        if (selected) {
                                            activate()
                                        } else {
                                            column = c
                                            index = r
                                            waiting = false
                                        }
                                    }
                                    .padding(horizontal = retroDp(6), vertical = retroDp(2)),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RetroText(label, color = if (selected) RetroColors.high else RetroColors.normal, maxLines = 1, modifier = Modifier.weight(1f))
                                RetroText(value, color = if (selected) RetroColors.high else RetroColors.dim, maxLines = 1)
                            }
                        }
                        // The mouse is player one's and has no keys to set: what it does, below the keys.
                        if (c == 0) {
                            RetroText("Mouse", color = RetroColors.accent, maxLines = 1, modifier = Modifier.padding(top = retroDp(8)))
                            for ((label, value) in MOUSE) {
                                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = retroDp(6), vertical = retroDp(2))) {
                                    RetroText(label, color = RetroColors.normal, maxLines = 1, modifier = Modifier.weight(1f))
                                    RetroText(value, color = RetroColors.dim, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            if (upright) {
                Column(modifier = Modifier.verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(retroDp(10))) { columns(Modifier.fillMaxWidth()) }
            } else {
                Row(modifier = Modifier.verticalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(retroDp(16))) { columns(Modifier.weight(1f)) }
            }
            if (scroll.canScrollBackward) RetroText("^", color = RetroColors.high, modifier = Modifier.align(Alignment.TopEnd))
            if (scroll.canScrollForward) RetroText("v", color = RetroColors.high, modifier = Modifier.align(Alignment.BottomEnd))
        }
    }
}

package nl.tankarena.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.tankarena.input.PadReading
import nl.tankarena.ui.compose.extended.ExtendedColors

/** Pad buttons in [nl.tankarena.ui.compose.menu.PAD_BUTTONS] order. */
private const val PAD_A = 1
private const val PAD_B = 1 shl 1

/**
 * Extended: ESC (or the touch screen's exit) in a game first asks whether to leave. A local game
 * stands still meanwhile; a network game runs on. Enter or A leaves, ESC or B plays on (the keys
 * come through the game's key handling); every gamepad can answer.
 */
@Composable
internal fun LeaveQuestion(controls: DesktopControls, pads: () -> List<PadReading>, network: Boolean) {
    if (!controls.leaveAsked) return
    PadPresses(pads, active = { controls.leaveAsked }) { pressed ->
        when {
            pressed and PAD_A != 0 -> controls.answerLeave(leave = true)
            pressed and PAD_B != 0 -> controls.answerLeave(leave = false)
        }
    }
    GameOverlay("Leave the game?", if (network) "The game goes on meanwhile" else null, "Enter or A leaves · ESC or B plays on")
}

/** Extended: the game stands still (P, or the touch screen's pause) until a key or A or B. */
@Composable
internal fun PausedOverlay(controls: DesktopControls, pads: () -> List<PadReading>) {
    if (!controls.paused || controls.leaveAsked) return
    PadPresses(pads, active = { controls.paused }) { pressed ->
        if (pressed and (PAD_A or PAD_B) != 0) {
            controls.paused = false
            controls.resetAll()
        }
    }
    GameOverlay("Paused", null, "Any key or A goes on")
}

/** Calls [onPress] with the gamepad buttons newly pressed in each frame while [active]; held ones do not count. */
@Composable
private fun PadPresses(pads: () -> List<PadReading>, active: () -> Boolean, onPress: (Int) -> Unit) {
    LaunchedEffect(Unit) {
        var held = pads().fold(0) { mask, pad -> mask or pad.buttons }
        while (active()) {
            val buttons = withFrameNanos { pads().fold(0) { mask, pad -> mask or pad.buttons } }
            val pressed = buttons and held.inv()
            held = buttons
            if (pressed != 0) onPress(pressed)
        }
    }
}

/** The game dimmed, with a small panel in the middle. */
@Composable
private fun GameOverlay(title: String, note: String?, keys: String) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.background(ExtendedColors.panel, RoundedCornerShape(10.dp)).padding(horizontal = 32.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, color = ExtendedColors.accent, fontSize = 24.sp)
            if (note != null) Text(note, color = ExtendedColors.dim, fontSize = 14.sp)
            Text(keys, color = ExtendedColors.text, fontSize = 16.sp)
        }
    }
}

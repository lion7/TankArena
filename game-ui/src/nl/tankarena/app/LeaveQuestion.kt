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
 * come through [answerLeaveKey]); every gamepad can answer.
 */
@Composable
internal fun LeaveQuestion(controls: DesktopControls, pads: () -> List<PadReading>, network: Boolean) {
    if (!controls.leaveAsked) return
    LaunchedEffect(Unit) {
        // Only presses from now on count: the button that is held already does not.
        var held = pads().fold(0) { mask, pad -> mask or pad.buttons }
        while (controls.leaveAsked) {
            val buttons = withFrameNanos { pads().fold(0) { mask, pad -> mask or pad.buttons } }
            val pressed = buttons and held.inv()
            held = buttons
            when {
                pressed and PAD_A != 0 -> controls.answerLeave(leave = true)
                pressed and PAD_B != 0 -> controls.answerLeave(leave = false)
            }
        }
    }
    Box(modifier = Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.background(ExtendedColors.panel, RoundedCornerShape(10.dp)).padding(horizontal = 32.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Leave the game?", color = ExtendedColors.accent, fontSize = 24.sp)
            if (network) Text("The game goes on meanwhile", color = ExtendedColors.dim, fontSize = 14.sp)
            Text("Enter or A leaves · ESC or B plays on", color = ExtendedColors.text, fontSize = 16.sp)
        }
    }
}

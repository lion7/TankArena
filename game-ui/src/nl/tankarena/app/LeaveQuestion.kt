package nl.tankarena.app

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import nl.tankarena.input.PadReading
import nl.tankarena.ui.compose.extended.PageAction
import nl.tankarena.ui.compose.extended.RetroBox
import nl.tankarena.ui.compose.extended.RetroButton
import nl.tankarena.ui.compose.extended.RetroColors
import nl.tankarena.ui.compose.extended.RetroFont
import nl.tankarena.ui.compose.extended.RetroScaled
import nl.tankarena.ui.compose.extended.RetroText
import nl.tankarena.ui.compose.extended.retroDp

/** Pad buttons in [nl.tankarena.ui.compose.menu.PAD_BUTTONS] order. */
private const val PAD_A = 1
private const val PAD_B = 1 shl 1

/**
 * Extended: ESC (or the touch screen's exit) in a game first asks whether to leave. A local game
 * stands still meanwhile; a network game runs on. Enter or A leaves, ESC or B plays on (the keys
 * come through the game's key handling); every gamepad can answer, and the buttons can be tapped or
 * clicked (a touch screen has no Enter; its Back is ESC).
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
    GameOverlay(
        "Leave the game?",
        if (network) "The game goes on meanwhile" else null,
        "Enter or A leaves · ESC or B plays on",
        buttons = listOf(PageAction("Leave") { controls.answerLeave(leave = true) }, PageAction("Play on") { controls.answerLeave(leave = false) }),
    )
}

/** Extended: the game stands still (P, or the touch screen's pause) until a key, A or B, or a tap. */
@Composable
internal fun PausedOverlay(controls: DesktopControls, pads: () -> List<PadReading>) {
    if (!controls.paused || controls.leaveAsked) return
    val goOn = {
        controls.paused = false
        controls.resetAll()
    }
    PadPresses(pads, active = { controls.paused }) { pressed ->
        if (pressed and (PAD_A or PAD_B) != 0) goOn()
    }
    GameOverlay("Paused", null, "Any key or A goes on", buttons = listOf(PageAction("Go on", goOn)), onTap = goOn)
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

/**
 * The game dimmed, with a box of the original's prompts in the middle and [buttons] to tap or click.
 * It takes every touch, so the touch controls and the mouse aim below it get none; a tap beside the
 * buttons goes to [onTap]. The keys are not shown on a touch screen.
 */
@Composable
private fun GameOverlay(title: String, note: String?, keys: String, buttons: List<PageAction>, onTap: () -> Unit = {}) {
    val touch = LocalTouchScreen.current
    RetroScaled(
        modifier = Modifier.fillMaxSize().background(Color(0x99000000)).pointerInput(onTap) { detectTapGestures { onTap() } },
        contentAlignment = Alignment.Center,
    ) {
        RetroBox(padding = 10) {
            Column(verticalArrangement = Arrangement.spacedBy(retroDp(6)), horizontalAlignment = Alignment.CenterHorizontally) {
                RetroText(title.uppercase(), color = RetroColors.high, font = RetroFont.TITLE)
                if (note != null) RetroText(note, color = RetroColors.dim, font = RetroFont.SMALL)
                if (!touch) RetroText(keys, color = RetroColors.dim, font = RetroFont.SMALL)
                Row(horizontalArrangement = Arrangement.spacedBy(retroDp(12))) {
                    for (button in buttons) RetroButton(button.label, button.onClick)
                }
            }
        }
    }
}

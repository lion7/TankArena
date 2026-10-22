package nl.tankarena.ui.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * The screen takes the keyboard focus back whenever it is touched: on a touch screen a tap can
 * leave nothing focused, and then keys and gamepad buttons (Back included) reach no screen.
 */
fun Modifier.refocusOnTouch(focus: FocusRequester): Modifier = pointerInput(focus) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial)
            runCatching { focus.requestFocus() }
        }
    }
}

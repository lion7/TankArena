package nl.tankarena.ui.compose.legacy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import nl.tankarena.ui.compose.LegacyScreen
import nl.tankarena.ui.compose.LegacyScreenScope
import nl.tankarena.ui.compose.LegacyVideoMode
import nl.tankarena.ui.compose.refocusOnTouch

/**
 * A mode 13h screen, painted by [draw] on every recomposition and shown through [palette] at
 * [brightness]. Key presses go to [onKey] (legacy `wait_key()`).
 */
@Composable
fun Mode13View(
    modifier: Modifier = Modifier,
    palette: IntArray = LegacyFonts.gamePalette,
    brightness: Float = 1f,
    onKey: (KeyEvent) -> Boolean = { false },
    /** Live content over the picture, in legacy screen coordinates (the tank selection's views). */
    overlay: @Composable LegacyScreenScope.() -> Unit = {},
    /** A tap on a touch screen, at a point of the 320×200 screen. */
    onTap: ((x: Int, y: Int) -> Unit)? = null,
    draw: Mode13Screen.() -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val image = Mode13Screen().apply(draw).toImageBitmap(palette)
    LegacyScreen(
        mode = LegacyVideoMode.MODE13,
        brightness = brightness,
        modifier = modifier
            .focusRequester(focusRequester)
            .refocusOnTouch(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event -> event.type == KeyEventType.KeyDown && onKey(event) },
    ) {
        val scope = this
        Canvas(
            modifier = Modifier.fillMaxSize().then(
                if (onTap == null) {
                    Modifier
                } else {
                    Modifier.pointerInput(onTap) {
                        detectTapGestures { at -> onTap((at.x / scope.pixelWidth).toInt(), (at.y / scope.pixelHeight).toInt()) }
                    }
                },
            ),
        ) {
            drawImage(
                image = image,
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                filterQuality = FilterQuality.None,
            )
        }
        overlay()
    }
}

/** Legacy palette indices used by the menus (original/src/include/define.h and the routines' callers). */
object LegacyColors {
    const val BLACK = 0

    /** `m13_add_bar(..., 5)`: the brighter panel band behind menus, prompts and lists. */
    const val BAR_ADD = 5

    /** `FNT_NORM`: menu entries. */
    const val NORMAL = 7

    /** `read_string`'s cursor block. */
    const val CURSOR = 0x0A

    /** `cmap[13]`: the numbers of the status display. */
    const val STATUS_NUMBER = 13

    /** `FNT_HIGH`: titles, the selected entry, prompt text. */
    const val HIGH = 15
}

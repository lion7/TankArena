package com.tankarena.ui.compose.legacy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.tankarena.ui.compose.LegacyScreen
import com.tankarena.ui.compose.LegacyScreenScope
import com.tankarena.ui.compose.LegacyVideoMode

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
            .focusable()
            .onPreviewKeyEvent { event -> event.type == KeyEventType.KeyDown && onKey(event) },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
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

/** Legacy palette indices used by the menus (src/include/define.h and the routines' callers). */
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

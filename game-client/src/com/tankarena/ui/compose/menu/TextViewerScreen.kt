package com.tankarena.ui.compose.menu

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.random.Random

/** Legacy `num_black`: the help pages pick one of `BLACK1.BMP`..`BLACK11.BMP`. */
private const val BACKDROP_COUNT = 11

/** The legacy screen is 320×200 and `view_file` draws 8-pixel lines from y = 10. */
private const val SCREEN_WIDTH = 320f
private const val SCREEN_HEIGHT = 200f
private const val LINE_PITCH = 8f
private const val TEXT_LEFT = 10f
private const val TEXT_TOP = 10f

/**
 * Legacy `view_file` (src/game/prompt.c): a full-screen page of upper-case text over a random
 * dark backdrop. Up/PgUp and Down/PgDn scroll, ESC, Enter and Space close it. Used for the main
 * menu's "Arena info" and "Default keys" and for mission briefings.
 */
@Composable
fun TextViewerScreen(
    text: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lines = remember(text) { TextPages.lines(text) }
    var top by remember(text) { mutableIntStateOf(0) }
    val backdrop = remember { loadUnpackedBackground("black${Random.nextInt(BACKDROP_COUNT) + 1}.bmp") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp, Key.PageUp -> {
                        top = TextPages.scroll(top, -TextPages.SCROLL_STEP, lines.size)
                        true
                    }
                    Key.DirectionDown, Key.PageDown -> {
                        top = TextPages.scroll(top, TextPages.SCROLL_STEP, lines.size)
                        true
                    }
                    Key.Escape, Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                        onClose()
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(modifier = Modifier.aspectRatio(SCREEN_WIDTH / SCREEN_HEIGHT)) {
            if (backdrop != null) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawImage(
                        image = backdrop,
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                    )
                }
            }
            val unit = maxHeight / SCREEN_HEIGHT
            val lineStyle = with(LocalDensity.current) {
                RetroTypography.Line.copy(
                    fontSize = (unit * LINE_PITCH * 0.9f).toSp(),
                    lineHeight = (unit * LINE_PITCH).toSp(),
                    letterSpacing = (unit * 0.4f).toSp(),
                )
            }
            if (top > 0) {
                Text(text = "▲", color = Color.White, style = lineStyle, modifier = Modifier.offset(unit * 2, unit * 2))
            }
            lines.drop(top).take(TextPages.VISIBLE_LINES).forEachIndexed { index, line ->
                Text(
                    text = line,
                    color = Color.White,
                    style = lineStyle,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.offset(unit * TEXT_LEFT, unit * (TEXT_TOP + index * LINE_PITCH)),
                )
            }
            if (top < TextPages.maxTop(lines.size)) {
                Text(
                    text = "▼",
                    color = Color.White,
                    style = lineStyle,
                    modifier = Modifier.offset(unit * 2, unit * (TEXT_TOP + TextPages.VISIBLE_LINES * LINE_PITCH)),
                )
            }
        }
    }
}

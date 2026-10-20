package nl.tankarena.ui.compose.menu

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import nl.tankarena.ui.compose.MODE13_FADE_MILLIS
import nl.tankarena.ui.compose.legacy.IndexedBitmap
import nl.tankarena.ui.compose.legacy.LegacyFonts
import nl.tankarena.ui.compose.legacy.Mode13View
import nl.tankarena.ui.compose.rememberFadeIn
import kotlin.random.Random

/** Legacy `num_black`: the text pages pick one of `BLACK1.BMP`..`BLACK11.BMP`. */
private const val BACKDROP_COUNT = 11

/** `view_file` draws 8-pixel lines from y = 10, in colour 255 of the backdrop's palette. */
private const val LINE_PITCH = 8
private const val TEXT_LEFT = 10
private const val TEXT_TOP = 10
private const val TEXT_COLOR = 255

/** The 5×5 font's arrows, characters 0x1E and 0x1F. */
private const val ARROW_UP = "\u001e"
private const val ARROW_DOWN = "\u001f"

/**
 * Legacy `view_file` (src/game/prompt.c): a full-screen page of upper-case text over a random
 * dark backdrop, shown through the backdrop's own palette. Up/PgUp and Down/PgDn scroll, ESC,
 * Enter and Space close it. Used for the main menu's "Arena info" and "Default keys", the
 * mission briefings and the texts after a mission. It fades in from black (`fade_in(pl, 3)`)
 * and fades out again when closed (`fade_out(3)`).
 */
@Composable
fun TextViewerScreen(
    text: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lines = remember(text) { TextPages.lines(text) }
    var top by remember(text) { mutableIntStateOf(0) }
    val backdrop = remember(text) { IndexedBitmap.loadBackground("black${Random.nextInt(BACKDROP_COUNT) + 1}.bmp") }
    val palette = remember(backdrop) {
        backdrop?.palette ?: LegacyFonts.gamePalette.copyOf().also { it[TEXT_COLOR] = 0xFFFFFF }
    }
    val brightness = rememberFadeIn(MODE13_FADE_MILLIS, text)
    var closing by remember(text) { mutableStateOf(false) }
    LaunchedEffect(closing) {
        if (!closing) return@LaunchedEffect
        brightness.animateTo(0f, tween(MODE13_FADE_MILLIS, easing = LinearEasing))
        onClose()
    }

    Mode13View(
        modifier = modifier,
        palette = palette,
        brightness = brightness.value,
        onKey = { event ->
            if (closing) return@Mode13View true
            when (event.key) {
                Key.DirectionUp, Key.PageUp -> top = TextPages.scroll(top, -TextPages.SCROLL_STEP, lines.size)
                Key.DirectionDown, Key.PageDown -> top = TextPages.scroll(top, TextPages.SCROLL_STEP, lines.size)
                Key.Escape, Key.Enter, Key.NumPadEnter, Key.Spacebar -> closing = true
                else -> return@Mode13View false
            }
            true
        },
    ) {
        backdrop?.let { blit(it) }
        if (top > 0) writeText(2, 2, TEXT_COLOR, ARROW_UP)
        lines.drop(top).take(TextPages.VISIBLE_LINES).forEachIndexed { index, line ->
            writeText(TEXT_LEFT, TEXT_TOP + index * LINE_PITCH, TEXT_COLOR, line)
        }
        if (top < TextPages.maxTop(lines.size)) {
            writeText(2, TEXT_TOP + TextPages.VISIBLE_LINES * LINE_PITCH, TEXT_COLOR, ARROW_DOWN)
        }
    }
}

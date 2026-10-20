package nl.tankarena.ui.compose.menu

import androidx.compose.animation.core.Animatable
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
import nl.tankarena.ui.compose.MODE13_FADE_MILLIS
import nl.tankarena.ui.compose.legacy.FliAnimation
import nl.tankarena.ui.compose.legacy.IndexedBitmap
import nl.tankarena.ui.compose.legacy.LegacyFonts
import nl.tankarena.ui.compose.legacy.Mode13View
import nl.tankarena.ui.compose.rememberFadeIn
import kotlinx.coroutines.delay

/** The steps of legacy write_title() (src/game/title.c) before the main menu. */
enum class IntroStep { LOGO, TITLE, CREDITS, THANKS }

/**
 * Legacy show_fli() followed by `fade_out(3)`: plays [fileName] from `FLI/` once at its own
 * speed; a key press stops it. Then the picture fades out and [onDone] says whether it was
 * stopped. A missing file shows nothing (the original release has no VICTORY.FLI or THUNDER.FLI).
 */
@Composable
fun FliScreen(
    fileName: String,
    onDone: (stopped: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val animation = remember(fileName) { FliAnimation.load(fileName) }
    if (animation == null) {
        LaunchedEffect(fileName) { onDone(false) }
        return
    }
    var frame by remember(fileName) { mutableIntStateOf(0) }
    var stopped by remember(fileName) { mutableStateOf(false) }
    val brightness = remember(fileName) { Animatable(1f) }
    LaunchedEffect(fileName) {
        while (!stopped && animation.next()) {
            frame++
            delay(animation.frameMillis)
        }
        brightness.animateTo(0f, tween(MODE13_FADE_MILLIS, easing = LinearEasing))
        onDone(stopped)
    }
    Mode13View(
        modifier = modifier,
        palette = remember(frame) { animation.palette.copyOf() },
        brightness = brightness.value,
        onKey = {
            stopped = true
            true
        },
        onTap = { _, _ -> stopped = true },
    ) {
        if (frame > 0) System.arraycopy(animation.pixels, 0, pixels, 0, minOf(pixels.size, animation.pixels.size))
    }
}

/**
 * Legacy display_credits(): [background] from BITMAPS.DAT with [lines] in colour 255 centred on
 * ([x], [y]), 7 pixels apart; it fades in, waits for a key and fades out.
 */
@Composable
fun CreditsScreen(
    background: String,
    x: Int,
    y: Int,
    lines: List<String>,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val picture = remember(background) { IndexedBitmap.loadBackground(background) }
    val brightness = rememberFadeIn(MODE13_FADE_MILLIS, background)
    var closing by remember(background) { mutableStateOf(false) }
    LaunchedEffect(closing) {
        if (!closing) return@LaunchedEffect
        brightness.animateTo(0f, tween(MODE13_FADE_MILLIS, easing = LinearEasing))
        onDone()
    }
    Mode13View(
        modifier = modifier,
        palette = picture?.palette ?: LegacyFonts.gamePalette,
        brightness = brightness.value,
        onKey = {
            closing = true
            true
        },
        onTap = { _, _ -> closing = true },
    ) {
        picture?.let { blit(it) }
        val top = y - lines.size * CREDITS_PITCH / 2
        lines.forEachIndexed { index, line ->
            writeText(x - line.length * 3, top + index * CREDITS_PITCH, CREDITS_COLOR, line)
        }
    }
}

/** The texts of legacy `credits[]` and `credits2[]`. */
object Credits {
    val CREDITS = listOf(
        "3.14 SOFWARE PRESENTS:", "TANK ARENA", "",
        "PROGRAMMING BY:", "JORRIT ROUWE", "",
        "GRAPHICS BY:", "DENNIS MEDEMA", "",
        "MUSIC BY:", "JANNE LEINONEN",
    )
    val THANKS = listOf(
        "THANKS TO:", "",
        "D.J. DELORIE (DJGPP)",
        "CHARLES SANDMANN (CWSDPMI)",
        "SHAWN HARGREAVES (ALLEGRO)",
    )
}

private const val CREDITS_PITCH = 7
private const val CREDITS_COLOR = 255

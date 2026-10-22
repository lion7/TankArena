package nl.tankarena.ui.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlin.math.floor
import kotlin.math.min

/**
 * The two video modes of the original: the 640×480 game screen (`res_x`×`res_y`, square pixels)
 * and the 320×200 mode 13h of the menus, prompts and text pages, which a VGA monitor shows at the
 * same 4:3 size, so its pixels are 1.2 times taller than wide.
 */
/** Extended's wide screen stops at 2:1. */
private const val MAX_WIDE_SCREEN_WIDTH = 1280

enum class LegacyVideoMode(val width: Int, val height: Int) {
    GAME(640, 480),
    MODE13(320, 200),
}

/** Converts legacy screen pixels of a [LegacyScreen] to layout units. */
class LegacyScreenScope internal constructor(
    boxScope: BoxScope,
    val mode: LegacyVideoMode,
    /** Legacy pixels across: the mode's width, or more on a wide screen (Extended). */
    val screenWidth: Int,
    /** Window pixels per legacy pixel, across and down. */
    val pixelWidth: Float,
    val pixelHeight: Float,
    private val density: Float,
) : BoxScope by boxScope {
    fun x(legacy: Number): Dp = Dp(legacy.toFloat() * pixelWidth / density)
    fun y(legacy: Number): Dp = Dp(legacy.toFloat() * pixelHeight / density)

    /** Places a child at a legacy position with a legacy size. */
    fun Modifier.legacyBounds(left: Number, top: Number, width: Number, height: Number): Modifier =
        offset(x(left), y(top)).size(x(width), y(height))
}

/**
 * The screen of the original in the middle of the window: 4:3, scaled by the largest whole
 * number that fits (as DOSBox does with a window), or shrunk to fit a smaller window. Black
 * around it. [brightness] dims the whole picture like the original's palette fades, where 0 is
 * black and 1 the full palette.
 */
@Composable
fun LegacyScreen(
    mode: LegacyVideoMode,
    modifier: Modifier = Modifier,
    brightness: Float = 1f,
    /** Extended: fill the window at any scale instead of whole multiples. */
    fill: Boolean = false,
    /** Extended: as wide as the window's shape allows (more than 640 legacy pixels across). */
    wide: Boolean = false,
    content: @Composable LegacyScreenScope.() -> Unit,
) {
    val density = LocalDensity.current.density
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        val availableWidth = constraints.maxWidth.toFloat()
        val availableHeight = constraints.maxHeight.toFloat()
        // On a wide window the screen grows across: 480 high, up to 1280 wide, on a multiple of 4.
        val virtualWidth = if (wide && availableHeight > 0f) {
            ((SCREEN_HEIGHT * availableWidth / availableHeight).toInt() and 3.inv()).coerceIn(SCREEN_WIDTH.toInt(), MAX_WIDE_SCREEN_WIDTH)
        } else {
            SCREEN_WIDTH.toInt()
        }
        val scale = if (fill || wide) {
            min(availableWidth / virtualWidth, availableHeight / SCREEN_HEIGHT)
        } else {
            legacyScreenScale(availableWidth, availableHeight)
        }
        val width = virtualWidth * scale
        val height = SCREEN_HEIGHT * scale
        val screenWidth = mode.width * virtualWidth / SCREEN_WIDTH.toInt()
        Box(
            modifier = Modifier
                .size(Dp(width / density), Dp(height / density))
                .fade(brightness),
        ) {
            val scope = LegacyScreenScope(
                boxScope = this,
                mode = mode,
                screenWidth = screenWidth,
                pixelWidth = width / screenWidth,
                pixelHeight = height / mode.height,
                density = density,
            )
            scope.content()
        }
    }
}

/** The 640×480 picture is scaled by a whole number when the window is large enough. */
internal fun legacyScreenScale(availableWidth: Float, availableHeight: Float): Float {
    val fit = min(availableWidth / SCREEN_WIDTH, availableHeight / SCREEN_HEIGHT)
    return if (fit >= 1f) floor(fit) else fit
}

private const val SCREEN_WIDTH = 640f
private const val SCREEN_HEIGHT = 480f

private fun Modifier.fade(brightness: Float): Modifier = if (brightness >= 1f) this else drawWithContent {
    drawContent()
    drawRect(Color.Black.copy(alpha = 1f - brightness.coerceIn(0f, 1f)))
}

/**
 * Allegro `fade_in(pal, 3)` / `fade_out(3)` of the mode 13h screens: 64 palette steps of 3 at
 * 70 Hz, about 0.3 s.
 */
const val MODE13_FADE_MILLIS: Int = 300

/**
 * Legacy `do_fade_step()` (original/src/graph/palette.c): the game screen fades over `fade_time` = 0.6 s
 * while the game runs on.
 */
const val GAME_FADE_MILLIS: Int = 600

/** A brightness that fades in from black once, over [millis]. */
@Composable
fun rememberFadeIn(millis: Int, key: Any? = Unit): Animatable<Float, *> {
    val level = remember(key) { Animatable(0f) }
    LaunchedEffect(level) { level.animateTo(1f, tween(millis, easing = LinearEasing)) }
    return level
}

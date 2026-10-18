package com.tankarena.ui.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import com.tankarena.ui.compose.menu.GameOptions
import org.jetbrains.skia.ColorFilter
import org.jetbrains.skia.ImageFilter

/**
 * Legacy do_gamma_correction(): the original corrects every palette (correct_pal()); the
 * rewrite runs the whole picture through the same curve. Gamma 0 leaves it alone.
 */
fun Modifier.gammaCorrection(gamma: Int): Modifier {
    val table = GameOptions.gammaTable(gamma) ?: return this
    val filter = ImageFilter.makeColorFilter(ColorFilter.makeTableARGB(null, table, table, table), null, null)
    return graphicsLayer { renderEffect = filter.asComposeRenderEffect() }
}

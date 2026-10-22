package nl.tankarena.ui.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import nl.tankarena.render.gammaEffect
import nl.tankarena.ui.compose.menu.GameOptions

/**
 * Legacy do_gamma_correction(): the original corrects every palette (correct_pal()); the
 * rewrite runs the whole picture through the same curve. Gamma 0 leaves it alone, as does a
 * platform without the effect.
 */
fun Modifier.gammaCorrection(gamma: Int): Modifier {
    val table = GameOptions.gammaTable(gamma) ?: return this
    val effect = gammaEffect(table) ?: return this
    return graphicsLayer { renderEffect = effect }
}

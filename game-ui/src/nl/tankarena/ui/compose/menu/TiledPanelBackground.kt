package nl.tankarena.ui.compose.menu

import nl.tankarena.render.decodeImage
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import nl.tankarena.ui.compose.LegacyScreenScope
import nl.tankarena.ui.compose.legacy.readBackground
import java.io.File
import kotlin.math.roundToInt

/**
 * The panel pattern across a whole [LegacyScreen]: legacy `write_panel()` (original/src/game/prompt.c)
 * blits the 40×40 `PANEL.BMP` across the screen from its top-left corner, in screen pixels, so
 * the pattern is as coarse as the rest of the picture. The game screen shows it around the map
 * windows; the mode 13h screens draw it themselves (Mode13Screen.panel()).
 */
@Composable
fun LegacyScreenScope.PanelPattern() {
    val tile = remember { loadPanelTile() } ?: return
    val tileWidth = PANEL_TILE * pixelWidth
    val tileHeight = PANEL_TILE * pixelHeight
    Canvas(modifier = Modifier.fillMaxSize()) {
        var y = 0f
        while (y < size.height) {
            var x = 0f
            while (x < size.width) {
                drawImage(
                    image = tile,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(tile.width, tile.height),
                    dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
                    dstSize = IntSize((x + tileWidth).roundToInt() - x.roundToInt(), (y + tileHeight).roundToInt() - y.roundToInt()),
                    filterQuality = FilterQuality.None,
                )
                x += tileWidth
            }
            y += tileHeight
        }
    }
}

/** Legacy `write_panel()` blits `PANEL.BMP` in 40×40 blocks. */
private const val PANEL_TILE = 40

private fun loadPanelTile(): ImageBitmap? =
    loadUnpackedBackground("panel.bmp", System.getProperty("tankarena.assets.background"))

/**
 * Loads a picture extracted from `original/DATA/BITMAPS.DAT` (bundled as `backgrounds/`), or [override]
 * when that file exists. Returns `null` when the picture is missing.
 */
internal fun loadUnpackedBackground(fileName: String, override: String? = null): ImageBitmap? {
    val bytes = override?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }?.readBytes()
        ?: readBackground(fileName)
        ?: return null
    return runCatching {
        decodeImage(bytes)
    }.getOrNull()
}

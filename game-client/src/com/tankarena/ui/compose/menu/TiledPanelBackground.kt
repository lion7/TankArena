package com.tankarena.ui.compose.menu

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import java.io.File
import org.jetbrains.skia.Image

@Composable
fun TiledPanelBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val tile = remember { loadPanelTile() }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(RetroColors.Background),
    ) {
        if (tile != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val tileWidth = tile.width
                val tileHeight = tile.height
                if (tileWidth <= 0 || tileHeight <= 0) return@Canvas
                val totalWidth = size.width.toInt()
                val totalHeight = size.height.toInt()
                var y = 0
                while (y < totalHeight) {
                    var x = 0
                    while (x < totalWidth) {
                        drawImage(
                            image = tile,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(tileWidth, tileHeight),
                            dstOffset = IntOffset(x, y),
                            dstSize = IntSize(tileWidth, tileHeight),
                        )
                        x += tileWidth
                    }
                    y += tileHeight
                }
                if (totalWidth <= 0 || totalHeight <= 0) {
                    drawRect(
                        color = RetroColors.Background,
                        topLeft = Offset.Zero,
                        size = Size(size.width, size.height),
                    )
                }
            }
        }
        content()
    }
}

private fun loadPanelTile(): ImageBitmap? =
    loadUnpackedBackground("panel.bmp", System.getProperty("tankarena.assets.background"))

/**
 * Loads a picture extracted from `DATA/BITMAPS.DAT` into `unpacked/backgrounds/` (the panel
 * tile, the `BLACK*.BMP` help-page backdrops). Returns `null` when the file is missing.
 */
internal fun loadUnpackedBackground(fileName: String, override: String? = null): ImageBitmap? {
    val candidates = buildList {
        if (!override.isNullOrBlank()) add(File(override))
        add(File("unpacked/backgrounds/$fileName"))
        add(File(System.getProperty("user.dir") ?: ".", "unpacked/backgrounds/$fileName"))
    }
    val file = candidates.firstOrNull { it.isFile } ?: return null
    return runCatching {
        Image.makeFromEncoded(file.readBytes()).toComposeImageBitmap()
    }.getOrNull()
}

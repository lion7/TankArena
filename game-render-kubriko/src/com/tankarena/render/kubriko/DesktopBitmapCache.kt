package com.tankarena.render.kubriko

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.io.File
import org.jetbrains.skia.Image

internal class DesktopBitmapCache(
    rootPath: String = System.getProperty("tankarena.assets.root", "unpacked/sprites"),
) {
    private val root = File(rootPath)
    private val cache = mutableMapOf<String, ImageBitmap?>()

    fun get(assetId: String): ImageBitmap? {
        return cache.getOrPut(assetId) {
            val file = File(root, "$assetId.png")
            if (!file.isFile) return@getOrPut null
            Image.makeFromEncoded(file.readBytes()).toComposeImageBitmap()
        }
    }
}

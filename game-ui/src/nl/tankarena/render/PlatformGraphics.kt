package nl.tankarena.render

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RenderEffect

/** Decodes a PNG or BMP file's bytes. */
expect fun decodeImage(bytes: ByteArray): ImageBitmap

/** An image of [width] × [height] from ARGB pixels (0xAARRGGBB), row by row. */
expect fun imageFromArgb(width: Int, height: Int, argb: IntArray): ImageBitmap

/**
 * A shader over a layer's own picture (`uniform shader content`), with images and float uniforms:
 * SkSL on the desktop, AGSL on Android 13 and later (the language as the game's shaders use it is
 * the same). Images are sampled pixel by pixel, clamped at the edges or, with `decal`, transparent
 * outside them.
 */
expect class LayerShader(source: String) {
    fun uniform(name: String, vararg values: Float)
    fun image(name: String, image: ImageBitmap, decal: Boolean = false)

    /** The effect for a layer, or null where the platform has no runtime shaders. */
    fun effect(): RenderEffect?
}

/** The same 256-entry curve on red, green and blue (legacy gamma correction), or null where unsupported. */
expect fun gammaEffect(table: ByteArray): RenderEffect?

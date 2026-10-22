package nl.tankarena.render

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorFilter
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.SamplingMode

actual fun decodeImage(bytes: ByteArray): ImageBitmap = Image.makeFromEncoded(bytes).toComposeImageBitmap()

actual fun imageFromArgb(width: Int, height: Int, argb: IntArray): ImageBitmap {
    val bytes = ByteArray(width * height * 4)
    for (i in argb.indices) {
        val c = argb[i]
        bytes[i * 4] = c.toByte()
        bytes[i * 4 + 1] = (c ushr 8).toByte()
        bytes[i * 4 + 2] = (c ushr 16).toByte()
        bytes[i * 4 + 3] = (c ushr 24).toByte()
    }
    val bitmap = Bitmap()
    bitmap.allocPixels(ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL))
    bitmap.installPixels(bytes)
    return bitmap.asComposeImageBitmap()
}

actual class LayerShader actual constructor(source: String) {
    private val builder = RuntimeShaderBuilder(effects.getOrPut(source) { RuntimeEffect.makeForShader(source) })

    actual fun uniform(name: String, vararg values: Float) {
        when (values.size) {
            1 -> builder.uniform(name, values[0])
            2 -> builder.uniform(name, values[0], values[1])
            3 -> builder.uniform(name, values[0], values[1], values[2])
            else -> builder.uniform(name, values[0], values[1], values[2], values[3])
        }
    }

    actual fun image(name: String, image: ImageBitmap, decal: Boolean) {
        val mode = if (decal) FilterTileMode.DECAL else FilterTileMode.CLAMP
        builder.child(name, Image.makeFromBitmap(image.asSkiaBitmap()).makeShader(mode, mode, SamplingMode.DEFAULT, null))
    }

    actual fun effect(): RenderEffect? = imageFilter().asComposeRenderEffect()

    /** The desktop's Skia filter itself (tests draw with it). */
    fun imageFilter(): ImageFilter = ImageFilter.makeRuntimeShader(builder, "content", null)

    private companion object {
        val effects = HashMap<String, RuntimeEffect>()
    }
}

actual fun gammaEffect(table: ByteArray): RenderEffect? =
    ImageFilter.makeColorFilter(ColorFilter.makeTableARGB(null, table, table, table), null, null).asComposeRenderEffect()

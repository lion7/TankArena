package nl.tankarena.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap

actual fun decodeImage(bytes: ByteArray): ImageBitmap =
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inScaled = false }).asImageBitmap()

actual fun imageFromArgb(width: Int, height: Int, argb: IntArray): ImageBitmap =
    Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()

/** AGSL runtime shaders came with Android 13 (API 33); before that the layer is drawn as it is. */
actual class LayerShader actual constructor(source: String) {
    private val shader: Any? = if (Build.VERSION.SDK_INT >= 33) RuntimeShader(source) else null

    actual fun uniform(name: String, vararg values: Float) {
        if (Build.VERSION.SDK_INT < 33) return
        (shader as RuntimeShader).setFloatUniform(name, values)
    }

    actual fun image(name: String, image: ImageBitmap, decal: Boolean) {
        if (Build.VERSION.SDK_INT < 33) return
        val mode = if (decal) Shader.TileMode.DECAL else Shader.TileMode.CLAMP
        val bitmapShader = BitmapShader(image.asAndroidBitmap(), mode, mode)
        bitmapShader.filterMode = BitmapShader.FILTER_MODE_NEAREST
        (shader as RuntimeShader).setInputShader(name, bitmapShader)
    }

    actual fun effect(): RenderEffect? {
        if (Build.VERSION.SDK_INT < 33) return null
        return android.graphics.RenderEffect.createRuntimeShaderEffect(shader as RuntimeShader, "content").asComposeRenderEffect()
    }
}

actual fun gammaEffect(table: ByteArray): RenderEffect? {
    if (Build.VERSION.SDK_INT < 33) return null
    val shader = LayerShader(GAMMA_AGSL)
    shader.image("curve", imageFromArgb(256, 1, IntArray(256) { (0xFF shl 24) or ((table[it].toInt() and 0xFF) shl 16) }))
    return shader.effect()
}

/** The gamma curve as a 256×1 image (in red), looked up per channel. */
private const val GAMMA_AGSL = """
    uniform shader content;
    uniform shader curve;

    half4 main(float2 p) {
        half4 c = content.eval(p);
        if (c.a <= 0.0) return c;
        float3 v = clamp(float3(c.rgb / c.a), 0.0, 1.0) * 255.0;
        half r = curve.eval(float2(v.r + 0.5, 0.5)).r;
        half g = curve.eval(float2(v.g + 0.5, 0.5)).r;
        half b = curve.eval(float2(v.b + 0.5, 0.5)).r;
        return half4(half3(r, g, b) * c.a, c.a);
    }
"""

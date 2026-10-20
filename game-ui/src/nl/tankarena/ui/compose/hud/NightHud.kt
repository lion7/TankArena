package nl.tankarena.ui.compose.hud

import nl.tankarena.render.LayerShader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.RenderEffect
import nl.tankarena.ui.resources.Res
import nl.tankarena.ui.resources.allDrawableResources
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.imageResource

/**
 * The status display of a night mission (legacy load_pictures() at night, src/game/init.c): every
 * picture goes through NIGHT.LNK into NIGHT.PAL, the pictures marked `night_vis` (meters, the
 * large weapon icon, faces, arrows, SELECT, ...) into its lit half and the rest into its dark
 * half. Text and radar dots use `cmap[c] = night_link[c] + 0x80`, the lit half too.
 *
 * Returns the effect that turns day palette colours into the [lit] or dark night colours, using
 * the same table as the night view (`night_lut.png`), or null while it is loading.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
internal fun rememberNightHudEffect(lit: Boolean): RenderEffect? {
    val resource = Res.allDrawableResources["night_lut"] ?: return null
    val lut = imageResource(resource)
    return remember(lut, lit) {
        LayerShader(NIGHT_HUD_SKSL).apply {
            uniform("lit", if (lit) 1f else 0f)
            image("lut", lut)
        }.effect()
    }
}

/** NightLighting's lookup with one switch for the whole layer instead of a light mask. */
internal const val NIGHT_HUD_SKSL = """
    uniform shader content;
    uniform shader lut;
    uniform float lit;

    half4 main(float2 p) {
        half4 c = content.eval(p);
        if (c.a < 0.5) return c;
        float3 v = floor(floor(clamp(float3(c.rgb / c.a), 0.0, 1.0) * 255.0 + 0.5) / 4.0);
        float sliceX = mod(v.b, 8.0);
        float sliceY = floor(v.b / 8.0);
        float2 cell = float2(sliceX * 64.0 + v.r + lit * 512.0, sliceY * 64.0 + v.g) + 0.5;
        return half4(lut.eval(cell).rgb * c.a, c.a);
    }
"""

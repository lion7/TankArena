package com.tankarena.render.kubriko

import com.tankarena.ui.compose.legacy.LegacyFonts
import kotlin.random.Random
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.SamplingMode

/**
 * Legacy palette_effects() (src/graph/palette.c): by day the game rotates five ranges of the
 * palette, so water ripples, conveyor belts roll, teleporters, glowing parts and lava move. The
 * pictures here are already in colour, so a shader recognises the pixels of those palette entries
 * (their colours are unique in the palette) and gives them the colour their entry has now.
 *
 * At night the original rotates the same entries of the night palette, which the night pictures
 * do not use, so nothing moves there; night maps skip this.
 */
internal class PaletteCycling(seed: Long = 0) {

    /** One rotating range: entries [first]..[last], rotated every [interval] ticks. */
    private class Range(val first: Int, val last: Int, val interval: (Random) -> Int) {
        var nextTick = 0L
    }

    private val random = Random(seed)
    private val ranges = listOf(
        Range(144, 149) { 100 + it.nextInt(100) }, // water
        Range(150, 153) { 10 }, // rolling
        Range(154, 158) { 20 }, // teleport
        Range(159, 164) { 50 }, // glow
        Range(165, 170) { 50 + it.nextInt(100) }, // lava
    )

    /** The current colour (0xRRGGBB) of every cycled entry, FIRST..LAST. */
    val colours: IntArray = IntArray(LAST - FIRST + 1) { LegacyFonts.gamePalette[FIRST + it] }

    /** Bumped whenever a range rotated. */
    var version: Int = 0
        private set

    private var lastTick = -1L
    private var colourImage: Image? = null
    private var colourImageVersion = -1
    private val effect: RuntimeEffect = RuntimeEffect.makeForShader(SKSL)

    /** Legacy `tdone(x)` / `x = tinit(n)`, one server tick at a time (100 per second). */
    fun advanceTo(tick: Long) {
        if (lastTick < 0) lastTick = tick - 1
        while (lastTick < tick) {
            lastTick++
            for (range in ranges) {
                if (lastTick >= range.nextTick) {
                    rotate(range.first, range.last)
                    range.nextTick = lastTick + range.interval(random)
                    version++
                }
            }
        }
    }

    /** Legacy rotate_palette(p, s, e): every entry takes the next one's colour, the last the first's. */
    private fun rotate(first: Int, last: Int) {
        val saved = colours[first - FIRST]
        for (i in first until last) colours[i - FIRST] = colours[i + 1 - FIRST]
        colours[last - FIRST] = saved
    }

    /** The render effect for the current colours. */
    fun filter(): ImageFilter {
        val image = colourImage?.takeIf { colourImageVersion == version } ?: buildColourImage().also {
            colourImage = it
            colourImageVersion = version
        }
        val builder = RuntimeShaderBuilder(effect)
        builder.child("slots", slotTable.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, SamplingMode.DEFAULT, null))
        builder.child("colours", image.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, SamplingMode.DEFAULT, null))
        return ImageFilter.makeRuntimeShader(builder, "content", null)
    }

    /** Slot s (1-based) holds the current colour of entry FIRST + s − 1. */
    private fun buildColourImage(): Image {
        val bytes = ByteArray(SLOTS * 4)
        for (slot in 1..colours.size) {
            val rgb = colours[slot - 1]
            bytes[slot * 4] = rgb.toByte()
            bytes[slot * 4 + 1] = (rgb ushr 8).toByte()
            bytes[slot * 4 + 2] = (rgb ushr 16).toByte()
            bytes[slot * 4 + 3] = 0xFF.toByte()
        }
        return Image.makeRaster(ImageInfo(SLOTS, 1, ColorType.BGRA_8888, ColorAlphaType.OPAQUE), bytes, SLOTS * 4)
    }

    companion object {
        const val FIRST = 144
        const val LAST = 170
        private const val SLOTS = 32
        private const val SLICE = 64

        /**
         * Every 6-bit colour → the slot of the cycled entry with that colour (0 for none), in
         * the red channel, laid out like `night_lut.png`: blue picks one of 64 slices of 64×64,
         * 8 per row. Where two cycled entries share a colour, the first wins, as the pictures
         * were extracted with it.
         */
        private val slotTable: Image by lazy {
            val size = 8 * SLICE
            val bytes = ByteArray(size * size * 4)
            for (index in LAST downTo FIRST) {
                val rgb = LegacyFonts.gamePalette[index]
                val r = (rgb ushr 16 and 0xFF) ushr 2
                val g = (rgb ushr 8 and 0xFF) ushr 2
                val b = (rgb and 0xFF) ushr 2
                val x = (b % 8) * SLICE + r
                val y = (b / 8) * SLICE + g
                val at = (x + y * size) * 4
                bytes[at + 2] = (index - FIRST + 1).toByte()
                bytes[at + 3] = 0xFF.toByte()
            }
            Image.makeRaster(ImageInfo(size, size, ColorType.BGRA_8888, ColorAlphaType.OPAQUE), bytes, size * 4)
        }

        internal const val SKSL = """
            uniform shader content;
            uniform shader slots;
            uniform shader colours;

            half4 main(float2 p) {
                half4 c = content.eval(p);
                if (c.a < 0.5) return c;
                float3 v = floor(floor(clamp(float3(c.rgb / c.a), 0.0, 1.0) * 255.0 + 0.5) / 4.0);
                float2 cell = float2(mod(v.b, 8.0) * 64.0 + v.r, floor(v.b / 8.0) * 64.0 + v.g) + 0.5;
                float slot = floor(slots.eval(cell).r * 255.0 + 0.5);
                if (slot < 0.5) return c;
                half3 now = colours.eval(float2(slot + 0.5, 0.5)).rgb;
                return half4(now * c.a, c.a);
            }
        """
    }
}

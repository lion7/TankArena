package nl.tankarena.render

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RenderEffect
import nl.tankarena.ui.compose.legacy.LegacyFonts
import kotlin.random.Random

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
    private var colourImageVersion = -1
    private val shader = LayerShader(SKSL).also { it.image("slots", slotTable) }

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

    /** The shader with the current colours. */
    internal fun shader(): LayerShader {
        if (colourImageVersion != version) {
            shader.image("colours", buildColourImage())
            colourImageVersion = version
        }
        return shader
    }

    /** The render effect for the current colours (null where the platform has no runtime shaders). */
    fun filter(): RenderEffect? = shader().effect()

    /** Slot s (1-based) holds the current colour of entry FIRST + s − 1. */
    private fun buildColourImage(): ImageBitmap =
        imageFromArgb(SLOTS, 1, IntArray(SLOTS) { slot -> OPAQUE or if (slot in 1..colours.size) colours[slot - 1] else 0 })

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
        private const val OPAQUE = 0xFF shl 24

        private val slotTable: ImageBitmap by lazy {
            val size = 8 * SLICE
            val argb = IntArray(size * size) { OPAQUE }
            for (index in LAST downTo FIRST) {
                val rgb = LegacyFonts.gamePalette[index]
                val r = (rgb ushr 16 and 0xFF) ushr 2
                val g = (rgb ushr 8 and 0xFF) ushr 2
                val b = (rgb and 0xFF) ushr 2
                val x = (b % 8) * SLICE + r
                val y = (b / 8) * SLICE + g
                argb[x + y * size] = OPAQUE or ((index - FIRST + 1) shl 16)
            }
            imageFromArgb(size, size, argb)
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

package nl.tankarena.render

import nl.tankarena.ui.compose.legacy.LegacyFonts
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

/** Legacy palette_effects() and rotate_palette() (src/graph/palette.c). */
class PaletteCyclingTest {

    private fun argb(index: Int) = LegacyFonts.gamePalette[index] or (0xFF shl 24)

    @Test
    fun `every range rotates on the first tick, the conveyor belt again every 10 ticks`() {
        val cycling = PaletteCycling()
        cycling.advanceTo(0)
        // rotate_palette(p, 150, 153): 150 takes 151's colour ... 153 takes 150's.
        assertEquals(LegacyFonts.gamePalette[151], cycling.colours[150 - PaletteCycling.FIRST])
        assertEquals(LegacyFonts.gamePalette[150], cycling.colours[153 - PaletteCycling.FIRST])
        cycling.advanceTo(9)
        assertEquals(LegacyFonts.gamePalette[151], cycling.colours[150 - PaletteCycling.FIRST], "not yet")
        cycling.advanceTo(10)
        assertEquals(LegacyFonts.gamePalette[152], cycling.colours[150 - PaletteCycling.FIRST])
        // The teleporter only every 20 ticks: still one step on.
        assertEquals(LegacyFonts.gamePalette[155], cycling.colours[154 - PaletteCycling.FIRST])
    }

    @Test
    fun `the shader gives cycled colours their entry's current colour and leaves the rest alone`() {
        val cycling = PaletteCycling()
        cycling.advanceTo(0)
        val content = Surface.makeRasterN32Premul(2, 1)
        content.canvas.drawRect(Rect.makeXYWH(0f, 0f, 1f, 1f), Paint().apply { color = argb(154) })
        content.canvas.drawRect(Rect.makeXYWH(1f, 0f, 1f, 1f), Paint().apply { color = argb(31) })

        val out = Surface.makeRasterN32Premul(2, 1)
        out.canvas.drawImage(content.makeImageSnapshot(), 0f, 0f, Paint().apply { imageFilter = cycling.shader().imageFilter() })
        val bitmap = Bitmap().apply { allocPixels(ImageInfo.makeN32Premul(2, 1)) }
        out.readPixels(bitmap, 0, 0)

        assertEquals(argb(155), bitmap.getColor(0, 0), "teleporter entry 154 now shows 155's colour")
        assertEquals(argb(31), bitmap.getColor(1, 0))
        assertEquals(Color.getA(bitmap.getColor(0, 0)), 255)
    }
}

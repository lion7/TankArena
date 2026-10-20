package nl.tankarena.ui.compose.legacy

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The intro animations of legacy write_title() (src/game/title.c). */
class FliAnimationTest {

    @Test
    fun `the intro animations decode frame by frame`() {
        for ((name, frames) in listOf("LOGO.FLI" to 100, "TITLE.FLI" to 76)) {
            val fli = assertNotNull(FliAnimation.load(name), "missing $name")
            assertEquals(320 to 200, fli.width to fli.height)
            assertEquals(frames, fli.frameCount)
            assertEquals(71, fli.frameMillis, "speed 5 in 1/70 s")
            var shown = 0
            var lit = 0
            while (fli.next()) {
                shown++
                if (fli.pixels.any { it.toInt() != 0 } && fli.palette.any { it != 0 }) lit++
                dump(name, shown, fli)
            }
            assertEquals(frames, shown)
            assertTrue(lit > frames / 2, "$name shows a picture in most frames ($lit of $frames)")
        }
    }

    @Test
    fun `the victory animations are missing like in the original release`() {
        assertNull(FliAnimation.load("VICTORY.FLI"))
        assertNull(FliAnimation.load("THUNDER.FLI"))
    }

    /** With `DUMP_DIR` set, every 20th frame is written there as a PNG to look at. */
    private fun dump(name: String, frame: Int, fli: FliAnimation) {
        val dir = System.getenv("DUMP_DIR") ?: return
        if (frame % 20 != 0) return
        val image = BufferedImage(fli.width, fli.height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until fli.height) for (x in 0 until fli.width) {
            image.setRGB(x, y, fli.palette[fli.pixels[x + y * fli.width].toInt() and 0xFF])
        }
        ImageIO.write(image, "png", File(dir, "${name.substringBefore('.')}-$frame.png"))
    }
}

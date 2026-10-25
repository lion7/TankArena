package nl.tankarena.ui.compose.extended

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RetroSkinTest {
    @Test
    fun `the big font's upper half is code page 437`() {
        assertEquals(128, CP437_HIGH.length)
        assertEquals(128 + 2, retroCodes("é", RetroFont.BIG)[0].code)
        assertEquals(251, retroCodes("✓", RetroFont.BIG)[0].code, "a tick is the square root sign")
    }

    @Test
    fun `characters the fonts lack are written with what they have`() {
        assertEquals("a - b...", retroCodes("a — b…", RetroFont.BIG))
        assertEquals("code?", retroCodes("code€", RetroFont.BIG), "the euro sign is not in code page 437")
        assertEquals("SMALL", retroCodes("small", RetroFont.SMALL), "the 5×5 font is upper case")
        assertEquals("ENTER - ESC", retroCodes("Enter · ESC", RetroFont.SMALL), "the 5×5 font writes a separating dot as a dash")
    }

    @Test
    fun `lines break at spaces and cut words longer than a line`() {
        assertEquals(listOf("one two", "three"), retroLines("one two three", 8))
        assertEquals(listOf("abcd", "efgh", "ij"), retroLines("abcdefghij", 4))
        assertEquals(listOf("first", "", "second"), retroLines("first\n\nsecond", 20))
        assertEquals(listOf("one two", "thre..."), retroLines("one two three four", 7, maxLines = 2))
    }

    @Test
    fun `a legacy pixel is a whole number of window pixels, larger on a dense screen`() {
        assertEquals(2, retroPixel(1280, 960, density = 1f), "desktop: about 400 rows")
        assertEquals(3, retroPixel(2560, 1440, density = 1f))
        assertEquals(3, retroPixel(2340, 1080, density = 2.75f), "a phone on its side: the big font 16 dp high")
        assertEquals(3, retroPixel(1080, 2340, density = 2.75f), "a phone held upright")
        assertEquals(1, retroPixel(400, 300, density = 1f))
        assertTrue(retroPixel(1080, 2340, density = 2.75f) * 320 <= 1080)
    }
}

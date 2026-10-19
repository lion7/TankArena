package com.tankarena.render

import com.tankarena.ui.compose.legacyScreenScale
import kotlin.test.Test
import kotlin.test.assertEquals

/** Legacy compute_window_size() and write_screen(): where the map windows sit on the screen. */
class MapWindowTest {

    @Test
    fun `a large map fills the screen in a 639 by 480 window`() {
        assertEquals(listOf(LegacyMapWindow(0, 0, 639, 480)), legacyMapWindows(1, 3200, 3200))
    }

    @Test
    fun `a small map gets a window of its own size, centred across and a third down`() {
        // BEGIN1 is 12×12 tiles of 32 px.
        assertEquals(listOf(LegacyMapWindow(128, 32, 384, 384)), legacyMapWindows(1, 384, 384))
    }

    @Test
    fun `two players get two 319 pixel windows with a one pixel gap`() {
        assertEquals(
            listOf(LegacyMapWindow(0, 0, 319, 480), LegacyMapWindow(320, 0, 319, 480)),
            legacyMapWindows(2, 3200, 3200),
        )
    }

    @Test
    fun `two windows on a small map are spread with equal gaps on multiples of four`() {
        // start_x = (640 - 2 * 200) / 3 = 80; the second at 80 + 80 + 200 + 1 = 361, rounded down to 360.
        assertEquals(
            listOf(LegacyMapWindow(80, 93, 200, 200), LegacyMapWindow(360, 93, 200, 200)),
            legacyMapWindows(2, 200, 200),
        )
    }

    @Test
    fun `the screen is scaled by whole numbers, or shrunk to fit a small window`() {
        assertEquals(2f, legacyScreenScale(1651f, 1260f))
        assertEquals(1f, legacyScreenScale(1000f, 700f))
        assertEquals(0.5f, legacyScreenScale(320f, 400f))
    }
}

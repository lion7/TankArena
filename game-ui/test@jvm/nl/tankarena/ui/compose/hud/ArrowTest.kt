package nl.tankarena.ui.compose.hud

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Legacy write_arrow() (src/world/status.c) on a 320×400 split-screen view. */
class ArrowTest {
    private val w = 320f
    private val h = 400f

    @Test
    fun `no arrow while the other player is in view`() {
        assertNull(arrowFor(100f, 100f, w, h))
    }

    @Test
    fun `the arrow sits at the edge towards the other player`() {
        assertEquals(Triple(2, w - 33, (h - 33) / 2), arrowFor(500f, 100f, w, h), "east")
        assertEquals(Triple(1, w - 33, 0f), arrowFor(500f, -50f, w, h), "north-east")
        assertEquals(Triple(3, w - 33, h - 33), arrowFor(500f, 600f, w, h), "south-east")
        assertEquals(Triple(0, (w - 33) / 2, 0f), arrowFor(100f, -50f, w, h), "north")
        assertEquals(Triple(4, (w - 33) / 2, h - 33), arrowFor(100f, 600f, w, h), "south")
        assertEquals(Triple(7, 0f, 0f), arrowFor(-100f, -50f, w, h), "north-west")
        assertEquals(Triple(6, 0f, (h - 33) / 2), arrowFor(-100f, 100f, w, h), "west")
        assertEquals(Triple(5, 0f, h - 33), arrowFor(-100f, 600f, w, h), "south-west")
    }
}

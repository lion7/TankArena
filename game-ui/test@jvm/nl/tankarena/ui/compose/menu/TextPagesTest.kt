package nl.tankarena.ui.compose.menu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextPagesTest {

    @Test
    fun `lines are upper case without trailing blank lines`() {
        assertEquals(listOf("HELLO", "", "WORLD"), TextPages.lines("Hello\r\n\nWorld\n\n"))
    }

    @Test
    fun `scrolling moves ten lines and keeps the last page full`() {
        val count = 66
        assertEquals(10, TextPages.scroll(0, TextPages.SCROLL_STEP, count))
        assertEquals(44, TextPages.scroll(40, TextPages.SCROLL_STEP, count))
        assertEquals(0, TextPages.scroll(5, -TextPages.SCROLL_STEP, count))
        assertEquals(0, TextPages.scroll(0, TextPages.SCROLL_STEP, 10))
    }

    @Test
    fun `bundled help files are the legacy texts`() {
        val keys = TextPages.lines(HelpFile.DEFAULT_KEYS.load())
        assertEquals("HERE IS A LISTING OF THE DEFAULT KEYBOARD", keys.first())
        assertTrue("PLAYER ONE:" in keys)
        assertTrue(TextPages.lines(HelpFile.ARENA_INFO.load()).size > 1000)
    }
}

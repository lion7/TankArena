package nl.tankarena.ui.compose.legacy

import nl.tankarena.ui.compose.menu.drawMenu
import kotlin.test.Test
import kotlin.test.assertEquals

/** The ports of src/graph/mode13.c, 5x5text.c and the menu layout of src/game/menu.c. */
class Mode13ScreenTest {

    @Test
    fun `add_bar brightens the palette indices it covers`() {
        val screen = Mode13Screen()
        screen.bar(0, 0, 4, 4, 18)
        screen.addBar(1, 1, 2, 2, LegacyColors.BAR_ADD)
        assertEquals(18, screen[0, 0])
        assertEquals(23, screen[1, 1])
        assertEquals(18, screen[3, 3])
    }

    @Test
    fun `write_text sets only the font pixels, overwrite_text also clears the rest`() {
        val written = Mode13Screen().apply { bar(0, 0, 12, 5, 9) }
        assertEquals(6, written.writeText(0, 0, 15, "!"))
        // '!' is 0B01000000 on its first row.
        assertEquals(9, written[0, 0])
        assertEquals(15, written[1, 0])
        val overwritten = Mode13Screen().apply { bar(0, 0, 12, 5, 9) }
        overwritten.overwriteText(0, 0, 15, "!")
        assertEquals(0, overwritten[0, 0])
        assertEquals(15, overwritten[1, 0])
    }

    @Test
    fun `the menu box is centred on its widest entry`() {
        val screen = Mode13Screen()
        val labels = listOf("Start new game", "Exit to DOS")
        screen.drawMenu("MAIN MENU", labels, selected = 0)
        // widest = 14 × 8 = 112; top = (180 − 2 × 16) / 2 = 74; the black box starts at (89, 69).
        assertEquals(LegacyColors.BLACK, screen[89, 69])
        val bandX = (280 - 112) / 2
        assertEquals(true, screen[bandX, 64] != LegacyColors.BLACK, "the band around the box")
    }
}

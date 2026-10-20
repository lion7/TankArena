package nl.tankarena.input

import java.awt.event.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class GamepadMenuKeysTest {
    @Test
    fun `the stick gives arrow keys with repeat, buttons give enter and escape once`() {
        val keys = GamepadMenuKeys()
        val down = listOf(PadReading(0, 90, 0))
        assertEquals(listOf(KeyEvent.VK_DOWN), keys.step(0, down))
        assertEquals(emptyList(), keys.step(100_000_000, down), "held: not again yet")
        assertEquals(listOf(KeyEvent.VK_DOWN), keys.step(GamepadMenuKeys.FIRST_REPEAT_NANOS, down))
        assertEquals(emptyList(), keys.step(GamepadMenuKeys.FIRST_REPEAT_NANOS + 1, listOf(PadReading(10, 20, 0))), "in the dead zone")
        assertEquals(listOf(KeyEvent.VK_ENTER), keys.step(1_000_000_000, listOf(PadReading(0, 0, 1))))
        assertEquals(emptyList(), keys.step(1_100_000_000, listOf(PadReading(0, 0, 1))), "A held: once")
        assertEquals(listOf(KeyEvent.VK_ESCAPE), keys.step(1_200_000_000, listOf(PadReading(0, 0, 2))))
        assertEquals(listOf(KeyEvent.VK_LEFT), keys.step(1_300_000_000, listOf(PadReading(-100, 30, 0), PadReading(0, 0, 0))))
    }
}

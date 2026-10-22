package nl.tankarena.input

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals

class GamepadMenuKeysTest {
    @Test
    fun `the stick gives arrow keys with repeat, buttons give enter and escape once`() {
        val keys = GamepadMenuKeys()
        val down = listOf(PadReading(0, 90, 0))
        assertEquals(listOf(Key.DirectionDown), keys.step(0, down))
        assertEquals(emptyList<Key>(), keys.step(100_000_000, down), "held: not again yet")
        assertEquals(listOf(Key.DirectionDown), keys.step(GamepadMenuKeys.FIRST_REPEAT_NANOS, down))
        assertEquals(emptyList<Key>(), keys.step(GamepadMenuKeys.FIRST_REPEAT_NANOS + 1, listOf(PadReading(10, 20, 0))), "in the dead zone")
        assertEquals(listOf(Key.Enter), keys.step(1_000_000_000, listOf(PadReading(0, 0, 1))))
        assertEquals(emptyList<Key>(), keys.step(1_100_000_000, listOf(PadReading(0, 0, 1))), "A held: once")
        assertEquals(listOf(Key.Escape), keys.step(1_200_000_000, listOf(PadReading(0, 0, 2))))
        assertEquals(listOf(Key.DirectionLeft), keys.step(1_300_000_000, listOf(PadReading(-100, 30, 0), PadReading(0, 0, 0))))
    }
}

package nl.tankarena.ui.compose.extended

import nl.tankarena.input.Seat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JoinStateTest {
    @Test
    fun `players join in order with a pad, each once`() {
        val join = JoinState(maxPlayers = 2)
        assertTrue(join.pad(1))
        assertFalse(join.pad(1), "a pad joins once")
        assertTrue(join.pad(0))
        assertEquals(listOf(Seat.Pad(1), Seat.Pad(0)), join.seats)
        assertFalse(join.pad(2), "full")
        join.leave()
        assertTrue(join.pad(2))
        assertEquals(Seat.Pad(2), join.seats.last())
    }
}

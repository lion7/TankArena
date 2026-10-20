package nl.tankarena.ui.compose.extended

import androidx.compose.ui.input.key.Key
import nl.tankarena.input.Seat
import nl.tankarena.ui.compose.menu.DEFAULT_CONTROLS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JoinStateTest {
    @Test
    fun `players join in order with a key set or a pad, each once`() {
        val join = JoinState(maxPlayers = 2)
        assertTrue(join.pad(1))
        assertFalse(join.pad(1), "a pad joins once")
        assertFalse(join.key(Key.Q, DEFAULT_CONTROLS), "not a fire key")
        assertTrue(join.key(Key.Z, DEFAULT_CONTROLS), "player one's fire key")
        assertEquals(listOf(Seat(keys = null, pad = 1), Seat(keys = 0, pad = null)), join.seats)
        assertFalse(join.key(Key.Delete, DEFAULT_CONTROLS), "full")
        join.leave()
        assertTrue(join.key(Key.Delete, DEFAULT_CONTROLS), "player two's fire key")
        assertEquals(Seat(keys = 1, pad = null), join.seats.last())
    }
}

package nl.tankarena.server

import nl.tankarena.net.DEFAULT_PORT
import nl.tankarena.sim.MatchMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ServerOptionsTest {
    @Test
    fun `the options are read with their defaults`() {
        assertEquals(
            ServerOptions("flagisle", null, 2, DEFAULT_PORT, "tankarena", repeat = false),
            ServerOptions.parse(arrayOf("--map", "flagisle")),
        )
        assertEquals(
            ServerOptions("air", MatchMode.DUAL_VS_COMPUTER, 3, 5000, "lan", repeat = true),
            ServerOptions.parse(arrayOf("--map", "air", "--mode", "together", "--players", "3", "--port", "5000", "--name", "lan", "--repeat")),
        )
    }

    @Test
    fun `wrong options are refused`() {
        assertNull(ServerOptions.parse(arrayOf()))
        assertNull(ServerOptions.parse(arrayOf("--map", "x", "--players", "5")))
        assertNull(ServerOptions.parse(arrayOf("--map", "x", "--mode", "solo")))
        assertNull(ServerOptions.parse(arrayOf("--map", "x", "--wat")))
    }

    @Test
    fun `maps are found by name and get the mode they are made for`() {
        val (name, flagIsle) = assertNotNull(loadMap("FLAGISLE"))
        assertEquals("flagisle", name)
        assertEquals(MatchMode.DUAL, defaultMode(flagIsle))
        assertNull(loadMap("no-such-map"))
    }
}

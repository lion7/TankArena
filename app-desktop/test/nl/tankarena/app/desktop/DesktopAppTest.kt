package nl.tankarena.app.desktop

import nl.tankarena.app.Music
import kotlin.test.Test
import kotlin.test.assertNotNull

class DesktopAppTest {
    @Test
    fun `the desktop app finds both tunes on its class path`() {
        for (tune in listOf(Music.TUNE, Music.NIGHT_TUNE)) {
            assertNotNull(javaClass.classLoader.getResource("music/$tune"), tune)
        }
    }
}

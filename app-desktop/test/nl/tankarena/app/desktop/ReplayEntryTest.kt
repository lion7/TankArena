package nl.tankarena.app.desktop

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class ReplayEntryTest {
    @Test
    fun `a saved recording is listed by mission, day and outcome`() {
        assertEquals("BEGIN1 19-10 14:05 won", ReplayEntry.of(File("begin1-20261019-140533-won.json")).label)
        assertEquals("3_VS_3 01-02 09:00 unfinished", ReplayEntry.of(File("3_vs_3-20270201-090000-unfinished.json")).label)
        assertEquals("my-replay", ReplayEntry.of(File("my-replay.json")).label)
    }
}

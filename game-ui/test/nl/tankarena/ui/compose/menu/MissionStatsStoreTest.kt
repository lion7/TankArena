package nl.tankarena.ui.compose.menu

import nl.tankarena.ui.compose.GameMode
import nl.tankarena.ui.compose.MissionOutcome
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MissionStatsStoreTest {
    private val file = Files.createTempDirectory("tankarena-stats").resolve("mission-stats.json").toFile()

    @Test
    fun `missions are counted per mode and kept across restarts`() {
        val store = MissionStatsStore(file)
        store.record(GameMode.SINGLE_PLAYER_VS_COMPUTER, "begin1", MissionOutcome.WON)
        store.record(GameMode.SINGLE_PLAYER_VS_COMPUTER, "BEGIN1", MissionOutcome.LOST)
        store.record(GameMode.SINGLE_PLAYER_VS_COMPUTER, "begin1", null)
        store.record(GameMode.DUAL_PLAYER_VS_COMPUTER, "begin1", MissionOutcome.WON)

        val again = MissionStatsStore(file)
        assertEquals(MissionRecord(played = 3, won = 1, lost = 1), again.of(GameMode.SINGLE_PLAYER_VS_COMPUTER, "begin1"))
        assertEquals(MissionRecord(played = 1, won = 1, lost = 0), again.of(GameMode.DUAL_PLAYER_VS_COMPUTER, "Begin1"))
        assertNull(again.of(GameMode.SINGLE_PLAYER_VS_COMPUTER, "begin2"))
        assertEquals("Played 3× · won 1 · lost 1", again.of(GameMode.SINGLE_PLAYER_VS_COMPUTER, "begin1")!!.text())
    }

    @Test
    fun `an unreadable file counts as nothing played`() {
        file.parentFile.mkdirs()
        file.writeText("not json")
        assertNull(MissionStatsStore(file).of(GameMode.SINGLE_PLAYER_VS_COMPUTER, "begin1"))
    }
}

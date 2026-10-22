package nl.tankarena.ui.compose.menu

import nl.tankarena.sim.map.ShippedMaps
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MissionCatalogTest {
    @Test
    fun `a saved map replaces the shipped map of the same name, and new ones are added`() = runBlocking {
        val begin1 = assertNotNull(ShippedMaps.load("begin1")).document
        val changed = begin1.copy(header = begin1.header.copy(code = "CHANGED"))
        val own = begin1.copy(header = begin1.header.copy(code = "MINE"))
        val missions = MissionCatalog.load { mapOf("begin1" to changed, "mymap" to own) }

        assertEquals(ShippedMaps.baseNames().size + 1, missions.size)
        assertEquals("CHANGED", missions.single { it.baseName == "begin1" }.code)
        assertEquals("MINE", missions.single { it.baseName == "mymap" }.code)
    }
}

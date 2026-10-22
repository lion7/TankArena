package nl.tankarena.ui.compose.menu

import nl.tankarena.sim.map.MapDocument
import nl.tankarena.sim.map.MapHeader

/** An empty map, for tests that need a [MissionEntry] but never start a match on it. */
internal fun testMap(code: String = "TEST"): MapDocument = MapDocument(
    header = MapHeader(name = code, size = listOf(10, 10), code = code),
    floor = emptyList(),
    solid = emptyList(),
    top = emptyList(),
)

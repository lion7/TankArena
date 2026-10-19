package com.tankarena.sim.map

import com.tankarena.sim.SceneFile
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapDocumentTest {

    @Test
    fun `every MAP file of the original release imports cleanly to its committed tamap`() {
        val originals = File("../MAPS").listFiles { file -> file.isFile && file.name.endsWith(".MAP", ignoreCase = true) }
            .orEmpty().sortedBy { it.name.lowercase() }
        assertEquals(69, originals.size, "the maps the original game shipped with")
        val committed = File("../game-content/resources/maps")
        val problems = mutableListOf<String>()
        for (file in originals) {
            val result = runCatching { LegacyMapImport.import(file.readBytes(), file.nameWithoutExtension) }
                .getOrElse { problems += "${file.name}: does not import: ${it.message}"; continue }
            if (result.notes.isNotEmpty()) problems += "${file.name}: ${result.notes}"
            val shipped = File(committed, "${LegacyMapImport.baseName(file.nameWithoutExtension)}.$MAP_FILE_EXTENSION")
            if (!shipped.isFile || shipped.readText() != MapFile.write(result.document)) {
                problems += "${file.name}: ${shipped.name} is not what the importer makes of it (run import-all)"
            }
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
        assertEquals(originals.map { LegacyMapImport.baseName(it.nameWithoutExtension) }.sorted(), ShippedMaps.baseNames().sorted())
    }

    @Test
    fun `every shipped map reads back to the same text and the same objects`() {
        for (map in ShippedMaps.loadAll()) {
            val text = MapFile.write(map.document)
            val loaded = MapConversion.fromDocument(MapFile.read(text))
            val again = MapConversion.toDocument(loaded)
            assertEquals(text, MapFile.write(again), "${map.baseName}: map → game → map changes the file")
            assertEquals(SceneFile.write(loaded.objects), SceneFile.write(MapConversion.objectsOf(again)), "${map.baseName}: objects changed")
        }
    }

    @Test
    fun `rows read like a sketch of the map`() {
        val document = ShippedMaps.load("begin1")!!.document
        assertEquals(12, document.floor.size)
        assertTrue(document.floor.all { it.length == 24 }, "two characters per cell")
        assertTrue(document.legend.isNotEmpty())
        assertTrue(document.objects.all { "type" in it && "at" in it }, "every object has a type and a position")
    }

    @Test
    fun `a legend code that does not match its picture name is refused`() {
        val document = ShippedMaps.load("begin1")!!.document
        val (code, entry) = document.legend.entries.first()
        val broken = document.copy(legend = document.legend + (code to entry.copy(name = "NOT-${entry.name}")))
        val failure = runCatching { MapConversion.fromDocument(broken) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException && "legend" in failure.message.orEmpty(), "got $failure")
    }

    @Test
    fun `a map from a newer format is refused`() {
        val text = MapFile.write(ShippedMaps.load("begin1")!!.document).replaceFirst("{", "{\n  \"format\": ${MAP_FORMAT_VERSION + 1},")
        val failure = runCatching { MapFile.read(text) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException, "got $failure")
    }
}

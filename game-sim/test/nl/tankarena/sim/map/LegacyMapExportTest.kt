package nl.tankarena.sim.map

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** ADR 0022, option C: `.MAP` export. */
class LegacyMapExportTest {
    private val originals = File("../MAPS").listFiles { file -> file.isFile && file.name.endsWith(".MAP", ignoreCase = true) }!!
        .sortedBy { it.name }

    @Test
    fun `every original map exports to its own file again, unlocked`() {
        val wrong = originals.filter { file ->
            val bytes = file.readBytes()
            val document = LegacyMapImport.import(bytes, file.nameWithoutExtension).document
            !LegacyMapExport.export(document).contentEquals(LegacyMapExport.unlocked(bytes))
        }
        assertEquals(emptyList(), wrong.map { it.name })
    }

    @Test
    fun `an exported map imports to the same map`() {
        for (file in originals) {
            val document = LegacyMapImport.import(file.readBytes(), file.nameWithoutExtension).document
            val again = LegacyMapImport.import(LegacyMapExport.export(document), file.nameWithoutExtension).document
            assertEquals(document, again, file.name)
        }
    }

    @Test
    fun `a map made in the editor exports and imports again`() {
        val editor = MapEditor(MapEditor.newDocument("fresh", nl.tankarena.content.TankArenaWorld.CITY, 24, 20, NewMapStart.BORDER))
        for ((i, template) in MapObjects.TEMPLATES.withIndex()) editor.addObject(template, 60 + (i % 12) * 40, 60 + (i / 12) * 60)
        editor.changeTexts(editor.texts.copy(briefing = "Go!", success = "Well done"))
        val document = editor.toDocument()
        val bytes = LegacyMapExport.export(document)
        val again = LegacyMapImport.import(bytes, "fresh")
        assertEquals(emptyList(), again.notes)
        // Extended's pads for players 3 and 4 have no place in the original.
        val forOriginal = editor.objects.count { !(it is nl.tankarena.sim.Tank.State && it.padPlayer >= 2) }
        assertEquals(forOriginal, again.document.objects.size)
        assertEquals(document.texts, again.document.texts)
        assertEquals(document.floor, again.document.floor)
        assertTrue(again.document.legacy?.objects.isNullOrEmpty(), "new objects export without leftovers: ${again.document.legacy?.objects?.keys}")
    }

    @Test
    fun `an edit drops the leftovers over the bytes it changes and keeps the others`() {
        val (name, editor, index) = ShippedMaps.baseNames().asSequence().mapNotNull { name ->
            val document = ShippedMaps.load(name)!!.document
            val editor = MapEditor(document)
            // A turret whose saved position float is not its position (only an imported file has that).
            val index = editor.objects.indices.firstOrNull { i ->
                editor.objects[i] is nl.tankarena.sim.Turret.State &&
                    document.legacy?.objects?.get(i.toString()).orEmpty().any { it.offset in 72..79 }
            }
            index?.let { Triple(name, editor, it) }
        }.first()
        val (x, y) = MapObjects.centre(editor.objects[index])
        editor.moveObject(index, x + 40, y)
        val runs = editor.toDocument().legacy?.objects?.get(index.toString()).orEmpty()
        assertTrue(runs.none { it.offset in 72..80 || it.offset in 52..60 }, "$name: position bytes dropped: $runs")
        assertTrue(runs.any { it.offset in 4..47 }, "$name: pointers kept: $runs")
        val exported = LegacyMapImport.import(LegacyMapExport.export(editor.toDocument()), name).document
        assertEquals(MapObjects.centre(editor.objects[index]), MapObjects.centre(MapEditor(exported).objects[index]))
        editor.undo()
        assertEquals(ShippedMaps.load(name)!!.document, editor.toDocument(), "undo brings the leftovers back")
    }

    @Test
    fun `a new code drops what was left in the code field`() {
        val document = ShippedMaps.load("begin3")!!.document
        assertTrue(document.legacy?.head.orEmpty().any { it.offset < 22 }, "BEGIN3 has text left after its code")
        val editor = MapEditor(document)
        editor.changeSettings(editor.settings.copy(code = "SOMETHINGLONGER"))
        val bytes = LegacyMapExport.export(editor.toDocument())
        assertEquals("SOMETHINGLONGER", String(bytes, 0, 15, Charsets.ISO_8859_1))
        assertEquals(0, bytes[15].toInt())
    }
}

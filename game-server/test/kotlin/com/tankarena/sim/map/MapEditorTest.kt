package com.tankarena.sim.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MapEditorTest {
    private fun begin1() = assertNotNull(ShippedMaps.load("begin1")).document

    @Test
    fun `an unedited map gives back the same document`() {
        val document = begin1()
        val editor = MapEditor(document)
        assertFalse(editor.modified)
        assertEquals(MapFile.write(document), MapFile.write(editor.toDocument()))
    }

    @Test
    fun `a stroke is painted, undone and redone as one step`() {
        val editor = MapEditor(begin1())
        val before = editor.picture(MapLayer.FLOOR, 1, 1)
        val picture = (0 until editor.pictureCount).first { it != before }
        editor.beginStroke()
        assertTrue(editor.paint(MapLayer.FLOOR, 1, 1, picture))
        assertTrue(editor.paint(MapLayer.FLOOR, 2, 1, picture))
        assertFalse(editor.paint(MapLayer.FLOOR, 2, 1, picture), "the cell already holds the picture")
        assertFalse(editor.paint(MapLayer.FLOOR, -1, 1, picture), "outside the map")
        editor.endStroke()
        assertTrue(editor.modified)

        assertTrue(editor.undo())
        assertEquals(before, editor.picture(MapLayer.FLOOR, 1, 1))
        assertFalse(editor.canUndo)
        assertTrue(editor.redo())
        assertEquals(picture, editor.picture(MapLayer.FLOOR, 1, 1))
        assertEquals(picture, editor.picture(MapLayer.FLOOR, 2, 1))
    }

    @Test
    fun `a painted map saves and loads with the new picture`() {
        val editor = MapEditor(begin1())
        val picture = editor.pictureCount - 1
        editor.paint(MapLayer.SOLID, 3, 4, picture)
        editor.paint(MapLayer.TOP, 3, 4, -1)
        val reread = MapEditor(MapFile.read(MapFile.write(editor.toDocument())))
        assertEquals(picture, reread.picture(MapLayer.SOLID, 3, 4))
        assertEquals(-1, reread.picture(MapLayer.TOP, 3, 4))
        assertEquals(begin1().objects, reread.toDocument().objects)
    }

    @Test
    fun `a picture outside the world's table is refused`() {
        val editor = MapEditor(begin1())
        assertFailsWith<IllegalArgumentException> { editor.paint(MapLayer.FLOOR, 0, 0, editor.pictureCount) }
    }
}

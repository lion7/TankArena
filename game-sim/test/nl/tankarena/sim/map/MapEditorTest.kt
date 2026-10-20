package nl.tankarena.sim.map

import nl.tankarena.content.PictureCatalog
import nl.tankarena.content.StructureLibrary
import nl.tankarena.content.TankArenaWorld
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

class MapEditorLayersTest {
    private fun newEditor(start: NewMapStart = NewMapStart.EMPTY, world: TankArenaWorld = TankArenaWorld.DESERT) =
        MapEditor(MapEditor.newDocument("fresh", world, 10, 9, start))

    @Test
    fun `a new map is rounded to whole blocks of four and can start with a border`() {
        val editor = newEditor(NewMapStart.BORDER)
        assertEquals(12 to 12, editor.width to editor.height)
        val corner = editor.picture(MapLayer.SOLID, 0, 0)
        assertEquals("BARRIER_TL", PictureCatalog.legacyRow(editor.world, corner)?.type)
        assertEquals(corner, editor.picture(MapLayer.TOP, 0, 0))
        assertEquals("BARRIER_B", PictureCatalog.legacyRow(editor.world, editor.picture(MapLayer.SOLID, 5, 11))?.type)
        assertEquals(-1, editor.picture(MapLayer.SOLID, 5, 5))
        val big = MapEditor(MapEditor.newDocument("big", TankArenaWorld.DESERT, 400, 400))
        assertEquals(200 to 200, big.width to big.height)
    }

    @Test
    fun `a water start floods the floor, with pits in the city`() {
        val water = newEditor(NewMapStart.WATER)
        assertEquals("WATER", PictureCatalog.legacyRow(water.world, water.picture(MapLayer.FLOOR, 3, 3))?.type)
        val city = newEditor(NewMapStart.WATER, TankArenaWorld.CITY)
        assertEquals("PIT_C", PictureCatalog.legacyRow(city.world, city.picture(MapLayer.FLOOR, 3, 3))?.type)
    }

    @Test
    fun `a structure goes down in one step and remove clears the building before the floor`() {
        val editor = newEditor()
        val structure = StructureLibrary.forWorld(editor.world).first { it.width > 1 && it.cells.any { c -> c.pictures[1] >= 0 } }
        editor.placeStructure(structure, 2, 2)
        for (cell in structure.cells) for (layer in MapLayer.entries) {
            val picture = cell.pictures[layer.ordinal]
            if (picture >= 0) assertEquals(picture, editor.picture(layer, 2 + cell.dx, 2 + cell.dy))
        }
        val built = structure.cells.first { it.pictures[1] >= 0 }
        val (x, y) = 2 + built.dx to 2 + built.dy
        val floor = editor.picture(MapLayer.FLOOR, x, y)
        editor.removeAt(x, y)
        assertEquals(-1, editor.picture(MapLayer.SOLID, x, y))
        assertEquals(floor, editor.picture(MapLayer.FLOOR, x, y))
        editor.undo()
        editor.undo()
        assertEquals(-1, editor.picture(MapLayer.SOLID, x, y))
        assertFalse(editor.canUndo)
    }

    @Test
    fun `regions fill and clear in one step each`() {
        val editor = newEditor()
        val floor = StructureLibrary.forWorld(editor.world).first { it.cells.size == 1 && it.cells[0].pictures[0] >= 0 }
        editor.fillRegion(floor, 4, 5, 1, 2)
        assertEquals(floor.cells[0].pictures[0], editor.picture(MapLayer.FLOOR, 3, 4))
        editor.clearRegion(1, 2, 4, 5)
        assertEquals(-1, editor.picture(MapLayer.FLOOR, 3, 4))
        editor.undo()
        assertEquals(floor.cells[0].pictures[0], editor.picture(MapLayer.FLOOR, 1, 2))
    }

    @Test
    fun `goal, bonus and men layers edit like the original and survive saving`() {
        val editor = newEditor()
        repeat(11) { editor.changeGoal(1, 1, 10) }
        assertEquals(100, editor.goal(1, 1))
        repeat(25) { editor.changeGoal(1, 1, -10) }
        assertEquals(-100, editor.goal(1, 1))
        editor.changeGoal(2, 1, 7)
        editor.setBonus(3, 3, 13)
        repeat(3) { editor.addMan(4, 4, MenWeapon.CHAIN_GUN) }
        editor.addMan(4, 4, MenWeapon.FLAME_THROWER)
        assertEquals(MenWeapon.FLAME_THROWER to 3, editor.men(4, 4), "another kind changes the men, adds none")
        editor.removeMan(4, 4)

        val reopened = MapEditor(MapFile.read(MapFile.write(editor.toDocument())))
        assertEquals(-100, reopened.goal(1, 1))
        assertEquals(7, reopened.goal(2, 1))
        assertEquals(13, reopened.bonus(3, 3))
        assertEquals(null, reopened.bonus(3, 4))
        assertEquals(MenWeapon.FLAME_THROWER to 2, reopened.men(4, 4))
        editor.undo()
        assertEquals(MenWeapon.FLAME_THROWER to 3, editor.men(4, 4))
    }
}

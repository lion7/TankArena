package nl.tankarena.sim

import nl.tankarena.content.LEGACY_TILE_SIZE
import nl.tankarena.content.PictureCatalog
import nl.tankarena.content.PictureMasks
import nl.tankarena.content.TankArenaWorld
import nl.tankarena.content.TileLayers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The legacy structure model (strength[2][3][], hit_wall()) on the desert pictures of original/src/data/pictures.c. */
class StructureGridTest {

    private fun grid(vararg solid: Pair<Int, Int>, top: Map<Int, Int> = emptyMap(), base: Map<Int, Int> = emptyMap()): StructureGrid {
        val width = 8
        val cells = width * 3
        val solidLayer = MutableList(cells) { -1 }
        for ((index, picture) in solid) solidLayer[index] = picture
        return StructureGrid(
            width,
            3,
            TankArenaWorld.DESERT,
            TileLayers(
                base = List(cells) { base[it] ?: -1 },
                solid = solidLayer,
                top = List(cells) { top[it] ?: -1 },
                goalLayer = List(cells) { if (it == 10) 25 else 0 },
                bonusLayer = List(cells) { -1 },
                manTypeLayer = List(cells) { 0 },
                manAmountLayer = List(cells) { 0 },
            ),
        )
    }

    @Test
    fun `a structure stands until its normal strength is worn away`() {
        // Desert picture 8 (@ASEAA): strength 20 against normal hits.
        val grid = grid(10 to 8)
        assertTrue(grid.isStanding(2, 1))
        assertTrue(grid.hit(2, 1, StructureLayer.SOLID, HitKind.NORMAL, 10).isEmpty())
        val destroyed = grid.hit(2, 1, StructureLayer.SOLID, HitKind.NORMAL, 10)
        assertEquals(1, destroyed.size)
        assertEquals(25, destroyed.single().goalContribution, "the goal layer's share comes with it")
        assertFalse(grid.isStanding(2, 1))
        assertEquals(setOf(10), grid.changedTiles)
    }

    @Test
    fun `linked pieces of a building share their damage`() {
        // Pictures 0 and 1 are linked RIGHT: hitting the left piece wears down the right one too.
        val grid = grid(9 to 0, 10 to 1)
        grid.hit(1, 1, StructureLayer.SOLID, HitKind.NORMAL, 50)
        assertEquals(150, grid.strength(1, 1, StructureLayer.SOLID))
        assertEquals(150, grid.strength(2, 1, StructureLayer.SOLID))
    }

    @Test
    fun `a structure with no strength of a kind cannot be hurt that way`() {
        // Picture 0 has crash strength 0: ramming does nothing to it.
        val grid = grid(10 to 0)
        grid.hit(2, 1, StructureLayer.SOLID, HitKind.CRASH, 500)
        assertEquals(200, grid.strength(2, 1, StructureLayer.SOLID))
        assertTrue(grid.isStanding(2, 1))
    }

    @Test
    fun `indestructible structures shrug off any hit`() {
        val picture = (0 until 600).first { PictureCatalog.legacyRow(TankArenaWorld.DESERT, it)?.strength?.first() == StructureGrid.INDESTRUCTIBLE }
        val grid = grid(10 to picture)
        assertTrue(grid.hit(2, 1, StructureLayer.SOLID, HitKind.NORMAL, 5000).isEmpty())
        assertTrue(grid.isStanding(2, 1))
    }

    @Test
    fun `a destroyed bridge leaves water`() {
        // Picture 282 (BRUG1.H): a bridge with strength 30.
        val grid = grid(10 to 282)
        val destroyed = grid.hit(2, 1, StructureLayer.SOLID, HitKind.NORMAL, 30)
        assertEquals("BRIDGE_H", destroyed.single().legacyType)
        assertEquals("WATER", PictureCatalog.legacyRow(TankArenaWorld.DESERT, grid.base[10])?.type)
        assertEquals(-1, grid.solid[10])
    }

    @Test
    fun `a destroyed top layer without a ruined picture is gone`() {
        // Desert ABOX has no ruined picture (pictures.c `{{"ABOX"}, ...}`): once destroyed, nothing of it is hit.
        val abox = (0 until 600).first { PictureCatalog.pictureNames(TankArenaWorld.DESERT, it) == listOf("ABOX") }
        val grid = grid(10 to 8, top = mapOf(10 to abox))
        val shape = assertNotNull(PictureMasks.mask("ABOX"))
        val left = 2 * LEGACY_TILE_SIZE
        val top = LEGACY_TILE_SIZE
        assertTrue(grid.overlapTop(shape, left, top) > 0, "the standing box is in the way")
        grid.hit(2, 1, StructureLayer.TOP, HitKind.NORMAL, 1000)
        assertFalse(grid.isTopIntact(2, 1))
        assertTrue(grid.isStanding(2, 1), "the building under it still stands")
        assertEquals(0, grid.overlapTop(shape, left, top))
    }

    @Test
    fun `a destroyed building takes its top layer with it`() {
        val grid = grid(10 to 8, top = mapOf(10 to 9))
        assertTrue(grid.isTopIntact(2, 1))
        assertEquals(StructureLayer.TOP, grid.blastLayerAt(2, 1), "blasts wear down the top layer first")
        grid.hit(2, 1, StructureLayer.SOLID, HitKind.NORMAL, 20)
        assertFalse(grid.isTopIntact(2, 1))
    }
}

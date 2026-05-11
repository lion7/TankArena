package com.tankarena.sim.kubriko.server

import com.tankarena.content.LEGACY_TILE_SIZE
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class LineOfSightTest {

    private fun tileCenter(x: Int, y: Int): Pair<Int, Int> =
        (x * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2) to (y * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2)

    @Test
    fun `line of sight is clear on empty grid`() {
        val width = 10
        val height = 10
        val solid = List(width * height) { -1 }
        val los = LineOfSight(width, height, solid)

        val (x0, y0) = tileCenter(1, 1)
        val (x1, y1) = tileCenter(8, 8)
        assertTrue(los.hasLineOfSight(x0, y0, x1, y1), "should have LOS on empty grid")
    }

    @Test
    fun `line of sight is blocked by wall in the middle`() {
        val width = 10
        val height = 5
        val solid = MutableList(width * height) { -1 }
        // Wall at (5, 2)
        solid[5 + 2 * width] = 1
        val los = LineOfSight(width, height, solid)

        val (x0, y0) = tileCenter(1, 2)
        val (x1, y1) = tileCenter(8, 2)
        assertFalse(los.hasLineOfSight(x0, y0, x1, y1), "wall at (5,2) should block LOS")
    }

    @Test
    fun `line of sight is clear when wall is off the line`() {
        val width = 10
        val height = 5
        val solid = MutableList(width * height) { -1 }
        // Wall at (5, 1) — above the line of sight at y=2
        solid[5 + 1 * width] = 1
        val los = LineOfSight(width, height, solid)

        val (x0, y0) = tileCenter(1, 2)
        val (x1, y1) = tileCenter(8, 2)
        assertTrue(los.hasLineOfSight(x0, y0, x1, y1), "wall above the line should not block LOS")
    }

    @Test
    fun `line of sight is blocked by diagonal wall`() {
        val width = 10
        val height = 10
        val solid = MutableList(width * height) { -1 }
        // Wall at (4, 4) on diagonal from (1,1) to (7,7)
        solid[4 + 4 * width] = 1
        val los = LineOfSight(width, height, solid)

        val (x0, y0) = tileCenter(1, 1)
        val (x1, y1) = tileCenter(7, 7)
        assertFalse(los.hasLineOfSight(x0, y0, x1, y1), "diagonal wall should block LOS")
    }

    @Test
    fun `line of sight to self is always clear`() {
        val width = 5
        val height = 5
        val solid = List(width * height) { -1 }
        val los = LineOfSight(width, height, solid)

        val (x, y) = tileCenter(2, 2)
        assertTrue(los.hasLineOfSight(x, y, x, y), "LOS to self should be clear")
    }

    @Test
    fun `line of sight blocked when start is on wall`() {
        val width = 5
        val height = 5
        val solid = MutableList(width * height) { -1 }
        solid[1 + 1 * width] = 1
        val los = LineOfSight(width, height, solid)

        val (x0, y0) = tileCenter(1, 1)
        val (x1, y1) = tileCenter(3, 3)
        assertFalse(los.hasLineOfSight(x0, y0, x1, y1), "start on wall should block LOS")
    }

    @Test
    fun `line of sight blocked when goal is on wall`() {
        val width = 5
        val height = 5
        val solid = MutableList(width * height) { -1 }
        solid[3 + 3 * width] = 1
        val los = LineOfSight(width, height, solid)

        val (x0, y0) = tileCenter(1, 1)
        val (x1, y1) = tileCenter(3, 3)
        assertFalse(los.hasLineOfSight(x0, y0, x1, y1), "goal on wall should block LOS")
    }

    @Test
    fun `LOS blocked by full wall between two points`() {
        val widthTiles = 20
        val heightTiles = 5
        val cellCount = widthTiles * heightTiles
        val solid = MutableList(cellCount) { -1 }

        // Full wall at x=8
        for (y in 0 until heightTiles) solid[8 + y * widthTiles] = 1

        val los = LineOfSight(widthTiles, heightTiles, solid)
        val (px, py) = tileCenter(2, 1)
        val (ex, ey) = tileCenter(14, 1)
        assertFalse(los.hasLineOfSight(px, py, ex, ey), "full wall should block LOS")
    }
}

package com.tankarena.sim.kubriko.server

import com.tankarena.content.LEGACY_TILE_SIZE
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TilePathfinderTest {

    private fun tileCenter(x: Int, y: Int): Pair<Int, Int> =
        (x * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2) to (y * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2)

    @Test
    fun `pathfinder finds direct path on empty grid`() {
        val width = 10
        val height = 10
        val solid = List(width * height) { -1 }
        val pf = TilePathfinder(width, height, solid)

        val path = pf.findPath(tileCenter(1, 1).first, tileCenter(1, 1).second, tileCenter(5, 5).first, tileCenter(5, 5).second)

        assertTrue(path.isNotEmpty(), "should find a path on empty grid")
        assertEquals(tileCenter(1, 1), path.first())
        assertEquals(tileCenter(5, 5), path.last())
    }

    @Test
    fun `pathfinder returns empty when start is on wall`() {
        val width = 5
        val height = 5
        val solid = MutableList(width * height) { -1 }
        solid[1 + 1 * width] = 1 // wall at (1,1)
        val pf = TilePathfinder(width, height, solid)

        val path = pf.findPath(tileCenter(1, 1).first, tileCenter(1, 1).second, tileCenter(3, 3).first, tileCenter(3, 3).second)
        assertTrue(path.isEmpty(), "should not find path when start is on wall")
    }

    @Test
    fun `pathfinder returns empty when goal is on wall`() {
        val width = 5
        val height = 5
        val solid = MutableList(width * height) { -1 }
        solid[3 + 3 * width] = 1 // wall at (3,3)
        val pf = TilePathfinder(width, height, solid)

        val path = pf.findPath(tileCenter(1, 1).first, tileCenter(1, 1).second, tileCenter(3, 3).first, tileCenter(3, 3).second)
        assertTrue(path.isEmpty(), "should not find path when goal is on wall")
    }

    @Test
    fun `pathfinder routes around wall obstacle`() {
        val width = 10
        val height = 5
        val solid = MutableList(width * height) { -1 }
        // Wall row at y=2, x=3..6
        for (x in 3..6) solid[x + 2 * width] = 1
        val pf = TilePathfinder(width, height, solid)

        val path = pf.findPath(tileCenter(1, 2).first, tileCenter(1, 2).second, tileCenter(8, 2).first, tileCenter(8, 2).second)

        assertTrue(path.isNotEmpty(), "should find path around wall")
        // Verify path goes through y=1 or y=3 at x=3..6
        val midTiles = path.map { (px, py) ->
            (px / LEGACY_TILE_SIZE) to (py / LEGACY_TILE_SIZE)
        }
        val blockedTiles = (3..6).map { x -> x to 2 }
        assertFalse(
            midTiles.any { it in blockedTiles },
            "path should not pass through blocked tiles",
        )
    }

    @Test
    fun `pathfinder returns empty when no path exists`() {
        val width = 10
        val height = 5
        val solid = MutableList(width * height) { -1 }
        // Full wall at x=5
        for (y in 0 until height) solid[5 + y * width] = 1
        val pf = TilePathfinder(width, height, solid)

        val path = pf.findPath(tileCenter(1, 2).first, tileCenter(1, 2).second, tileCenter(8, 2).first, tileCenter(8, 2).second)
        assertTrue(path.isEmpty(), "should not find path when wall blocks all routes")
    }

    @Test
    fun `pathfinder returns single tile when start equals goal`() {
        val width = 5
        val height = 5
        val solid = List(width * height) { -1 }
        val pf = TilePathfinder(width, height, solid)

        val path = pf.findPath(tileCenter(2, 2).first, tileCenter(2, 2).second, tileCenter(2, 2).first, tileCenter(2, 2).second)

        assertEquals(1, path.size)
        assertEquals(tileCenter(2, 2), path.first())
    }

    @Test
    fun `directionToNextStep returns correct direction`() {
        val width = 10
        val height = 10
        val solid = List(width * height) { -1 }
        val pf = TilePathfinder(width, height, solid)

        val path = pf.findPath(tileCenter(1, 1).first, tileCenter(1, 1).second, tileCenter(5, 1).first, tileCenter(5, 1).second)
        assertTrue(path.isNotEmpty())

        val dir = pf.directionToNextStep(tileCenter(1, 1).first, tileCenter(1, 1).second, path)
        assertTrue(dir >= 0, "should return a valid direction")
    }

    @Test
    fun `directionToNextStep returns -1 for empty path`() {
        val width = 5
        val height = 5
        val solid = List(width * height) { -1 }
        val pf = TilePathfinder(width, height, solid)

        val dir = pf.directionToNextStep(tileCenter(1, 1).first, tileCenter(1, 1).second, emptyList())
        assertEquals(-1, dir)
    }

    @Test
    fun `pathfinder routes around partial wall in corridor`() {
        val widthTiles = 20
        val heightTiles = 5
        val cellCount = widthTiles * heightTiles
        val solid = MutableList(cellCount) { -1 }

        // Wall at x=8, y=1 (blocking direct path)
        solid[8 + 1 * widthTiles] = 1
        // Also block above and below to force a detour
        solid[8 + 0 * widthTiles] = 1 // top boundary already blocked
        solid[8 + 4 * widthTiles] = 1 // bottom boundary already blocked

        val pf = TilePathfinder(widthTiles, heightTiles, solid)
        val path = pf.findPath(
            2 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
            1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
            14 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
            1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
        )

        assertTrue(path.isNotEmpty(), "should find path around obstacle")
        // Verify path avoids the wall tile
        val tiles = path.map { (px, py) ->
            (px / LEGACY_TILE_SIZE) to (py / LEGACY_TILE_SIZE)
        }
        assertFalse(tiles.contains(8 to 1), "path should avoid wall at (8,1)")
    }
}

package com.tankarena.sim.kubriko.server

import com.tankarena.content.LEGACY_TILE_SIZE

/**
 * Line-of-sight checker using Bresenham raycast against the solid tile grid.
 *
 * Returns true if a straight line from (x0, y0) to (x1, y1) does not pass
 * through any impassable tile (solid >= 0).
 */
class LineOfSight(
    private val widthTiles: Int,
    private val heightTiles: Int,
    private val solidLayer: List<Int>,
) {

    /**
     * Check if there is an unobstructed line of sight from (x0, y0) to (x1, y1).
     */
    fun hasLineOfSight(x0: Int, y0: Int, x1: Int, y1: Int): Boolean {
        val dx = kotlin.math.abs(x1 - x0)
        val dy = kotlin.math.abs(y1 - y0)
        val sx = if (x1 > x0) 1 else -1
        val sy = if (y1 > y0) 1 else -1
        var err = dx - dy
        var x = x0
        var y = y0

        while (true) {
            val tx = pixelToTile(x)
            val ty = pixelToTile(y)
            if (!isPassable(tx, ty)) return false
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 > -dy) {
                err -= dy
                x += sx
            }
            if (e2 < dx) {
                err += dx
                y += sy
            }
        }
        return true
    }

    private fun isPassable(x: Int, y: Int): Boolean {
        if (x < 0 || x >= widthTiles || y < 0 || y >= heightTiles) return false
        val idx = x + y * widthTiles
        val solid = solidLayer.getOrElse(idx) { -1 }
        return solid < 0
    }

    private fun pixelToTile(pixel: Int): Int = (pixel / LEGACY_TILE_SIZE).coerceIn(0, widthTiles - 1)
}

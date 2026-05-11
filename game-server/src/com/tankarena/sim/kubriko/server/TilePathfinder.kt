package com.tankarena.sim.kubriko.server

import com.tankarena.content.LEGACY_TILE_SIZE

/**
 * A* pathfinder over the tile grid.
 *
 * A tile is impassable if the `solid` layer entry is >= 0 (wall).
 * The map perimeter is always impassable (boundary walls).
 *
 * Returns a list of pixel coordinates (tile centers) from start to goal inclusive.
 * An empty list means no path was found.
 */
class TilePathfinder(
    private val widthTiles: Int,
    private val heightTiles: Int,
    private val solidLayer: List<Int>,
) {

    /**
     * Find a path from [startX], [startY] to [goalX], [goalY] in pixel coordinates.
     * Returns a list of tile-center pixel coordinates (inclusive of both ends).
     * Empty list if no path exists.
     */
    fun findPath(startX: Int, startY: Int, goalX: Int, goalY: Int): List<Pair<Int, Int>> {
        val startTileX = pixelToTile(startX)
        val startTileY = pixelToTile(startY)
        val goalTileX = pixelToTile(goalX)
        val goalTileY = pixelToTile(goalY)

        if (!isPassable(startTileX, startTileY) || !isPassable(goalTileX, goalTileY)) {
            return emptyList()
        }

        val startKey = tileKey(startTileX, startTileY)
        val goalKey = tileKey(goalTileX, goalTileY)

        if (startKey == goalKey) {
            return listOf(tileCenter(startTileX, startTileY))
        }

        return astar(startKey, goalKey)
    }

    /**
     * Get the next direction (0-15) the AI tank should face to follow the path.
     * Returns -1 if no direction change is needed or path is unavailable.
     */
    fun directionToNextStep(
        currentX: Int,
        currentY: Int,
        path: List<Pair<Int, Int>>,
    ): Int {
        if (path.isEmpty()) return -1

        // Find the next waypoint that is more than one tile away
        val currentTileX = pixelToTile(currentX)
        val currentTileY = pixelToTile(currentY)

        for (i in path.indices) {
            val (px, py) = path[i]
            val tx = pixelToTile(px)
            val ty = pixelToTile(py)
            if (tx == currentTileX && ty == currentTileY) {
                // We're at this waypoint, look ahead
                if (i + 1 < path.size) {
                    val (nx, ny) = path[i + 1]
                    val ntx = pixelToTile(nx)
                    val nty = pixelToTile(ny)
                    val dx = ntx - tx
                    val dy = nty - ty
                    return com.tankarena.core.LegacyDirections.fromFacing(
                        dx.coerceIn(-1, 1),
                        dy.coerceIn(-1, 1),
                    )
                }
            } else {
                // Not at this waypoint yet, head toward it
                val dx = tx - currentTileX
                val dy = ty - currentTileY
                return com.tankarena.core.LegacyDirections.fromFacing(
                    dx.coerceIn(-1, 1),
                    dy.coerceIn(-1, 1),
                )
            }
        }

        return -1
    }

    private fun astar(startKey: Int, goalKey: Int): List<Pair<Int, Int>> {
        val openSet = mutableSetOf(startKey)
        val cameFrom = mutableMapOf<Int, Int>()
        val gScore = mutableMapOf<Int, Int>().withDefault { Int.MAX_VALUE }
        gScore[startKey] = 0
        val fScore = mutableMapOf<Int, Int>().withDefault { Int.MAX_VALUE }
        fScore[startKey] = heuristic(startKey, goalKey)

        // Use a simple priority queue simulation (find min fScore)
        while (openSet.isNotEmpty()) {
            val current = openSet.minByOrNull { fScore[it] ?: Int.MAX_VALUE } ?: break

            if (current == goalKey) {
                return reconstructPath(cameFrom, startKey, goalKey)
            }

            openSet.remove(current)
            val closedSet = mutableSetOf(current)

            val (cx, cy) = keyToTile(current)
            for ((nx, ny) in neighbors(cx, cy)) {
                if (closedSet.contains(tileKey(nx, ny))) continue
                if (!isPassable(nx, ny)) continue

                val neighborKey = tileKey(nx, ny)
                val tentativeG = (gScore[current] ?: Int.MAX_VALUE) + 1

                if (tentativeG < (gScore[neighborKey] ?: Int.MAX_VALUE)) {
                    cameFrom[neighborKey] = current
                    gScore[neighborKey] = tentativeG
                    fScore[neighborKey] = tentativeG + heuristic(neighborKey, goalKey)
                    openSet.add(neighborKey)
                }
            }
        }

        return emptyList()
    }

    private fun reconstructPath(
        cameFrom: Map<Int, Int>,
        startKey: Int,
        goalKey: Int,
    ): List<Pair<Int, Int>> {
        val path = mutableListOf<Pair<Int, Int>>()
        var current = goalKey
        while (current in cameFrom) {
            val (x, y) = keyToTile(current)
            path.add(tileCenter(x, y))
            current = cameFrom[current]!!
        }
        val (sx, sy) = keyToTile(startKey)
        path.add(tileCenter(sx, sy))
        path.reverse()
        return path
    }

    private fun heuristic(a: Int, b: Int): Int {
        val (ax, ay) = keyToTile(a)
        val (bx, by) = keyToTile(b)
        // Manhattan distance
        return kotlin.math.abs(ax - bx) + kotlin.math.abs(ay - by)
    }

    private fun neighbors(x: Int, y: Int): List<Pair<Int, Int>> {
        val result = mutableListOf<Pair<Int, Int>>()
        // 8-directional movement
        for (dx in -1..1) {
            for (dy in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx
                val ny = y + dy
                if (nx in 0 until widthTiles && ny in 0 until heightTiles) {
                    result.add(nx to ny)
                }
            }
        }
        return result
    }

    private fun isPassable(x: Int, y: Int): Boolean {
        if (x < 0 || x >= widthTiles || y < 0 || y >= heightTiles) return false
        val idx = x + y * widthTiles
        val solid = solidLayer.getOrElse(idx) { -1 }
        return solid < 0
    }

    private fun tileKey(x: Int, y: Int): Int = x + y * widthTiles
    private fun keyToTile(key: Int): Pair<Int, Int> = key % widthTiles to key / widthTiles
    private fun pixelToTile(pixel: Int): Int = (pixel / LEGACY_TILE_SIZE).coerceIn(0, widthTiles - 1)
    private fun tileCenter(tx: Int, ty: Int): Pair<Int, Int> =
        (tx * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2) to (ty * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2)
}

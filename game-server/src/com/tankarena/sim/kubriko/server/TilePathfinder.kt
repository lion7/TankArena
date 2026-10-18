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
    /** Passability and connected areas, read from [solidLayer] on first use after [invalidate]. */
    private var passable = BooleanArray(0)
    private var area = IntArray(0)
    private var stale = true

    /**
     * The layer may have changed (a building fell, a bridge went down); the next search reads it
     * again. The match calls this once per tick.
     */
    fun invalidate() {
        stale = true
    }

    /** Reads the layer and labels every passable tile with its 8-connected area. */
    private fun refresh() {
        if (!stale) return
        stale = false
        val size = widthTiles * heightTiles
        passable = BooleanArray(size) { (solidLayer.getOrNull(it) ?: -1) < 0 }
        area = IntArray(size) { -1 }
        val queue = IntArray(size)
        var label = 0
        for (seed in 0 until size) {
            if (!passable[seed] || area[seed] >= 0) continue
            var head = 0
            var tail = 0
            queue[tail++] = seed
            area[seed] = label
            while (head < tail) {
                val tile = queue[head++]
                val x = tile % widthTiles
                val y = tile / widthTiles
                for (dx in -1..1) for (dy in -1..1) {
                    val nx = x + dx
                    val ny = y + dy
                    if (!isPassable(nx, ny)) continue
                    val next = tileKey(nx, ny)
                    if (area[next] < 0) {
                        area[next] = label
                        queue[tail++] = next
                    }
                }
            }
            label++
        }
    }

    /**
     * Find a path from [startX], [startY] to [goalX], [goalY] in pixel coordinates.
     * Returns a list of tile-center pixel coordinates (inclusive of both ends).
     * Empty list if no path exists.
     */
    fun findPath(startX: Int, startY: Int, goalX: Int, goalY: Int): List<Pair<Int, Int>> {
        refresh()
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
        // Different areas: A* would search the whole area of the start and find nothing.
        if (area[startKey] != area[goalKey]) return emptyList()

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

    /**
     * A* with 8 neighbours and unit step costs. The open set is a heap ordered by f-score and,
     * among equal scores, by when the tile joined the open set, which picks the same tile as a
     * scan of an insertion-ordered set would; stale heap entries are skipped when popped.
     */
    private fun astar(startKey: Int, goalKey: Int): List<Pair<Int, Int>> {
        val size = widthTiles * heightTiles
        val g = IntArray(size) { Int.MAX_VALUE }
        val f = IntArray(size) { Int.MAX_VALUE }
        val cameFrom = IntArray(size) { -1 }
        val inOpen = BooleanArray(size)
        val joined = IntArray(size)
        var nextJoin = 0
        // Entries: f-score in the high 32 bits, join number in the low ones.
        val heap = java.util.PriorityQueue<Long>()
        val joinedNode = IntArrayList()

        fun open(node: Int) {
            if (!inOpen[node]) {
                inOpen[node] = true
                joined[node] = nextJoin++
                joinedNode.add(node)
            }
            heap.add((f[node].toLong() shl 32) or joined[node].toLong())
        }

        g[startKey] = 0
        f[startKey] = heuristic(startKey, goalKey)
        open(startKey)
        while (heap.isNotEmpty()) {
            val entry = heap.poll()
            val join = (entry and 0xFFFFFFFFL).toInt()
            val current = joinedNode[join]
            if (!inOpen[current] || joined[current] != join || f[current] != (entry ushr 32).toInt()) continue
            if (current == goalKey) return reconstructPath(cameFrom, startKey, goalKey)
            inOpen[current] = false
            val cx = current % widthTiles
            val cy = current / widthTiles
            for (dx in -1..1) {
                for (dy in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = cx + dx
                    val ny = cy + dy
                    if (!isPassable(nx, ny)) continue
                    val neighbour = tileKey(nx, ny)
                    val tentative = g[current] + 1
                    if (tentative < g[neighbour]) {
                        cameFrom[neighbour] = current
                        g[neighbour] = tentative
                        f[neighbour] = tentative + heuristic(neighbour, goalKey)
                        open(neighbour)
                    }
                }
            }
        }
        return emptyList()
    }

    private fun reconstructPath(cameFrom: IntArray, startKey: Int, goalKey: Int): List<Pair<Int, Int>> {
        val path = mutableListOf<Pair<Int, Int>>()
        var current = goalKey
        while (cameFrom[current] >= 0) {
            val (x, y) = keyToTile(current)
            path.add(tileCenter(x, y))
            current = cameFrom[current]
        }
        val (sx, sy) = keyToTile(startKey)
        path.add(tileCenter(sx, sy))
        path.reverse()
        return path
    }

    /** A growable list of ints without boxing. */
    private class IntArrayList {
        private var items = IntArray(64)
        private var count = 0
        fun add(value: Int) {
            if (count == items.size) items = items.copyOf(count * 2)
            items[count++] = value
        }
        operator fun get(index: Int): Int = items[index]
    }

    private fun heuristic(a: Int, b: Int): Int {
        val (ax, ay) = keyToTile(a)
        val (bx, by) = keyToTile(b)
        // Manhattan distance
        return kotlin.math.abs(ax - bx) + kotlin.math.abs(ay - by)
    }

    private fun isPassable(x: Int, y: Int): Boolean {
        if (x < 0 || x >= widthTiles || y < 0 || y >= heightTiles) return false
        return passable[x + y * widthTiles]
    }

    private fun tileKey(x: Int, y: Int): Int = x + y * widthTiles
    private fun keyToTile(key: Int): Pair<Int, Int> = key % widthTiles to key / widthTiles
    private fun pixelToTile(pixel: Int): Int = (pixel / LEGACY_TILE_SIZE).coerceIn(0, widthTiles - 1)
    private fun tileCenter(tx: Int, ty: Int): Pair<Int, Int> =
        (tx * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2) to (ty * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2)
}

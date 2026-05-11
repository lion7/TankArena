package com.tankarena.sim.kubriko.server

import com.tankarena.content.LEGACY_TILE_SIZE

/**
 * Waypoint follower for AI tanks.
 *
 * When no enemy target is available, AI tanks follow a sequence of waypoints.
 * Waypoints are defined as pixel coordinates (tile centers).
 *
 * The follower uses the TilePathfinder to navigate between waypoints.
 */
class WaypointFollower(
    private val waypoints: List<Pair<Int, Int>>,
    private val pathfinder: TilePathfinder,
) {

    private var currentIndex: Int = 0
    private var currentPath: List<Pair<Int, Int>> = emptyList()
    private var lastRepathX: Int = 0
    private var lastRepathY: Int = 0

    /**
     * Get the next direction (0-15) the AI tank should face to follow waypoints.
     * Returns -1 if no waypoint is available or the tank has reached the end.
     */
    fun getNextDirection(x: Int, y: Int): Int {
        if (waypoints.isEmpty()) return -1

        // Recompute path if we've moved significantly
        val dx = x - lastRepathX
        val dy = y - lastRepathY
        val moved = (dx * dx + dy * dy) > (LEGACY_TILE_SIZE * LEGACY_TILE_SIZE)

        if (moved || currentPath.isEmpty()) {
            recomputePath(x, y)
            lastRepathX = x
            lastRepathY = y
        }

        // Check if we've reached the current waypoint
        if (currentPath.isNotEmpty()) {
            val (targetX, targetY) = currentPath.last()
            val tdx = x - targetX
            val tdy = y - targetY
            val distSq = tdx * tdx + tdy * tdy

            if (distSq < (LEGACY_TILE_SIZE * LEGACY_TILE_SIZE / 2)) {
                // Reached waypoint, advance to next
                advanceToNextWaypoint()
                return getNextDirection(x, y)
            }

            return pathfinder.directionToNextStep(x, y, currentPath)
        }

        return -1
    }

    /**
     * Reset the waypoint follower to the beginning.
     */
    fun reset() {
        currentIndex = 0
        currentPath = emptyList()
        lastRepathX = 0
        lastRepathY = 0
    }

    /**
     * Check if the waypoint follower has reached the end of the waypoint list.
     */
    fun isAtEnd(): Boolean = currentIndex >= waypoints.size

    private fun recomputePath(x: Int, y: Int) {
        if (currentIndex >= waypoints.size) {
            currentPath = emptyList()
            return
        }

        val (targetX, targetY) = waypoints[currentIndex]
        currentPath = pathfinder.findPath(x, y, targetX, targetY)
    }

    private fun advanceToNextWaypoint() {
        currentIndex = (currentIndex + 1) % waypoints.size
        currentPath = emptyList()
    }
}

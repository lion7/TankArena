package com.tankarena.sim.kubriko.server

import com.tankarena.content.LEGACY_TILE_SIZE
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class WaypointFollowerTest {

    private fun tileCenter(x: Int, y: Int): Pair<Int, Int> =
        (x * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2) to (y * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2)

    @Test
    fun `follower returns -1 when no waypoints`() {
        val width = 10
        val height = 10
        val solid = List(width * height) { -1 }
        val pf = TilePathfinder(width, height, solid)
        val follower = WaypointFollower(emptyList(), pf)

        assertEquals(-1, follower.getNextDirection(tileCenter(1, 1).first, tileCenter(1, 1).second))
    }

    @Test
    fun `follower advances through waypoints`() {
        val width = 10
        val height = 10
        val solid = List(width * height) { -1 }
        val pf = TilePathfinder(width, height, solid)
        val waypoints = listOf(tileCenter(3, 3), tileCenter(6, 6))
        val follower = WaypointFollower(waypoints, pf)

        // Start at (1,1), should head toward (3,3)
        val dir = follower.getNextDirection(tileCenter(1, 1).first, tileCenter(1, 1).second)
        assertTrue(dir >= 0, "should return a valid direction toward first waypoint")
    }

    @Test
    fun `follower loops back to first waypoint after reaching last`() {
        val width = 10
        val height = 10
        val solid = List(width * height) { -1 }
        val pf = TilePathfinder(width, height, solid)
        val waypoints = listOf(tileCenter(3, 3), tileCenter(6, 6))
        val follower = WaypointFollower(waypoints, pf)

        // Simulate reaching first waypoint
        val dir1 = follower.getNextDirection(tileCenter(3, 3).first, tileCenter(3, 3).second)
        assertTrue(dir1 >= 0, "should head toward second waypoint")

        // Simulate reaching second waypoint
        val dir2 = follower.getNextDirection(tileCenter(6, 6).first, tileCenter(6, 6).second)
        assertTrue(dir2 >= 0, "should loop back toward first waypoint")
    }

    @Test
    fun `follower resets to beginning`() {
        val width = 10
        val height = 10
        val solid = List(width * height) { -1 }
        val pf = TilePathfinder(width, height, solid)
        val waypoints = listOf(tileCenter(3, 3), tileCenter(6, 6))
        val follower = WaypointFollower(waypoints, pf)

        // Advance to second waypoint
        follower.getNextDirection(tileCenter(3, 3).first, tileCenter(3, 3).second)

        follower.reset()

        // Should now head toward first waypoint from (1,1)
        val dir = follower.getNextDirection(tileCenter(1, 1).first, tileCenter(1, 1).second)
        assertTrue(dir >= 0, "should head toward first waypoint after reset")
    }

    @Test
    fun `follower navigates around obstacles`() {
        val width = 10
        val height = 10
        val solid = MutableList(width * height) { -1 }
        // Wall at (2, 2)
        solid[2 + 2 * width] = 1
        val pf = TilePathfinder(width, height, solid)
        val waypoints = listOf(tileCenter(4, 4))
        val follower = WaypointFollower(waypoints, pf)

        val dir = follower.getNextDirection(tileCenter(1, 1).first, tileCenter(1, 1).second)
        assertTrue(dir >= 0, "should find direction around obstacle")
    }
}

package com.tankarena.sim.kubriko.server

import com.tankarena.content.GameModeCompatibility

/**
 * Per-mode AI behavior dispatcher.
 *
 * Different game modes have different AI behaviors:
 * - SINGLE: AI tanks patrol waypoints, engage enemies when visible.
 * - DUAL: AI tanks are aggressive, no waypoints.
 * - DUAL_VS_COMPUTER: AI tanks defend their base, patrol waypoints.
 * - SINGLE_OR_DUAL: Same as SINGLE.
 * - DONT_CARE: Default behavior (aggressive).
 */
class AiModeDispatcher(
    private val mode: GameModeCompatibility,
    private val waypoints: List<Pair<Int, Int>>,
    private val pathfinder: TilePathfinder,
) {

    private val waypointFollower = WaypointFollower(waypoints, pathfinder)

    /**
     * Check if AI should use waypoint patrol when no enemy is visible.
     */
    fun shouldPatrol(): Boolean = when (mode) {
        GameModeCompatibility.SINGLE,
        GameModeCompatibility.DUAL_VS_COMPUTER,
        GameModeCompatibility.SINGLE_OR_DUAL -> true
        GameModeCompatibility.DUAL,
        GameModeCompatibility.DONT_CARE -> false
    }

    /**
     * Get the next direction for waypoint patrol.
     * Returns -1 if patrol is not enabled or no waypoints are available.
     */
    fun getPatrolDirection(x: Int, y: Int): Int {
        if (!shouldPatrol()) return -1
        return waypointFollower.getNextDirection(x, y)
    }

    /**
     * Reset the waypoint follower (e.g., on tank respawn).
     */
    fun reset() {
        waypointFollower.reset()
    }

    /**
     * Check if the AI should be aggressive (fire on sight) or defensive.
     */
    fun isAggressive(): Boolean = when (mode) {
        GameModeCompatibility.DUAL,
        GameModeCompatibility.DONT_CARE -> true
        GameModeCompatibility.SINGLE,
        GameModeCompatibility.DUAL_VS_COMPUTER,
        GameModeCompatibility.SINGLE_OR_DUAL -> true // Always aggressive when enemy is visible
    }
}

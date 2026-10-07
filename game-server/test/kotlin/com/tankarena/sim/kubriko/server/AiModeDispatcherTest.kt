package com.tankarena.sim.kubriko.server

import com.tankarena.content.GameModeCompatibility
import com.tankarena.content.LEGACY_TILE_SIZE
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class AiModeDispatcherTest {

    private fun tileCenter(x: Int, y: Int): Pair<Int, Int> =
        (x * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2) to (y * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2)

    private fun createDispatcher(mode: GameModeCompatibility, waypoints: List<Pair<Int, Int>> = emptyList()): AiModeDispatcher {
        val width = 10
        val height = 10
        val solid = List(width * height) { -1 }
        val pf = TilePathfinder(width, height, solid)
        return AiModeDispatcher(mode, waypoints, pf)
    }

    @Test
    fun `SINGLE mode enables patrol`() {
        val dispatcher = createDispatcher(GameModeCompatibility.SINGLE)
        assertTrue(dispatcher.shouldPatrol(), "SINGLE mode should enable patrol")
    }

    @Test
    fun `DUAL mode disables patrol`() {
        val dispatcher = createDispatcher(GameModeCompatibility.DUAL)
        assertFalse(dispatcher.shouldPatrol(), "DUAL mode should disable patrol")
    }

    @Test
    fun `DUAL_VS_COMPUTER mode enables patrol`() {
        val dispatcher = createDispatcher(GameModeCompatibility.DUAL_VS_COMPUTER)
        assertTrue(dispatcher.shouldPatrol(), "DUAL_VS_COMPUTER should enable patrol")
    }

    @Test
    fun `SINGLE_OR_DUAL mode enables patrol`() {
        val dispatcher = createDispatcher(GameModeCompatibility.SINGLE_OR_DUAL)
        assertTrue(dispatcher.shouldPatrol(), "SINGLE_OR_DUAL should enable patrol")
    }

    @Test
    fun `DONT_CARE mode disables patrol`() {
        val dispatcher = createDispatcher(GameModeCompatibility.DONT_CARE)
        assertFalse(dispatcher.shouldPatrol(), "DONT_CARE should disable patrol")
    }

    @Test
    fun `patrol returns -1 when patrol is disabled`() {
        val dispatcher = createDispatcher(GameModeCompatibility.DUAL)
        val (x, y) = tileCenter(1, 1)
        assertTrue(dispatcher.getPatrolDirection(x, y) < 0, "should return -1 when patrol is disabled")
    }

    @Test
    fun `patrol returns direction when patrol is enabled and waypoints exist`() {
        val waypoints = listOf(tileCenter(3, 3), tileCenter(6, 6))
        val dispatcher = createDispatcher(GameModeCompatibility.SINGLE, waypoints)
        val (x, y) = tileCenter(1, 1)
        assertTrue(dispatcher.getPatrolDirection(x, y) >= 0, "should return valid direction when patrol is enabled")
    }

    @Test
    fun `all modes are aggressive`() {
        for (mode in GameModeCompatibility.values()) {
            val dispatcher = createDispatcher(mode)
            assertTrue(dispatcher.isAggressive(), "$mode should be aggressive")
        }
    }

    @Test
    fun `reset clears waypoint progress`() {
        val waypoints = listOf(tileCenter(3, 3), tileCenter(6, 6))
        val dispatcher = createDispatcher(GameModeCompatibility.SINGLE, waypoints)

        // Advance
        dispatcher.getPatrolDirection(tileCenter(3, 3).first, tileCenter(3, 3).second)

        dispatcher.reset()

        // Should now head toward first waypoint
        val dir = dispatcher.getPatrolDirection(tileCenter(1, 1).first, tileCenter(1, 1).second)
        assertTrue(dir >= 0, "should head toward first waypoint after reset")
    }
}

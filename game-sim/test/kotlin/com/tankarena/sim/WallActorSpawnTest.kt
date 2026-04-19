package com.tankarena.sim

import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.sim.actor.WallActor
import com.tankarena.sim.runtime.SimulationHost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Validates the wall layer the host materialises: every solid map tile becomes
 * one [WallActor] with a tile-sized box mask, and four synthetic boundary
 * walls are added along the playfield edges. This replaces the deleted
 * `PassabilityTest.kt` semantics.
 */
class WallActorSpawnTest {

    @Test
    fun `every solid tile in the canonical map gets one wall actor`() {
        // Place two solid tiles diagonally inside a 4x3 map.
        val map = TestMaps.empty(widthTiles = 4, heightTiles = 3).copy(
            layers = TestMaps.empty(widthTiles = 4, heightTiles = 3).layers.copy(
                solid = listOf(
                    -1, -1, -1, -1,
                    -1,  0, -1,  0,
                    -1, -1, -1, -1,
                ),
            ),
        )
        val sim = SimulationFactory.fromCanonicalMap(map)
        val host = sim.unwrapHostForTest()
        val walls = host.wallsForDebug()

        // 2 solid tiles + 4 boundary walls.
        assertEquals(6, walls.size, "expected one wall per solid tile plus four boundary walls")

        val tileWalls = walls.filter { it.halfWidth == LEGACY_TILE_SIZE / 2 && it.halfHeight == LEGACY_TILE_SIZE / 2 }
        assertEquals(2, tileWalls.size)
        assertNotNull(tileWalls.firstOrNull { wall ->
            wall.centerX == 1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2 &&
                wall.centerY == 1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2
        })
        assertNotNull(tileWalls.firstOrNull { wall ->
            wall.centerX == 3 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2 &&
                wall.centerY == 1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2
        })

        val boundaryWalls = walls.filterNot { tileWalls.contains(it) }
        assertEquals(4, boundaryWalls.size)
        // All boundary walls must lie outside the playfield bounds.
        val w = host.bounds.widthPixels
        val h = host.bounds.heightPixels
        assertTrue(
            boundaryWalls.all { wall ->
                wall.centerX < 0 || wall.centerY < 0 || wall.centerX > w || wall.centerY > h
            },
            "boundary walls should be outside playfield",
        )
    }

    @Test
    fun `empty map produces only the four boundary walls`() {
        val map = TestMaps.empty(widthTiles = 5, heightTiles = 4)
        val sim = SimulationFactory.fromCanonicalMap(map)
        val walls = sim.unwrapHostForTest().wallsForDebug()

        assertEquals(4, walls.size, "no solid tiles ? only boundary walls expected")
    }
}

/** Test-only accessor for the host that backs a [TankArenaSimulation]. */
internal fun TankArenaSimulation.unwrapHostForTest(): SimulationHost = SimulationHostAccess.from(this)

internal object SimulationHostAccess {
    fun from(sim: TankArenaSimulation): SimulationHost {
        val field = sim.javaClass.getDeclaredField("host")
        field.isAccessible = true
        return field.get(sim) as SimulationHost
    }
}

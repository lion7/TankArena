package com.tankarena.sim.kubriko.server

import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.MapMetadata
import com.tankarena.content.TankArenaWorld
import com.tankarena.content.TerrainGridBuilder
import com.tankarena.content.TileLayers
import com.tankarena.protocol.snapshot.TerrainMaterial
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerrainGridBuilderTest {

    private fun makeMetadata(w: Int = 10, h: Int = 10, world: TankArenaWorld = TankArenaWorld.DESERT) =
        MapMetadata(name = "test", widthTiles = w, heightTiles = h, missionCode = "T01", world = world)

    @Test
    fun `builder returns empty grid when layers is null`() {
        val grid = TerrainGridBuilder.build(null, makeMetadata())
        assertEquals(10, grid.tileWidth)
        assertEquals(10, grid.tileHeight)
        assertEquals(TerrainMaterial.NORMAL, grid.materialAt(0, 0))
        assertEquals(1.0f, grid.speedAt(0, 0))
    }

    @Test
    fun `builder resolves water from base layer`() {
        // pc0 index 260 = WAT1 (WATER, 0.2)
        val layers = TileLayers(
            base = listOf(260, -1, -1, -1, -1, -1, -1, -1, -1, -1),
            solid = List(10) { -1 },
            top = List(10) { -1 },
            goalLayer = emptyList(),
            bonusLayer = emptyList(),
            manTypeLayer = emptyList(),
            manAmountLayer = emptyList(),
        )
        val grid = TerrainGridBuilder.build(layers, makeMetadata(w = 1, h = 10))
        assertEquals(TerrainMaterial.WATER, grid.materialAt(0, 0))
        assertEquals(0.2f, grid.speedAt(0, 0))
        assertEquals(TerrainMaterial.NORMAL, grid.materialAt(0, 1))
    }

    @Test
    fun `builder resolves oil from base layer in city world`() {
        // pc2 index 254 = OLIEH (OIL, 0.8)
        val layers = TileLayers(
            base = listOf(254),
            solid = listOf(-1),
            top = listOf(-1),
            goalLayer = emptyList(),
            bonusLayer = emptyList(),
            manTypeLayer = emptyList(),
            manAmountLayer = emptyList(),
        )
        val grid = TerrainGridBuilder.build(layers, makeMetadata(w = 1, h = 1, world = TankArenaWorld.CITY))
        assertEquals(TerrainMaterial.OIL, grid.materialAt(0, 0))
        assertEquals(0.8f, grid.speedAt(0, 0))
    }

    @Test
    fun `builder resolves lava from base layer`() {
        // pc0 index 452 = LAVAPUT (LAVA, 0.9)
        val layers = TileLayers(
            base = listOf(452),
            solid = listOf(-1),
            top = listOf(-1),
            goalLayer = emptyList(),
            bonusLayer = emptyList(),
            manTypeLayer = emptyList(),
            manAmountLayer = emptyList(),
        )
        val grid = TerrainGridBuilder.build(layers, makeMetadata(w = 1, h = 1))
        assertEquals(TerrainMaterial.LAVA, grid.materialAt(0, 0))
        assertEquals(0.9f, grid.speedAt(0, 0))
    }

    @Test
    fun `builder multiplies speed from base and top layers`() {
        // pc0 index 260 = WAT1 (WATER, 0.2) in base
        // pc0 index 281 = BRIDGE (BRIDGE, 1.0) in top
        // Expected speed: 0.2 * 1.0 = 0.2
        val layers = TileLayers(
            base = listOf(260),
            solid = listOf(-1),
            top = listOf(281),
            goalLayer = emptyList(),
            bonusLayer = emptyList(),
            manTypeLayer = emptyList(),
            manAmountLayer = emptyList(),
        )
        val grid = TerrainGridBuilder.build(layers, makeMetadata(w = 1, h = 1))
        // Top layer material (BRIDGE) overrides base (WATER)
        assertEquals(TerrainMaterial.BRIDGE, grid.materialAt(0, 0))
        assertEquals(0.2f, grid.speedAt(0, 0))
    }

    @Test
    fun `builder top layer material overrides base`() {
        // pc0 index 260 = WAT1 (WATER) in base
        // pc0 index 281 = BRIDGE in top
        val layers = TileLayers(
            base = listOf(260),
            solid = listOf(-1),
            top = listOf(281),
            goalLayer = emptyList(),
            bonusLayer = emptyList(),
            manTypeLayer = emptyList(),
            manAmountLayer = emptyList(),
        )
        val grid = TerrainGridBuilder.build(layers, makeMetadata(w = 1, h = 1))
        assertEquals(TerrainMaterial.BRIDGE, grid.materialAt(0, 0))
    }

    @Test
    fun `builder uses base material when top is -1`() {
        val layers = TileLayers(
            base = listOf(260),
            solid = listOf(-1),
            top = listOf(-1),
            goalLayer = emptyList(),
            bonusLayer = emptyList(),
            manTypeLayer = emptyList(),
            manAmountLayer = emptyList(),
        )
        val grid = TerrainGridBuilder.build(layers, makeMetadata(w = 1, h = 1))
        assertEquals(TerrainMaterial.WATER, grid.materialAt(0, 0))
    }

    @Test
    fun `builder handles multi-tile grid`() {
        // 2x2 grid: water, oil, lava, normal
        val layers = TileLayers(
            base = listOf(260, 398, 452, -1), // WAT1, SHIT2(OIL), LAVAPUT, nothing
            solid = List(4) { -1 },
            top = List(4) { -1 },
            goalLayer = emptyList(),
            bonusLayer = emptyList(),
            manTypeLayer = emptyList(),
            manAmountLayer = emptyList(),
        )
        val grid = TerrainGridBuilder.build(layers, makeMetadata(w = 2, h = 2))
        assertEquals(TerrainMaterial.WATER, grid.materialAt(0, 0))
        assertEquals(TerrainMaterial.OIL, grid.materialAt(1, 0))
        assertEquals(TerrainMaterial.LAVA, grid.materialAt(0, 1))
        assertEquals(TerrainMaterial.NORMAL, grid.materialAt(1, 1))
    }

    @Test
    fun `builder resolves pit from base layer`() {
        // pc0 index 350 = PIT_A (PIT_BIG)
        val layers = TileLayers(
            base = listOf(350),
            solid = listOf(-1),
            top = listOf(-1),
            goalLayer = emptyList(),
            bonusLayer = emptyList(),
            manTypeLayer = emptyList(),
            manAmountLayer = emptyList(),
        )
        val grid = TerrainGridBuilder.build(layers, makeMetadata(w = 1, h = 1))
        assertEquals(TerrainMaterial.PIT_BIG, grid.materialAt(0, 0))
    }

    @Test
    fun `builder resolves small pit from base layer`() {
        // pc0 index 328 = PITDA (PIT_SMALL)
        val layers = TileLayers(
            base = listOf(328),
            solid = listOf(-1),
            top = listOf(-1),
            goalLayer = emptyList(),
            bonusLayer = emptyList(),
            manTypeLayer = emptyList(),
            manAmountLayer = emptyList(),
        )
        val grid = TerrainGridBuilder.build(layers, makeMetadata(w = 1, h = 1))
        assertEquals(TerrainMaterial.PIT_SMALL, grid.materialAt(0, 0))
    }

    @Test
    fun `builder resolves runway from base layer`() {
        // pc0 index 248 = AIRH1 (RUNWAY, 1.3)
        val layers = TileLayers(
            base = listOf(248),
            solid = listOf(-1),
            top = listOf(-1),
            goalLayer = emptyList(),
            bonusLayer = emptyList(),
            manTypeLayer = emptyList(),
            manAmountLayer = emptyList(),
        )
        val grid = TerrainGridBuilder.build(layers, makeMetadata(w = 1, h = 1))
        assertEquals(TerrainMaterial.RUNWAY, grid.materialAt(0, 0))
        assertEquals(1.3f, grid.speedAt(0, 0))
    }

    @Test
    fun `builder resolves road from base layer`() {
        // pc0 index 231 = WEGH (ROAD, 1.3)
        val layers = TileLayers(
            base = listOf(231),
            solid = listOf(-1),
            top = listOf(-1),
            goalLayer = emptyList(),
            bonusLayer = emptyList(),
            manTypeLayer = emptyList(),
            manAmountLayer = emptyList(),
        )
        val grid = TerrainGridBuilder.build(layers, makeMetadata(w = 1, h = 1))
        assertEquals(TerrainMaterial.ROAD, grid.materialAt(0, 0))
        assertEquals(1.3f, grid.speedAt(0, 0))
    }
}

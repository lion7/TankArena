package com.tankarena.sim

import com.tankarena.content.CanonicalMapDefinition
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.MapMetadata
import com.tankarena.content.MissionText
import com.tankarena.content.TileLayers
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PassabilityTest {
    @Test
    fun `solid tile in canonical map registers as blocked at its pixel range`() {
        val map = makeMap(widthTiles = 3, heightTiles = 3, solidIndex = indexOf(1, 1, width = 3))
        val passability = Passability.fromMap(map)

        val centerOfSolid = LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2
        assertTrue(passability.isSolidPixel(centerOfSolid, centerOfSolid))
        assertTrue(passability.isSolidTile(1, 1))

        val centerOfEmpty = LEGACY_TILE_SIZE / 2
        assertFalse(passability.isSolidPixel(centerOfEmpty, centerOfEmpty))
        assertFalse(passability.isSolidTile(0, 0))
    }

    @Test
    fun `out of bounds is treated as solid`() {
        val map = makeMap(widthTiles = 3, heightTiles = 3, solidIndex = -1)
        val passability = Passability.fromMap(map)

        assertTrue(passability.isSolidTile(-1, 0))
        assertTrue(passability.isSolidTile(3, 0))
        assertTrue(passability.isSolidPixel(-5, 0))
        assertTrue(passability.isSolidPixel(passability.widthPixels + 1, 0))
    }

    @Test
    fun `aabb sampling detects partial overlap with solid tile`() {
        val map = makeMap(widthTiles = 3, heightTiles = 3, solidIndex = indexOf(1, 1, width = 3))
        val passability = Passability.fromMap(map)

        val tankHalf = LEGACY_TILE_SIZE / 2 - 2

        assertFalse(passability.isAabbBlocked(LEGACY_TILE_SIZE / 2, LEGACY_TILE_SIZE / 2, tankHalf, tankHalf))

        val justInside = LEGACY_TILE_SIZE + 2
        assertTrue(passability.isAabbBlocked(justInside, justInside, tankHalf, tankHalf))
    }

    private fun makeMap(widthTiles: Int, heightTiles: Int, solidIndex: Int): CanonicalMapDefinition {
        val cellCount = widthTiles * heightTiles
        val solid = MutableList(cellCount) { -1 }
        if (solidIndex in 0 until cellCount) {
            solid[solidIndex] = 0
        }
        return CanonicalMapDefinition(
            metadata = MapMetadata(
                name = "passability-fixture",
                widthTiles = widthTiles,
                heightTiles = heightTiles,
                missionCode = "TEST",
            ),
            layers = TileLayers(
                base = List(cellCount) { -1 },
                solid = solid,
                top = List(cellCount) { -1 },
                goalLayer = List(cellCount) { 0 },
                bonusLayer = List(cellCount) { -1 },
                manTypeLayer = List(cellCount) { 0 },
                manAmountLayer = List(cellCount) { 0 },
            ),
            missionText = MissionText(),
            objects = emptyList(),
        )
    }

    private fun indexOf(x: Int, y: Int, width: Int): Int = x + y * width
}

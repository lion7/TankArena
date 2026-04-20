package com.tankarena.sim

import com.tankarena.content.AuthoredObject
import com.tankarena.content.CanonicalMapDefinition
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.MapMetadata
import com.tankarena.content.MissionText
import com.tankarena.content.ObjectKinds
import com.tankarena.content.TileLayers
import com.tankarena.input.PlayerIntentFrame
import kotlin.test.Test
import kotlin.test.assertTrue

class CollisionTest {
    @Test
    fun `tank cannot enter a solid tile to the right`() {
        val map = TestMaps.withSingleSolidAt(widthTiles = 5, heightTiles = 3, solidX = 3, solidY = 1)
        val sim = SimulationFactory.fromCanonicalMap(map.copy(objects = playerAt(centerOfTile(1, 1), direction = 4)))

        repeat(40) { sim.tick(mapOf(0 to PlayerIntentFrame(forward = true))) }
        val tank = sim.currentState().tanks.single()

        // Tank position should not have entered the solid tile (tile 3 starts at x = 3*33 = 99).
        assertTrue(
            tank.position.x < 3 * LEGACY_TILE_SIZE,
            "tank x=${tank.position.x} should be left of wall at x=${3 * LEGACY_TILE_SIZE}",
        )
        assertTrue(
            tank.position.x > centerOfTile(1, 1).first,
            "tank should have moved right of its start",
        )
    }

    @Test
    fun `tank can drive freely when no walls block`() {
        val map = TestMaps.empty(widthTiles = 6, heightTiles = 3)
        val start = centerOfTile(1, 1)
        val sim = SimulationFactory.fromCanonicalMap(map.copy(objects = playerAt(start, direction = 4)))

        repeat(5) { sim.tick(mapOf(0 to PlayerIntentFrame(forward = true))) }

        val tank = sim.currentState().tanks.single()
        assertTrue(tank.position.x > start.first)
    }

    @Test
    fun `tank slides along wall instead of stopping completely`() {
        // Wall on the right at column 3, tank at column 1, drive diagonally down-right.
        val map = TestMaps.verticalWall(widthTiles = 5, heightTiles = 5, wallX = 3)
        val start = centerOfTile(1, 2)
        val sim = SimulationFactory.fromCanonicalMap(map.copy(objects = playerAt(start, direction = 6)))

        repeat(20) { sim.tick(mapOf(0 to PlayerIntentFrame(forward = true))) }

        val tank = sim.currentState().tanks.single()
        // X should be blocked at the wall.
        assertTrue(tank.position.x < 3 * LEGACY_TILE_SIZE)
        // Y should still progress because Y axis was free.
        assertTrue(tank.position.y > start.second)
    }

    private fun playerAt(position: Pair<Int, Int>, direction: Int): List<AuthoredObject> = listOf(
        AuthoredObject(
            id = "p1",
            kind = ObjectKinds.PLAYER_START,
            x = position.first,
            y = position.second,
            properties = mapOf("direction" to direction.toString()),
        ),
    )

    private fun centerOfTile(tx: Int, ty: Int): Pair<Int, Int> =
        tx * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2 to ty * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2
}

internal object TestMaps {
    fun empty(widthTiles: Int, heightTiles: Int): CanonicalMapDefinition = makeMap(widthTiles, heightTiles) { _, _ -> false }

    fun withSingleSolidAt(widthTiles: Int, heightTiles: Int, solidX: Int, solidY: Int): CanonicalMapDefinition =
        makeMap(widthTiles, heightTiles) { x, y -> x == solidX && y == solidY }

    fun verticalWall(widthTiles: Int, heightTiles: Int, wallX: Int): CanonicalMapDefinition =
        makeMap(widthTiles, heightTiles) { x, _ -> x == wallX }

    private fun makeMap(
        widthTiles: Int,
        heightTiles: Int,
        isSolid: (Int, Int) -> Boolean,
    ): CanonicalMapDefinition {
        val cellCount = widthTiles * heightTiles
        val solid = MutableList(cellCount) { idx ->
            val x = idx % widthTiles
            val y = idx / widthTiles
            if (isSolid(x, y)) 0 else -1
        }
        return CanonicalMapDefinition(
            metadata = MapMetadata(
                name = "collision-fixture",
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
}

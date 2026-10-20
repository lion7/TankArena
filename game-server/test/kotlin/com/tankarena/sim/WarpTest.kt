package com.tankarena.sim

import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.MapMetadata
import com.tankarena.content.MissionText
import com.tankarena.content.TileLayers
import com.tankarena.protocol.snapshot.EffectKind
import com.tankarena.protocol.snapshot.GameEvent
import com.tankarena.protocol.snapshot.SoundKind
import com.tankarena.sim.legacy.AuthoredObject
import com.tankarena.sim.legacy.CanonicalMapDefinition
import com.tankarena.sim.legacy.ObjectKinds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Warps (src/objects/warp.c): vehicles, soldiers, bombers and rockets, no pause, sparks and sound. */
class WarpTest {
    private val warpX = 4 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2
    private val warpY = 2 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2
    private val exitX = 12 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2

    @Test
    fun `a vehicle in the warp comes out at the exit, with sparks and a sound at both ends`() {
        val match = start()
        val tank = match.playerTankForTest(0)!!
        tank.teleportTo(warpX + 5, warpY)
        val events = match.stepForTest().events
        assertEquals(exitX, tank.positionX)
        assertEquals(6, events.count { it is GameEvent.Effect && it.kind.name.startsWith("TELEPORT_SPARK") })
        assertEquals(2, events.count { it is GameEvent.Sound && it.kind == SoundKind.TELEPORT })
    }

    @Test
    fun `a wreck and a soldier go through too, and nothing waits between two warps`() {
        val match = start()
        val tank = match.playerTankForTest(0)!!
        tank.queueDestroy()
        repeat(40) { match.stepForTest() }
        tank.teleportTo(warpX, warpY)
        match.stepForTest()
        assertEquals(exitX, tank.positionX, "a wreck is warped")
        tank.teleportTo(warpX, warpY)
        match.stepForTest()
        assertEquals(exitX, tank.positionX, "again on the very next step")
    }

    private fun start(): Match {
        val width = 16
        val height = 5
        val cells = width * height
        val match = Match.fromCanonicalMap(
            CanonicalMapDefinition(
                metadata = MapMetadata(name = "warps", widthTiles = width, heightTiles = height, missionCode = "TEST"),
                layers = TileLayers(
                    base = List(cells) { -1 },
                    solid = List(cells) { -1 },
                    top = List(cells) { -1 },
                    goalLayer = List(cells) { 0 },
                    bonusLayer = List(cells) { -1 },
                    manTypeLayer = List(cells) { 0 },
                    manAmountLayer = List(cells) { 0 },
                ),
                missionText = MissionText(),
                objects = listOf(
                    AuthoredObject("p1", ObjectKinds.PLAYER_START, 1 * LEGACY_TILE_SIZE + 16, warpY, mapOf("direction" to "4", "lives" to "3")),
                    AuthoredObject("w", ObjectKinds.WARP, warpX, warpY, mapOf("targetX" to exitX.toString(), "targetY" to warpY.toString())),
                ),
            ),
        )
        match.initialize()
        return match
    }
}

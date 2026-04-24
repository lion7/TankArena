package com.tankarena.sim.kubriko.server

import com.tankarena.content.AuthoredObject
import com.tankarena.content.CanonicalMapDefinition
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.MapMetadata
import com.tankarena.content.MissionText
import com.tankarena.content.ObjectKinds
import com.tankarena.content.TileLayers
import com.tankarena.protocol.InputFrame
import com.tankarena.protocol.snapshot.TankState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalMatchClientTest {

    @Test
    fun `currentServerFrame returns a fully populated frame immediately after construction`() {
        val client = LocalMatchClient.fromCanonicalMap(simpleMap())

        val initial = client.currentServerFrame()

        assertEquals(0L, initial.tick)
        assertEquals(initial.tick, initial.world.tick)
        assertTrue(initial.world.actors.any { it is TankState })
        assertEquals(1, initial.playerViews.size, "one player view per controlled tank")
        val view = initial.playerViews.single()
        assertEquals(0, view.playerId)
        assertTrue(view.hud.missionCode.isNotEmpty(), "HUD carries the mission code")

        client.dispose()
    }

    @Test
    fun `tick advances the frame while preserving actor identities`() {
        val client = LocalMatchClient.fromCanonicalMap(simpleMap())
        val baseIds = client.currentServerFrame().world.actors.map { it.actorId }.toSet()

        var frame = client.currentServerFrame()
        repeat(30) { frame = client.tick() }

        assertEquals(30L, frame.tick)
        assertEquals(baseIds, frame.world.actors.map { it.actorId }.toSet())
        assertEquals(frame, client.currentServerFrame())
        client.dispose()
    }

    @Test
    fun `submitInput accepts inputs per player without affecting server frame shape`() {
        val client = LocalMatchClient.fromCanonicalMap(simpleMap())

        client.submitInput(InputFrame(playerId = 0, inputSequence = 1, forward = true))
        client.submitInput(InputFrame(playerId = 1, inputSequence = 2, firePrimary = true))
        val frame = client.tick()

        assertEquals(1L, frame.tick)
        assertTrue(frame.world.actors.isNotEmpty())
        client.dispose()
    }

    private fun simpleMap(): CanonicalMapDefinition {
        val width = 6
        val height = 4
        val cellCount = width * height
        val solid = MutableList(cellCount) { idx ->
            val x = idx % width
            val y = idx / width
            val onEdge = x == 0 || y == 0 || x == width - 1 || y == height - 1
            if (onEdge) 0 else -1
        }
        return CanonicalMapDefinition(
            metadata = MapMetadata(
                name = "local-client-fixture",
                widthTiles = width,
                heightTiles = height,
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
            objects = listOf(
                AuthoredObject(
                    id = "p1",
                    kind = ObjectKinds.PLAYER_START,
                    x = 2 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    y = 2 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    properties = mapOf("direction" to "0", "lives" to "3"),
                ),
            ),
        )
    }
}

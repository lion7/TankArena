package com.tankarena.sim.kubriko.server

import com.tankarena.sim.kubriko.server.legacy.AuthoredObject
import com.tankarena.sim.kubriko.server.legacy.CanonicalMapDefinition
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.MapMetadata
import com.tankarena.content.MissionText
import com.tankarena.sim.kubriko.server.legacy.ObjectKinds
import com.tankarena.content.TileLayers
import com.tankarena.protocol.Team
import com.tankarena.protocol.snapshot.RadarContactKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServerPlayerViewTest {

    @Test
    fun `player view camera follows the controlled tank and HUD mirrors tank state`() {
        val prototype = ServerMatchPrototype.fromCanonicalMap(
            basicMap(goalContribution = 40, missionCode = "M01"),
        )
        prototype.initialize()

        val view = prototype.buildPlayerViews().single()
        assertEquals(0, view.playerId)
        assertEquals(prototype.snapshot().playerTankX(), view.cameraCenterX)
        assertEquals("M01", view.hud.missionCode)
        assertEquals(100, view.hud.armor)
        assertEquals(3, view.hud.lives)
        assertEquals(0, view.hud.missionProgress)

        val goalContact = view.radar.single { it.kind == RadarContactKind.GOAL }
        assertEquals(Team.PLAYER, goalContact.team)
        assertTrue(view.radar.none { it.actorId == view.controlledActorId }, "self is excluded from radar")
        prototype.dispose()
    }

    private fun com.tankarena.protocol.snapshot.WorldSnapshot.playerTankX(): Int =
        actors.filterIsInstance<com.tankarena.protocol.snapshot.TankState>().single { it.controlled }.x

    private fun basicMap(goalContribution: Int, missionCode: String): CanonicalMapDefinition {
        val widthTiles = 8
        val heightTiles = 5
        val cellCount = widthTiles * heightTiles
        return CanonicalMapDefinition(
            metadata = MapMetadata(
                name = "view-fixture",
                widthTiles = widthTiles,
                heightTiles = heightTiles,
                missionCode = missionCode,
            ),
            layers = TileLayers(
                base = List(cellCount) { -1 },
                solid = MutableList(cellCount) { -1 },
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
                AuthoredObject(
                    id = "g1",
                    kind = ObjectKinds.GOAL,
                    x = 5 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    y = 2 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    properties = mapOf("who" to "0", "contribution" to goalContribution.toString()),
                ),
            ),
        )
    }
}

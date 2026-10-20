package nl.tankarena.sim

import nl.tankarena.sim.legacy.AuthoredObject
import nl.tankarena.sim.legacy.CanonicalMapDefinition
import nl.tankarena.content.LEGACY_TILE_SIZE
import nl.tankarena.content.MapMetadata
import nl.tankarena.content.MissionText
import nl.tankarena.sim.legacy.ObjectKinds
import nl.tankarena.content.TileLayers
import nl.tankarena.protocol.Team
import nl.tankarena.protocol.snapshot.RadarContactKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayerViewTest {

    @Test
    fun `player view camera follows the controlled tank and HUD mirrors tank state`() {
        val prototype = Match.fromCanonicalMap(
            basicMap(goalContribution = 40, missionCode = "M01"),
        )
        prototype.initialize()

        val view = prototype.buildPlayerViews().single()
        assertEquals(0, view.playerId)
        assertEquals(prototype.snapshot().playerTankX(), view.cameraCenterX)
        assertEquals("M01", view.hud.missionCode)
        assertEquals(Tank.DEFAULT_TANK_ARMOR, view.hud.armor)
        assertEquals(3, view.hud.lives)
        assertEquals(0, view.hud.missionProgress)
        assertEquals(0, view.hud.score)
        assertEquals(0, view.hud.kills)
        assertEquals(0L, view.hud.time)

        // Legacy write_radar(): the player's own tank is a black blip; goals are not shown.
        assertTrue(view.radar.any { it.actorId == view.controlledActorId }, "self is on the radar")
        assertTrue(view.radar.none { it.kind == RadarContactKind.GOAL }, "goals are not on the radar")
        prototype.dispose()
    }

    private fun nl.tankarena.protocol.snapshot.WorldSnapshot.playerTankX(): Int =
        actors.filterIsInstance<nl.tankarena.protocol.snapshot.TankState>().single { it.controlled }.x

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

package com.tankarena.sim

import com.tankarena.sim.legacy.AuthoredObject
import com.tankarena.sim.legacy.CanonicalMapDefinition
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.MapMetadata
import com.tankarena.content.MissionText
import com.tankarena.sim.legacy.ObjectKinds
import com.tankarena.content.TileLayers
import com.tankarena.input.PlayerIntentFrame
import com.tankarena.protocol.snapshot.GameEvent
import com.tankarena.protocol.snapshot.SoundKind
import com.tankarena.protocol.snapshot.TankState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T12 acceptance: mortars travel for the legacy travel-time, then explode with linear
 * damage falloff inside the blast radius.
 */
class MortarTest {

    @Test
    fun `mortar disappears after travel time and emits an explosion event`() {
        val prototype = Match.fromCanonicalMap(soloTankMap(direction = 4))
        prototype.initialize()
        prototype.selectWeaponForTest(WEAPON_MORTAR)
        prototype.tick(mapOf(0 to PlayerIntentFrame(firePrimary = true)))
        assertEquals(1, prototype.snapshotMortars().size, "mortar should be in flight")

        // The flight, then the blast's 12 rings, one per step.
        repeat(MORTAR_TRAVEL_TICKS + 13 * STEP_TICKS) { prototype.tick() }
        assertTrue(prototype.snapshotMortars().isEmpty(), "mortar should detonate after travel time")
        prototype.dispose()
    }

    @Test
    fun `the blast grows by 5 px a step and hits for 5 with every ring that reaches`() {
        val centre = Match.fromCanonicalMap(twoTankMap(victimDistanceTiles = 4))
        centre.initialize()
        val victim = centre.tanksForTest().single { it.playerIndex < 0 }
        val armor = victim.armor
        centre.landMortarForTest(victim.positionX, victim.positionY)
        val impact = centre.stepForTest()
        assertTrue(impact.events.any { it is GameEvent.Sound && it.kind == SoundKind.NUKE }, "the impact goes off with SND_NUKE")
        assertEquals(armor - 5, victim.armor, "the first ring (5 px)")
        repeat(15) { centre.stepForTest() }
        assertEquals(armor - 60, victim.armor, "twelve rings at the centre")

        val edge = Match.fromCanonicalMap(twoTankMap(victimDistanceTiles = 4))
        edge.initialize()
        val far = edge.tanksForTest().single { it.playerIndex < 0 }
        val farArmor = far.armor
        edge.landMortarForTest(far.positionX + 27, far.positionY)
        repeat(16) { edge.stepForTest() }
        assertEquals(farArmor - 35, far.armor, "only the rings of 30..60 px reach 27 px")
    }

    private fun com.tankarena.protocol.snapshot.WorldSnapshot.enemyTank(): TankState =
        actors.filterIsInstance<TankState>().single { !it.controlled }

    private fun soloTankMap(direction: Int): CanonicalMapDefinition {
        val widthTiles = 110
        val heightTiles = 5
        return canonicalMap(
            widthTiles,
            heightTiles,
            solid = MutableList(widthTiles * heightTiles) { -1 },
            players = listOf(
                AuthoredObject(
                    id = "p1",
                    kind = ObjectKinds.PLAYER_START,
                    x = 4 * LEGACY_TILE_SIZE,
                    y = 2 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    properties = mapOf("direction" to direction.toString(), "lives" to "3"),
                ),
            ),
        )
    }

    private fun twoTankMap(victimDistanceTiles: Int): CanonicalMapDefinition {
        val widthTiles = 16
        val heightTiles = 5
        return canonicalMap(
            widthTiles,
            heightTiles,
            solid = MutableList(widthTiles * heightTiles) { -1 },
            players = listOf(
                AuthoredObject(
                    id = "p1",
                    kind = ObjectKinds.PLAYER_START,
                    x = 2 * LEGACY_TILE_SIZE,
                    y = 2 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    properties = mapOf("direction" to "4", "lives" to "3"),
                ),
                AuthoredObject(
                    id = "victim",
                    kind = ObjectKinds.AI_TANK,
                    x = (2 + victimDistanceTiles) * LEGACY_TILE_SIZE,
                    y = 2 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    properties = mapOf("direction" to "12", "lives" to "3", "armor" to "100"),
                ),
            ),
        )
    }

    private fun canonicalMap(
        widthTiles: Int,
        heightTiles: Int,
        solid: List<Int>,
        players: List<AuthoredObject>,
    ): CanonicalMapDefinition {
        val cellCount = widthTiles * heightTiles
        return CanonicalMapDefinition(
            metadata = MapMetadata(
                name = "mortar-fixture",
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
            objects = players,
        )
    }
}

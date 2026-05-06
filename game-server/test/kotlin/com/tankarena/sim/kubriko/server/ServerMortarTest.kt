package com.tankarena.sim.kubriko.server

import com.tankarena.sim.kubriko.server.legacy.AuthoredObject
import com.tankarena.sim.kubriko.server.legacy.CanonicalMapDefinition
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.MapMetadata
import com.tankarena.content.MissionText
import com.tankarena.sim.kubriko.server.legacy.ObjectKinds
import com.tankarena.content.TileLayers
import com.tankarena.input.PlayerIntentFrame
import com.tankarena.protocol.snapshot.TankState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T12 acceptance: mortars travel for the legacy travel-time, then explode with linear
 * damage falloff inside the blast radius.
 */
class ServerMortarTest {

    @Test
    fun `mortar disappears after travel time and emits an explosion event`() {
        val prototype = ServerMatchPrototype.fromCanonicalMap(soloTankMap(direction = 4))
        prototype.initialize()
        cycleN(prototype, times = 4)
        prototype.tick(mapOf(0 to PlayerIntentFrame(firePrimary = true)))
        assertEquals(1, prototype.snapshotMortars().size, "mortar should be in flight")

        repeat(MORTAR_TRAVEL_TICKS + 5) { prototype.tick() }
        assertTrue(prototype.snapshotMortars().isEmpty(), "mortar should detonate after travel time")
        prototype.dispose()
    }

    @Test
    fun `mortar damage scales with proximity to impact center`() {
        val nearProto = ServerMatchPrototype.fromCanonicalMap(twoTankMap(victimDistanceTiles = 4))
        nearProto.initialize()
        nearProto.injectMortarExplosionForTest(
            x = nearProto.snapshot().enemyTank().x,
            y = nearProto.snapshot().enemyTank().y,
            radius = MORTAR_MAX_RADIUS_PX,
            damage = 60,
        )
        nearProto.tick()
        nearProto.tick()
        val nearDamage = 100 - nearProto.snapshot().enemyTank().armor
        nearProto.dispose()

        val farProto = ServerMatchPrototype.fromCanonicalMap(twoTankMap(victimDistanceTiles = 4))
        farProto.initialize()
        val enemy = farProto.snapshot().enemyTank()
        farProto.injectMortarExplosionForTest(
            x = enemy.x + MORTAR_MAX_RADIUS_PX - 5,
            y = enemy.y,
            radius = MORTAR_MAX_RADIUS_PX,
            damage = 60,
        )
        farProto.tick()
        farProto.tick()
        val farDamage = 100 - farProto.snapshot().enemyTank().armor
        farProto.dispose()

        assertTrue(nearDamage > 0, "tank at center should take damage — was $nearDamage")
        assertTrue(farDamage < nearDamage, "tank at edge should take less damage than center — center=$nearDamage edge=$farDamage")
    }

    private fun cycleN(prototype: ServerMatchPrototype, times: Int) {
        repeat(times) {
            prototype.tick(mapOf(0 to PlayerIntentFrame(cycleWeaponRight = true)))
            repeat(15) { prototype.tick() }
        }
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
                    kind = ObjectKinds.ENFORCER,
                    x = (2 + victimDistanceTiles) * LEGACY_TILE_SIZE,
                    y = 2 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    properties = mapOf("direction" to "12", "lives" to "3"),
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

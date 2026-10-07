package com.tankarena.sim.kubriko.server

import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.MapMetadata
import com.tankarena.content.MissionText
import com.tankarena.content.TileLayers
import com.tankarena.input.PlayerIntentFrame
import com.tankarena.protocol.snapshot.EffectKind
import com.tankarena.protocol.snapshot.GameEvent
import com.tankarena.sim.kubriko.server.legacy.AuthoredObject
import com.tankarena.sim.kubriko.server.legacy.CanonicalMapDefinition
import com.tankarena.sim.kubriko.server.legacy.ObjectKinds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Buildings in play: pixel collisions, being shot down, rammed and burnt, and vehicles shoving each other. */
class StructureGameplayTest {

    private val buildingTileX = 6
    private val row = 2

    @Test
    fun `a building stops a tank until two main-gun hits bring it down`() {
        // Desert picture 8 (@ASEAA, strength 20) with a 100 % goal share.
        val match = start(structures = mapOf(buildingTileX to 8), goal = 100)
        val tank = assertNotNull(match.playerTankForTest(0))
        repeat(400) { match.tick(mapOf(0 to PlayerIntentFrame(forward = true))) }
        assertTrue(tank.positionX < buildingTileX * LEGACY_TILE_SIZE, "the building blocks the tank (x=${tank.positionX})")

        var collapsed = false
        repeat(400) {
            val frame = match.tick(mapOf(0 to PlayerIntentFrame(firePrimary = true)))
            if (frame.events.any { it is GameEvent.Sound && it.kind == com.tankarena.protocol.snapshot.SoundKind.COLLAPSE }) collapsed = true
        }
        assertTrue(collapsed, "two hits of power 10 wear down strength 20")
        assertFalse(match.structures.isStanding(buildingTileX, row))
        assertEquals(100, match.goalProgressGood, "the tile's goal-layer share counts")
        assertTrue(match.snapshot().changedTiles.any { !it.solidStanding }, "the client is told to draw the ruin")
    }

    @Test
    fun `a ruined building no longer blocks`() {
        val match = start(structures = mapOf(buildingTileX to 8))
        match.structures.hit(buildingTileX, row, StructureLayer.SOLID, HitKind.NORMAL, 20)
        val tank = assertNotNull(match.playerTankForTest(0))
        repeat(600) { match.tick(mapOf(0 to PlayerIntentFrame(forward = true))) }
        assertTrue(tank.positionX > (buildingTileX + 1) * LEGACY_TILE_SIZE, "the tank drives through the ruin (x=${tank.positionX})")
    }

    @Test
    fun `ramming a fuel dump wears down its crash strength until it goes up in flames`() {
        // Desert picture 105 (FUELD): crash strength 4; a STANDARD tank rams for |v|/25 + 1.
        val match = start(structures = mapOf(buildingTileX to 105))
        var flames = 0
        repeat(1200) {
            val frame = match.tick(mapOf(0 to PlayerIntentFrame(forward = true)))
            flames += frame.events.count { it is GameEvent.Effect && it.kind == EffectKind.FLAME }
        }
        assertFalse(match.structures.isStanding(buildingTileX, row), "the fuel dump should be rammed to pieces")
        assertTrue(flames > 0, "a fuel dump burns when destroyed")
    }

    @Test
    fun `the flamethrower burns bushes`() {
        // Desert picture 212 (TUN1): flame strength 10, flamer power 1 per flame.
        val match = start(structures = mapOf(4 to 212))
        match.selectWeaponForTest(WEAPON_FLAME)
        repeat(600) { match.tick(mapOf(0 to PlayerIntentFrame(firePrimary = true))) }
        assertFalse(match.structures.isStanding(4, row))
    }

    @Test
    fun `a moving tank shoves a standing one by the legacy mass rule`() {
        val match = start(structures = emptyMap(), secondTankAtTileX = 5)
        val pusher = assertNotNull(match.playerTankForTest(0))
        val pushed = assertNotNull(match.playerTankForTest(1))
        val startX = pushed.positionX
        var pushedMoved = false
        repeat(400) {
            match.tick(mapOf(0 to PlayerIntentFrame(forward = true), 1 to PlayerIntentFrame()))
            if (pushed.velocityX > 0f) pushedMoved = true
        }
        assertTrue(pushedMoved, "the rammed tank picks up speed")
        assertTrue(pushed.positionX > startX, "and is pushed along")
        assertTrue(pushed.positionX - pusher.positionX >= 2 * LegacyVehicles.spec(0).radius)
    }

    private fun start(structures: Map<Int, Int>, goal: Int = 0, secondTankAtTileX: Int? = null): ServerMatchPrototype {
        val width = 12
        val height = 5
        val cells = width * height
        val objects = mutableListOf(
            AuthoredObject(
                id = "p1",
                kind = ObjectKinds.PLAYER_START,
                x = 2 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                y = row * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                properties = mapOf("direction" to "4", "lives" to "3"),
            ),
        )
        if (secondTankAtTileX != null) {
            objects += AuthoredObject(
                id = "p2",
                kind = ObjectKinds.PLAYER_START,
                x = secondTankAtTileX * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                y = row * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                properties = mapOf("direction" to "4", "lives" to "3"),
            )
        }
        val map = CanonicalMapDefinition(
            metadata = MapMetadata(name = "structure-fixture", widthTiles = width, heightTiles = height, missionCode = "TEST"),
            layers = TileLayers(
                base = List(cells) { -1 },
                solid = List(cells) { index -> if (index / width == row) structures[index % width] ?: -1 else -1 },
                top = List(cells) { -1 },
                goalLayer = List(cells) { index -> if (index / width == row && (index % width) in structures) goal else 0 },
                bonusLayer = List(cells) { -1 },
                manTypeLayer = List(cells) { 0 },
                manAmountLayer = List(cells) { 0 },
            ),
            missionText = MissionText(),
            objects = objects,
        )
        val match = ServerMatchPrototype.fromCanonicalMap(map, if (secondTankAtTileX != null) MatchMode.DUAL_VS_COMPUTER else MatchMode.SINGLE)
        match.initialize()
        return match
    }
}

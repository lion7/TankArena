package nl.tankarena.sim

import nl.tankarena.content.LEGACY_TILE_SIZE
import nl.tankarena.content.MapMetadata
import nl.tankarena.content.MissionText
import nl.tankarena.sim.map.ShippedMaps
import nl.tankarena.content.TileLayers
import nl.tankarena.input.PlayerIntentFrame
import nl.tankarena.protocol.snapshot.ProjectileState
import nl.tankarena.sim.legacy.AuthoredObject
import nl.tankarena.sim.legacy.CanonicalMapDefinition
import nl.tankarena.sim.legacy.ObjectKinds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The per-vehicle data from src/data/tanks.c and the behaviour built on it. */
class VehicleSpecTest {

    @Test
    fun `the vehicle table holds the legacy numbers`() {
        assertEquals(23, GeneratedLegacyVehicles.vehicles.size)
        val standard = LegacyVehicles.spec(0)
        assertEquals("STANDARD", standard.name)
        assertEquals(30, standard.armor)
        assertEquals(listOf(-4 to -8, 4 to -8), standard.chainGuns)
        val devastator = LegacyVehicles.spec(1)
        assertEquals(TurretMode.STATIC, devastator.turretMode)
        assertEquals(listOf(-8 to -6, 9 to -6), devastator.mainGuns)
    }

    @Test
    fun `offsets turn with the vehicle like loc_x and loc_y`() {
        assertEquals(0 to -12, rotate(0, 0 to -12))
        assertEquals(12 to 0, rotate(4, 0 to -12))
        assertEquals(0 to 12, rotate(8, 0 to -12))
        assertEquals(-12 to 0, rotate(12, 0 to -12))
        // One step clockwise: loc_x = round(cos(22.5°)·0 − sin(22.5°)·−12) = 5.
        assertEquals(5 to -11, rotate(1, 0 to -12))
    }

    @Test
    fun `the Devastator fires its two main guns in turn`() {
        val scene = assertNotNull(ShippedMaps.load("begin1"))
        val match = Match.fromMap(scene.document, MatchMode.SINGLE)
        match.initialize()
        val player = assertNotNull(match.playerTankForTest(0))
        val origins = mutableListOf<Int>()
        repeat(2) {
            val before = match.snapshot().actors.filterIsInstance<ProjectileState>().map { it.actorId }.toSet()
            val frame = match.tick(mapOf(0 to PlayerIntentFrame(firePrimary = true)))
            frame.actors.filterIsInstance<ProjectileState>().filter { it.actorId !in before }.forEach { origins += it.x - player.positionX }
            repeat(LegacyVehicles.spec(1).refireTicks[WEAPON_MAIN]) { match.tick() }
        }
        assertEquals(2, origins.size)
        assertNotEquals(origins[0], origins[1], "consecutive shots come from different barrels")
        match.dispose()
    }

    @Test
    fun `a chain-gun burst fires one round from each chain gun`() {
        val match = Match.fromCanonicalMap(openField(direction = 0))
        match.initialize()
        match.selectWeaponForTest(WEAPON_CHAIN)
        val frame = match.tick(mapOf(0 to PlayerIntentFrame(firePrimary = true)))
        val rounds = frame.actors.filterIsInstance<ProjectileState>()
        assertEquals(LegacyVehicles.spec(0).chainGuns.size, rounds.size)
        assertEquals(1500 - 5, assertNotNull(match.playerTankForTest(0)).ammoFor(WEAPON_CHAIN))
        match.dispose()
    }

    @Test
    fun `driving into a wall bounces the tank back`() {
        val match = Match.fromCanonicalMap(openField(direction = 4, wallAtTileX = 8))
        match.initialize()
        val tank = assertNotNull(match.playerTankForTest(0))
        var bounced = false
        repeat(600) {
            val before = tank.velocityX
            match.tick(mapOf(0 to PlayerIntentFrame(forward = true)))
            if (before > 20f && tank.velocityX < 0f) bounced = true
        }
        assertTrue(bounced, "legacy static_collide() reverses the velocity by the crash factor")
    }

    @Test
    fun `a player-vs-player tank refills at its base after an opponent dies`() {
        val scene = assertNotNull(ShippedMaps.load("war"))
        val match = Match.fromMap(scene.document, MatchMode.DUAL)
        match.initialize()
        val tank = match.tanksForTest().first { it.playerIndex == 0 }
        repeat(3) {
            match.tick(mapOf(0 to PlayerIntentFrame(firePrimary = true)))
            repeat(LegacyVehicles.spec(tank.tankType).refireTicks[WEAPON_MAIN]) { match.tick() }
        }
        // WAR's pads hand out their own weapons (legacy `pl.weap`).
        val full = assertNotNull(tank.padAmmo)[WEAPON_MAIN]
        assertTrue(tank.ammoFor(WEAPON_MAIN) < full, "shots spend main-gun ammo")

        // Legacy hit_tank() sets `weap.reload` on the other side's tanks; tank_refill() then tops up on the pad.
        match.tanksForTest().first { it.playerIndex == 1 }.queueDestroy()
        repeat(2 * STEP_TICKS) { match.tick() }
        assertEquals(full, tank.ammoFor(WEAPON_MAIN))
        match.dispose()
    }

    private fun openField(direction: Int, wallAtTileX: Int? = null): CanonicalMapDefinition {
        val widthTiles = 12
        val heightTiles = 5
        val cellCount = widthTiles * heightTiles
        val solid = MutableList(cellCount) { -1 }
        if (wallAtTileX != null) for (y in 0 until heightTiles) solid[wallAtTileX + y * widthTiles] = 0
        return CanonicalMapDefinition(
            metadata = MapMetadata(name = "vehicle-fixture", widthTiles = widthTiles, heightTiles = heightTiles, missionCode = "TEST"),
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
                    properties = mapOf("direction" to direction.toString(), "lives" to "3"),
                ),
            ),
        )
    }
}

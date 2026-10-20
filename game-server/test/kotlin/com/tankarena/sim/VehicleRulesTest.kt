package com.tankarena.sim

import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.MapMetadata
import com.tankarena.content.MissionText
import com.tankarena.content.TileLayers
import com.tankarena.input.PlayerIntentFrame
import com.tankarena.sim.legacy.AuthoredObject
import com.tankarena.sim.legacy.CanonicalMapDefinition
import com.tankarena.sim.legacy.ObjectKinds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Vehicle rules of new_position(), hit_tank() and tank_effects() (src/tanks/control.c, tank.c). */
class VehicleRulesTest {
    @Test
    fun `a vehicle leaving the map comes back in on the other side`() {
        val match = start(tileX = 1)
        val tank = match.playerTankForTest(0)!!
        tank.setVelocityForTest(-300f, 0f)
        var wrapped = false
        repeat(20) {
            match.stepForTest()
            if (tank.positionX > match.worldWidth - LEGACY_TILE_SIZE) wrapped = true
        }
        assertTrue(wrapped, "the tank should reappear at the east edge")
    }

    @Test
    fun `computer vehicles burn no fuel`() {
        val match = start(tileX = 5, extraTank = true)
        val computer = match.tanksForTest().single { it.computer }
        val before = computer.fuel
        computer.setVelocityForTest(100f, 0f)
        match.stepForTest()
        assertEquals(before, computer.fuel)
        val player = match.playerTankForTest(0)!!
        val playerFuel = player.fuel
        player.setVelocityForTest(100f, 0f)
        match.stepForTest()
        assertTrue(player.fuel < playerFuel)
    }

    @Test
    fun `the start shield does not protect below ground`() {
        val match = start(tileX = 5)
        val tank = match.playerTankForTest(0)!!
        tank.grantInvulnerability(START_SHIELD_HOLD_TICKS)
        val armor = tank.armor
        match.queueDamageForTest(0, 5)
        match.stepForTest()
        assertEquals(armor, tank.armor, "on the ground the shield holds")
        tank.setHeightForTest(0)
        match.queueDamageForTest(0, 5)
        match.stepForTest(mapOf(0 to PlayerIntentFrame()))
        assertEquals(armor - 5, tank.armor, "below ground it does not")
    }

    private fun start(tileX: Int, extraTank: Boolean = false): Match {
        val width = 12
        val height = 5
        val cells = width * height
        val objects = buildList {
            add(tank("p1", ObjectKinds.PLAYER_START, tileX, mapOf("direction" to "4", "lives" to "3")))
            if (extraTank) add(tank("ai", ObjectKinds.AI_TANK, tileX + 4, mapOf("activationRadius" to "1")))
        }
        val match = Match.fromCanonicalMap(
            CanonicalMapDefinition(
                metadata = MapMetadata(name = "rules", widthTiles = width, heightTiles = height, missionCode = "TEST"),
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
                objects = objects,
            ),
        )
        match.initialize()
        return match
    }

    private fun tank(id: String, kind: String, tileX: Int, properties: Map<String, String>) = AuthoredObject(
        id = id,
        kind = kind,
        x = tileX * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
        y = 2 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
        properties = properties,
    )
}

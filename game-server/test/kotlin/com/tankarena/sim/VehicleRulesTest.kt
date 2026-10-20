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
    fun `computer tanks refire after their own fire delay`() {
        val match = start(tileX = 5, extraTank = true, aiFireDelay = 250)
        val computer = match.tanksForTest().single { it.computer }
        computer.enforce(WEAPON_MAIN, fire = true, keep = true)
        assertEquals(250, computer.primaryCooldownTicks)
        val defaultDelay = start(tileX = 5, extraTank = true).tanksForTest().single { it.computer }
        assertEquals(defaultDelay.spec.refireTicks[defaultDelay.spec.initialWeapon], defaultDelay.aiFireDelay)
    }

    @Test
    fun `a wreck burns for 3000 ticks, then blows up and is gone`() {
        // A second enemy keeps the mission going: hit_wreck() does nothing once it is decided.
        val match = start(tileX = 5, extraTank = true, bystander = true)
        val computer = match.tanksForTest().first { it.computer }
        computer.queueDestroy()
        repeat((100 + 30) / STEP_TICKS) { match.stepForTest() }
        assertTrue(computer.isWreck)
        repeat((WRECK_LIFE_TICKS - 60) / STEP_TICKS) { match.stepForTest() }
        assertTrue(computer in match.tanksForTest(), "still burning")
        repeat(30) { match.stepForTest() }
        assertTrue(computer !in match.tanksForTest(), "blown up")
    }

    @Test
    fun `-GOD keeps good vehicles whole but for HT_DESTROY, -NOFAIL keeps the mission going`() {
        val match = start(tileX = 5, cheats = Cheats(god = true, noFail = true))
        val tank = match.playerTankForTest(0)!!
        val armor = tank.armor
        match.queueDamageForTest(0, 20)
        match.stepForTest()
        assertEquals(armor, tank.armor)
        match.modifyGoalCounter(-100)
        assertEquals(0, match.goalBad)
        tank.queueDestroy()
        match.stepForTest()
        assertEquals(0, tank.armor, "falling or crashing still kills")
        assertEquals(Cheats(god = true, loadsOfWeapons = true), Cheats.fromArgs(arrayOf("-GOD", "--loadsofweapons", "-bogus")))
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

    private fun start(tileX: Int, extraTank: Boolean = false, aiFireDelay: Int? = null, bystander: Boolean = false, cheats: Cheats = Cheats()): Match {
        val width = 12
        val height = 5
        val cells = width * height
        val objects = buildList {
            add(tank("p1", ObjectKinds.PLAYER_START, tileX, mapOf("direction" to "4", "lives" to "3")))
            if (extraTank) {
                val delay = aiFireDelay?.let { mapOf("fireDelay" to it.toString()) }.orEmpty()
                add(tank("ai", ObjectKinds.AI_TANK, tileX + 4, mapOf("activationRadius" to "1") + delay))
            }
            if (bystander) add(tank("ai2", ObjectKinds.AI_TANK, tileX + 6, mapOf("activationRadius" to "1")))
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
            cheats = cheats,
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

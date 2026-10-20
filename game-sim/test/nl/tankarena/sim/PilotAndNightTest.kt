package nl.tankarena.sim

import nl.tankarena.content.LEGACY_TILE_SIZE
import nl.tankarena.content.MapMetadata
import nl.tankarena.content.MissionText
import nl.tankarena.content.TileLayers
import nl.tankarena.input.PlayerIntentFrame
import nl.tankarena.protocol.snapshot.GameEvent
import nl.tankarena.protocol.snapshot.SoundKind
import nl.tankarena.protocol.snapshot.TankState
import nl.tankarena.sim.legacy.AuthoredObject
import nl.tankarena.sim.legacy.CanonicalMapDefinition
import nl.tankarena.sim.legacy.ObjectKinds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The pilot's messages (src/world/status.c) and the night maps' lights (src/game/night.c). */
class PilotAndNightTest {

    private val row = 2

    @Test
    fun `switching weapons tells the pilot what is selected, for 29 counts of tinit(10)`() {
        val match = start(ammo = listOf(10, 100))
        match.stepForTest(mapOf(0 to PlayerIntentFrame(cycleWeaponRight = true)))
        var hud = match.buildPlayerViews().single().hud
        assertEquals("CHAIN GUN" to "SELECTED!", hud.messageLine1 to hud.messageLine2)
        assertEquals(MESSAGE_STEPS, hud.messageSteps)
        assertEquals(0, hud.vehicleType)
        // Legacy msg_control(): the first count comes on the next step, then one every tinit(10),
        // which lasts 12 ticks (4 steps) on legacy steps.
        val steps = 1 + (MESSAGE_STEPS - 1) * stepRounded(MESSAGE_STEP_TICKS) / STEP_TICKS
        repeat(steps - 1) { match.stepForTest() }
        assertTrue(match.buildPlayerViews().single().hud.messageSteps > 0, "still showing")
        match.stepForTest()
        hud = match.buildPlayerViews().single().hud
        assertEquals(0, hud.messageSteps, "the message is gone")
    }

    @Test
    fun `a rocket launched at a vehicle warns its pilot`() {
        val match = start(ammo = listOf(0, 0, 0, 0, 3), enemyTileX = 6, mode = MatchMode.DUAL_VS_COMPUTER, secondPlayerTileX = 6)
        match.selectWeaponForTest(WEAPON_ROCKET)
        // Hold fire and steer the marker east until it locks onto the other player's tank, then let go.
        var incoming = false
        var ticks = 0
        // The marker starts on the shooter, which is a target too (nearest_target()); steer it off first.
        val shooter = match.playerTankForTest(0)!!
        do {
            match.tick(mapOf(0 to PlayerIntentFrame(firePrimary = true, turnRight = true)))
            val marker = match.buildPlayerViews().single { it.playerId == 0 }.lockMarker
            val offShooter = shooter.lockX - shooter.positionX > ROCKET_TARGET_RADIUS_PX
        } while ((!offShooter || marker?.state != nl.tankarena.protocol.snapshot.LockMarkerState.ON_TARGET) && ++ticks < 300)
        repeat(3) {
            val frame = match.tick()
            incoming = incoming || frame.events.any { it is GameEvent.Sound && it.kind == SoundKind.INCOMING }
        }
        assertTrue(incoming, "the target hears the rocket coming")
        val warned = match.buildPlayerViews().single { it.playerId == 1 }.hud
        assertEquals("INCOMING" to "MISSILE!", warned.messageLine1 to warned.messageLine2)
    }

    @Test
    fun `the player hears when another drivable tank is hit`() {
        val match = start(friendTileX = 8)
        val friend = match.tanksForTest().single { it.controllerId == 2 }
        friend.queueDamage(5)
        repeat(2) { match.tick() }
        val hud = match.buildPlayerViews().single().hud
        assertEquals("TANK 2 HAS" to "BEEN HIT!", hud.messageLine1 to hud.messageLine2)
    }

    @Test
    fun `on night maps vehicles cast headlights that the light switch turns off`() {
        val match = start(night = true)
        val tank = match.playerTankForTest(0)!!
        match.stepForTest()
        val cone = match.snapshot().actors.filterIsInstance<TankState>().single().lights.single()
        // STANDARD: one large light at (-2, 1) on the turret, which sits at (0, -2); facing east.
        assertEquals(0, cone.size)
        assertEquals(4, cone.direction)
        assertEquals(tank.positionX + 2 - 1 to tank.positionY - 2, cone.x to cone.y)

        match.selectWeaponForTest(WEAPON_LIGHT)
        match.stepForTest(mapOf(0 to PlayerIntentFrame(firePrimary = true)))
        assertTrue(match.snapshot().actors.filterIsInstance<TankState>().single().lights.isEmpty(), "the light is off")
        assertTrue(match.buildPlayerViews().single().hud.currentWeapon == WEAPON_LIGHT)
    }

    @Test
    fun `there is no light switch and no light by day`() {
        val match = start()
        match.tick()
        assertTrue(match.snapshot().actors.filterIsInstance<TankState>().single().lights.isEmpty())
        repeat(3) { match.tick(mapOf(0 to PlayerIntentFrame(cycleWeaponRight = true))); repeat(16) { match.tick() } }
        assertTrue(match.playerTankForTest(0)!!.currentWeapon != WEAPON_LIGHT)
    }

    private fun centre(tile: Int) = tile * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2

    private fun start(
        ammo: List<Int>? = null,
        night: Boolean = false,
        enemyTileX: Int? = null,
        friendTileX: Int? = null,
        mode: MatchMode = MatchMode.SINGLE,
        secondPlayerTileX: Int? = null,
    ): Match {
        val width = 16
        val height = 5
        val cells = width * height
        val objects = mutableListOf(
            AuthoredObject(
                id = "p1",
                kind = ObjectKinds.PLAYER_START,
                x = centre(2),
                y = centre(row),
                properties = buildMap {
                    put("direction", "4")
                    put("lives", "1")
                    ammo?.let { put("ammo", it.joinToString(",")) }
                },
            ),
        )
        if (secondPlayerTileX != null) {
            objects += AuthoredObject(id = "p2", kind = ObjectKinds.PLAYER_START, x = centre(secondPlayerTileX), y = centre(row), properties = mapOf("direction" to "12", "lives" to "1"))
        } else if (enemyTileX != null) {
            objects += AuthoredObject(id = "e", kind = ObjectKinds.AI_TANK, x = centre(enemyTileX), y = centre(row), properties = mapOf("activationRadius" to "1"))
        }
        if (friendTileX != null) {
            objects += AuthoredObject(
                id = "friend",
                kind = ObjectKinds.AI_TANK,
                x = centre(friendTileX),
                y = centre(row),
                properties = mapOf("computer" to "0", "controllerId" to "2"),
            )
        }
        val map = CanonicalMapDefinition(
            metadata = MapMetadata(name = "night-fixture", widthTiles = width, heightTiles = height, missionCode = "TEST", night = night),
            layers = TileLayers(
                base = List(cells) { -1 },
                solid = List(cells) { -1 },
                top = List(cells) { -1 },
                goalLayer = List(cells) { 0 },
                bonusLayer = List(cells) { 255 },
                manTypeLayer = List(cells) { 0 },
                manAmountLayer = List(cells) { 0 },
            ),
            missionText = MissionText(),
            objects = objects,
        )
        val match = Match.fromCanonicalMap(map, mode)
        match.initialize()
        return match
    }
}

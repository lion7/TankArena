package nl.tankarena.sim

import nl.tankarena.sim.map.ShippedMaps
import nl.tankarena.input.PlayerIntentFrame
import nl.tankarena.protocol.snapshot.GameEvent
import nl.tankarena.content.LEGACY_TILE_SIZE
import nl.tankarena.protocol.snapshot.PitShape
import nl.tankarena.protocol.snapshot.TerrainMaterial
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Exercises the production bootstrap: committed scene JSON + sidecar loaded from the
 * classpath through [ShippedMaps], exactly as the desktop client does.
 */
class ShippedSceneMatchTest {

    private var prototype: Match? = null

    @AfterTest
    fun tearDown() {
        prototype?.dispose()
        prototype = null
    }

    private fun start(baseName: String, mode: MatchMode): Match {
        val scene = assertNotNull(ShippedMaps.load(baseName), "missing shipped scene $baseName")
        return Match.fromMap(scene.document, mode).also {
            it.initialize()
            prototype = it
        }
    }

    @Test
    fun `every shipped map is listed in the index`() {
        val names = ShippedMaps.baseNames()
        assertEquals(69, names.size, "the maps the original game shipped with")
        assertTrue("begin1" in names)
    }

    @Test
    fun `BEGIN1 gives player 0 the map tank with id 1 and leaves four computer enemies`() {
        val match = start("begin1", MatchMode.SINGLE)
        val player = assertNotNull(match.playerTankForTest(0))
        assertEquals(1, player.controllerId)
        assertEquals(1, player.tankType, "BEGIN1's player drives a DEVASTATOR")
        assertEquals(0, player.team)
        val enemies = match.tanksForTest().filter { it.team == 1 }
        assertEquals(4, enemies.size)
        assertTrue(enemies.all { it.computer && it.playerIndex < 0 && it.goalContribution == 25 })
    }

    @Test
    fun `production bootstrap builds the terrain grid and solid layer from the sidecar`() {
        val match = start("begin1", MatchMode.SINGLE)
        assertTrue(match.solidTileCountForTest() > 0, "A* and line-of-sight need the solid layer")
        val nonNormal = TerrainMaterial.entries.filter { it != TerrainMaterial.NORMAL }.sumOf { match.terrainCountForTest(it) }
        assertTrue(nonNormal > 0, "terrain effects need a populated terrain grid")
    }

    @Test
    fun `BEGIN2's pit swallows a tank that drives into its centre`() {
        val match = start("begin2", MatchMode.SINGLE)
        val (tileX, tileY) = assertNotNull(
            match.terrainTileForTest { it.pitShape == PitShape.CENTER },
            "BEGIN2 has a big pit",
        )
        val player = assertNotNull(match.playerTankForTest(0))
        repeat(START_SHIELD_TICKS) { match.tick() }
        player.teleportTo(tileX * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2, tileY * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2)
        // Legacy new_velocity(): the tank first sinks (h 1 → -4 under 100 px/s² gravity, ~0.3 s) ...
        repeat(10) { match.tick() }
        assertTrue(player.falling && player.armor > 0, "the tank sinks before it is destroyed")
        // ... and is destroyed at the bottom, through the start shield (HT_DESTROY).
        val destroyed = (0 until 60).any { _ -> match.tick().events.any { it is GameEvent.TankDestroyed } }
        assertTrue(destroyed, "a tank in the middle of a pit falls in")
        assertEquals(-4, player.height)
        // Its time to die runs at the bottom too, so a player would get their next vehicle.
        repeat(110) { match.tick() }
        assertTrue(player.doneDying, "done dying at the bottom of the pit")
    }

    @Test
    fun `a pit edge only swallows a tank past the rim`() {
        val match = start("begin2", MatchMode.SINGLE)
        // BEGIN2's ring pit: pick a top-edge tile with road above it, clear of walls.
        val (tileX, tileY) = assertNotNull(
            match.terrainTileForTest { it.pitShape == PitShape.TOP },
            "BEGIN2's pit has a top edge",
        )
        val player = assertNotNull(match.playerTankForTest(0))
        val centreX = tileX * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2
        // Legacy in_pit(): a top-edge tile only kills more than 8 px into the tile.
        player.teleportTo(centreX, tileY * LEGACY_TILE_SIZE + 4)
        repeat(5) { match.tick() }
        assertTrue(player.armor > 0, "on the rim of the pit the tank is safe")
        player.teleportTo(centreX, tileY * LEGACY_TILE_SIZE + 20)
        repeat(60) { match.tick() }
        assertEquals(0, player.armor, "past the rim it falls in")
    }

    @Test
    fun `BEGIN1's Devastator fires along its hull and ignores the aim keys`() {
        val match = start("begin1", MatchMode.SINGLE)
        val player = assertNotNull(match.playerTankForTest(0))
        assertEquals(TurretMode.STATIC, player.turretMode)
        repeat(30) { match.tick(mapOf(0 to PlayerIntentFrame(aimLeft = true))) }
        assertEquals(player.bodyDirection, player.turretDirection, "a static gun stays on the hull")
        repeat(30) { match.tick(mapOf(0 to PlayerIntentFrame(turnRight = true))) }
        assertEquals(player.bodyDirection, player.turretDirection, "and turns with it")
        assertEquals(player.bodyDirection, player.aimDirection)
    }

    @Test
    fun `a mortar shell lands about 120 px away and explodes`() {
        // BEGIN3 ("Now for the bigger weapons!") gives the Devastator 50 mortar shells.
        val match = start("begin3", MatchMode.SINGLE)
        val player = assertNotNull(match.playerTankForTest(0))
        match.selectWeaponForTest(WEAPON_MORTAR)
        val startX = player.positionX
        val startY = player.positionY
        match.tick(mapOf(0 to PlayerIntentFrame(firePrimary = true)))
        var blast: GameEvent.Explosion? = null
        for (i in 0 until 300) {
            blast = match.tick().events.filterIsInstance<GameEvent.Explosion>().firstOrNull() ?: continue
            break
        }
        val explosion = assertNotNull(blast, "the shell should explode")
        val distance = kotlin.math.hypot((explosion.x - startX).toDouble(), (explosion.y - startY).toDouble())
        assertTrue(distance in 110.0..150.0, "legacy mortar range is 120 px from the barrel, got $distance")
    }

    @Test
    fun `BEGIN1's Devastator carries the main gun and the flamethrower and nothing else`() {
        val match = start("begin1", MatchMode.SINGLE)
        val player = assertNotNull(match.playerTankForTest(0))
        assertEquals(WEAPON_MAIN, player.currentWeapon)
        assertEquals(50, player.ammoFor(WEAPON_MAIN))
        assertEquals(30, player.armor)
        // Cycling only ever lands on the main gun and the flamethrower.
        val seen = mutableSetOf(player.currentWeapon)
        repeat(4) {
            match.tick(mapOf(0 to PlayerIntentFrame(cycleWeaponRight = true)))
            repeat(16) { match.tick() }
            seen += player.currentWeapon
        }
        assertEquals(setOf(WEAPON_MAIN, WEAPON_FLAME), seen)
    }

    @Test
    fun `BEGIN2 starts the player on the flamethrower`() {
        val match = start("begin2", MatchMode.SINGLE)
        val player = assertNotNull(match.playerTankForTest(0))
        assertEquals(WEAPON_FLAME, player.currentWeapon)
        assertTrue(player.ammoCounts.all { it == 0 })
    }

    @Test
    fun `a tank tops out near the legacy speed of about 1 px per tick`() {
        val match = start("begin2", MatchMode.SINGLE)
        val player = assertNotNull(match.playerTankForTest(0))
        // STANDARD: thrust acc/mass = 100 px/s², friction (1000·v + 2000)/1000 → top speed 98 px/s.
        // BEGIN2's player drives into the pit ring, so take the peak speed on the way.
        var speed = 0f
        repeat(400) {
            match.tick(mapOf(0 to PlayerIntentFrame(forward = true)))
            speed = maxOf(speed, kotlin.math.hypot(player.velocityX, player.velocityY))
        }
        assertTrue(speed in 50f..100f, "STANDARD should approach 98 px/s and never exceed it, got $speed")
    }

    @Test
    fun `the flamethrower burns fuel and sets a nearby car on fire`() {
        val match = start("begin1", MatchMode.SINGLE)
        val player = assertNotNull(match.playerTankForTest(0))
        val car = match.tanksForTest().first { it.computer }
        repeat(START_SHIELD_TICKS) { match.tick() }
        match.selectWeaponForTest(WEAPON_FLAME)
        // Park the Devastator right next to the car, facing it.
        player.teleportTo(car.positionX - 40, car.positionY)
        while (player.bodyDirection != 4) {
            match.tick(mapOf(0 to PlayerIntentFrame(turnRight = true)))
        }
        player.teleportTo(car.positionX - 40, car.positionY)
        val fuelBefore = player.fuel
        val carArmor = car.armor
        // Open floor inside BEGIN1's road ring, clear of walls.
        val (carX, carY) = 8 * LEGACY_TILE_SIZE to 3 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2
        repeat(300) {
            car.teleportTo(carX, carY)
            player.teleportTo(carX - 40, carY)
            match.tick(mapOf(0 to PlayerIntentFrame(firePrimary = true)))
        }
        assertTrue(player.fuel <= fuelBefore - 40, "each flame burst costs 40 fuel")
        assertTrue(car.armor < carArmor, "the flames should hurt the car")
    }

    @Test
    fun `destroying BEGIN1's four enemies fills the goal counter and wins`() {
        val match = start("begin1", MatchMode.SINGLE)
        var result: GameEvent.MissionResult? = null
        for (i in 0 until 50) {
            match.queueDamageToEnemyForTest(999)
            val frame = match.tick()
            result = frame.events.filterIsInstance<GameEvent.MissionResult>().firstOrNull() ?: result
            if (match.mission != Match.MissionStatus.IN_PROGRESS) break
        }
        assertEquals(Match.MissionStatus.WON, match.mission)
        assertEquals(100, match.goalProgressGood)
        assertEquals("GOALS_CAPTURED", result?.reason)
        assertEquals(4, result?.kills)
    }

    @Test
    fun `losing BEGIN1's only player tank loses the mission`() {
        val match = start("begin1", MatchMode.SINGLE)
        // Legacy make_tank(): the start shield protects the player for its first 75 ticks.
        repeat(START_SHIELD_TICKS + 1) { match.tick() }
        match.queueDamageForTest(0, 999)
        repeat(5) { match.tick() }
        assertEquals(Match.MissionStatus.LOST, match.mission)
        assertEquals(100, match.goalProgressBad)
    }

    @Test
    fun `AIR STRIKE is not won by the enemy shooting at the player's own buildings`() {
        // Legacy `mapm.g` is a signed char: the player's buildings carry negative goals.
        val match = start("air", MatchMode.SINGLE)
        repeat(3000) { match.tick() }
        assertEquals(Match.MissionStatus.IN_PROGRESS, match.mission)
        assertEquals(0, match.goalProgressGood)
        // The turrets by the base shoot only evil vehicles (`tur.shootat` 2), so they are on the
        // player's side and do not fire across it.
        val friendly = match.snapshot().actors.filterIsInstance<nl.tankarena.protocol.snapshot.TurretState>()
            .count { it.team == nl.tankarena.protocol.Team.PLAYER }
        assertEquals(6, friendly)
    }

    @Test
    fun `BEGIN1 plays for several seconds without the idle player dying to terrain`() {
        val match = start("begin1", MatchMode.SINGLE)
        repeat(100) { match.tick(mapOf(0 to PlayerIntentFrame())) }
        val player = assertNotNull(match.playerTankForTest(0))
        assertTrue(player.armor > 0)
    }

    @Test
    fun `co-operative play gives player 1 id 1 and player 2 id 2`() {
        val match = start("nuke2", MatchMode.DUAL_VS_COMPUTER)
        assertEquals(1, match.playerTankForTest(0)?.controllerId)
        assertEquals(2, match.playerTankForTest(1)?.controllerId)
    }

    @Test
    fun `single player drives every tank sharing id 1 at once`() {
        val match = start("bay", MatchMode.SINGLE)
        val squadron = match.tanksForTest().filter { it.controllerId == 1 && !it.computer }
        assertTrue(squadron.size > 1)
        assertTrue(squadron.all { it.playerIndex == 0 })
        assertTrue(match.tanksForTest().filter { it.controllerId == 2 }.all { it.playerIndex < 0 })
    }
}

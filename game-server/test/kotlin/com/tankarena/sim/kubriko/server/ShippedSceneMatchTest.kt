package com.tankarena.sim.kubriko.server

import com.tankarena.content.ShippedScenes
import com.tankarena.input.PlayerIntentFrame
import com.tankarena.protocol.snapshot.GameEvent
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.protocol.snapshot.PitShape
import com.tankarena.protocol.snapshot.TerrainMaterial
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Exercises the production bootstrap: committed scene JSON + sidecar loaded from the
 * classpath through [ShippedScenes], exactly as the desktop client does.
 */
class ShippedSceneMatchTest {

    private var prototype: ServerMatchPrototype? = null

    @AfterTest
    fun tearDown() {
        prototype?.dispose()
        prototype = null
    }

    private fun start(baseName: String, mode: MatchMode): ServerMatchPrototype {
        val scene = assertNotNull(ShippedScenes.load(baseName), "missing shipped scene $baseName")
        return ServerMatchPrototype.fromSceneJson(scene.sceneJson, scene.sidecar, mode).also {
            it.initialize()
            prototype = it
        }
    }

    @Test
    fun `every shipped scene is listed in the index`() {
        val names = ShippedScenes.baseNames()
        assertTrue(names.size >= 121, "expected the full shipped set, got ${names.size}")
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
        player.teleportTo(tileX * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2, tileY * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2)
        val destroyed = (0 until 5).any { _ -> match.tick().events.any { it is GameEvent.TankDestroyed } }
        assertTrue(destroyed, "a tank in the middle of a pit falls in")
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
        repeat(5) { match.tick() }
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
        val match = start("begin1", MatchMode.SINGLE)
        val player = assertNotNull(match.playerTankForTest(0))
        while (player.currentWeapon != WEAPON_MORTAR) {
            match.tick(mapOf(0 to PlayerIntentFrame(cycleWeaponRight = true)))
            repeat(15) { match.tick() }
        }
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
    fun `destroying BEGIN1's four enemies fills the goal counter and wins`() {
        val match = start("begin1", MatchMode.SINGLE)
        var result: GameEvent.MissionResult? = null
        for (i in 0 until 50) {
            match.queueDamageToEnemyForTest(999)
            val frame = match.tick()
            result = frame.events.filterIsInstance<GameEvent.MissionResult>().firstOrNull() ?: result
            if (match.mission != ServerMatchPrototype.MissionStatus.IN_PROGRESS) break
        }
        assertEquals(ServerMatchPrototype.MissionStatus.WON, match.mission)
        assertEquals(100, match.goalProgressGood)
        assertEquals("GOALS_CAPTURED", result?.reason)
        assertEquals(4, result?.kills)
    }

    @Test
    fun `losing BEGIN1's only player tank loses the mission`() {
        val match = start("begin1", MatchMode.SINGLE)
        match.queueDamageForTest(0, 999)
        repeat(5) { match.tick() }
        assertEquals(ServerMatchPrototype.MissionStatus.LOST, match.mission)
        assertEquals(100, match.goalProgressBad)
    }

    @Test
    fun `BEGIN1 plays for several seconds without the idle player dying to terrain`() {
        val match = start("begin1", MatchMode.SINGLE)
        repeat(100) { match.tick(mapOf(0 to PlayerIntentFrame())) }
        val player = assertNotNull(match.playerTankForTest(0))
        assertTrue(player.armor > 0)
    }

    @Test
    fun `player-vs-player spawns every start pad of the chosen vehicle kind for each player`() {
        val match = start("3_vs_3", MatchMode.DUAL)
        val tanks = match.tanksForTest()
        assertEquals(3, tanks.count { it.playerIndex == 0 })
        assertEquals(3, tanks.count { it.playerIndex == 1 })
        assertTrue(tanks.none { !it.computer && it.startPadType < 0 }, "human map tanks are removed in DUAL")
    }

    @Test
    fun `player-vs-player drives the whole group with one player's input`() {
        val match = start("3_vs_3", MatchMode.DUAL)
        val group = match.tanksForTest().filter { it.playerIndex == 0 }
        val before = group.map { it.bodyDirection }
        repeat(10) { match.tick(mapOf(0 to PlayerIntentFrame(turnRight = true))) }
        assertTrue(group.zip(before).all { (tank, dir) -> tank.bodyDirection != dir }, "every tank in the group turns")
    }

    @Test
    fun `co-operative play gives player 1 id 1 and player 2 id 2`() {
        val match = start("base2", MatchMode.DUAL_VS_COMPUTER)
        assertEquals(1, match.playerTankForTest(0)?.controllerId)
        assertEquals(2, match.playerTankForTest(1)?.controllerId)
    }

    @Test
    fun `single player drives every tank sharing id 1 at once`() {
        val match = start("planes2", MatchMode.SINGLE)
        val squadron = match.tanksForTest().filter { it.controllerId == 1 && !it.computer }
        assertTrue(squadron.size > 1)
        assertTrue(squadron.all { it.playerIndex == 0 })
        assertTrue(match.tanksForTest().filter { it.controllerId == 2 }.all { it.playerIndex < 0 })
    }
}

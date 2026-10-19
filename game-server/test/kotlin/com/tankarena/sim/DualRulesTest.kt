package com.tankarena.sim

import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.MapMetadata
import com.tankarena.content.MissionText
import com.tankarena.content.PictureCatalog
import com.tankarena.content.TankArenaWorld
import com.tankarena.content.TileLayers
import com.tankarena.input.PlayerIntentFrame
import com.tankarena.protocol.snapshot.GameEvent
import com.tankarena.sim.legacy.AuthoredObject
import com.tankarena.sim.legacy.CanonicalMapDefinition
import com.tankarena.sim.legacy.ObjectKinds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Player vs player rounds: src/objects/player.c, flag.c and dual.c. */
class DualRulesTest {

    @Test
    fun `each player gets the vehicle they picked, from the pads for that kind of vehicle`() {
        val match = start(
            pads = listOf(pad(0, tileX = 2), pad(1, tileX = 12), pad(2, tileX = 3), pad(3, tileX = 13)),
            dual = DualSetup(tankTypes = listOf(CHOPPER, STANDARD)),
        )
        val p1 = match.tanksForTest().filter { it.playerIndex == 0 }
        val p2 = match.tanksForTest().filter { it.playerIndex == 1 }
        assertEquals(listOf(CHOPPER), p1.map { it.tankType }, "player 1 flies from the helicopter pad")
        assertEquals(listOf(STANDARD), p2.map { it.tankType }, "player 2 drives from the tank pad")
        assertEquals(3 * T + T / 2, p1.single().spawnX)
    }

    @Test
    fun `every start pad of the chosen kind spawns a vehicle, and the whole group follows its player`() {
        // Like the 3 VS 3 map that was not in the original release: three tank pads per player.
        val match = start(pads = listOf(2, 5, 8).flatMap { x -> listOf(pad(0, tileX = x), pad(1, tileX = x, tileY = 4)) })
        val tanks = match.tanksForTest()
        assertEquals(3, tanks.count { it.playerIndex == 0 })
        assertEquals(3, tanks.count { it.playerIndex == 1 })
        val group = tanks.filter { it.playerIndex == 0 }
        val before = group.map { it.bodyDirection }
        repeat(10) { match.tick(mapOf(0 to PlayerIntentFrame(turnRight = true))) }
        assertTrue(group.zip(before).all { (tank, dir) -> tank.bodyDirection != dir }, "every tank in the group turns")
    }

    @Test
    fun `a pad with its own weapons arms every vehicle it spawns with them`() {
        val loadout = listOf(7, 300, 0, 2, 0, 0, 0, 0, 0, 1, 0, 0)
        val match = start(pads = listOf(pad(0, tileX = 2, ammo = loadout), pad(1, tileX = 12)))
        val p1 = match.tanksForTest().single { it.playerIndex == 0 }
        val p2 = match.tanksForTest().single { it.playerIndex == 1 }
        assertEquals(loadout, p1.ammoCounts)
        assertEquals(LegacyVehicles.spec(p2.tankType).ammo.take(12), p2.ammoCounts, "a pad without weapons gives the vehicle's own")
        // The next vehicle from the pad gets the pad's weapons again.
        p1.queueDestroy()
        repeat(310) { match.tick() }
        assertTrue(p1.armor > 0)
        assertEquals(loadout, p1.ammoCounts)
    }

    @Test
    fun `the round starts with its number and the score`() {
        val match = start(pads = listOf(pad(0, tileX = 2), pad(1, tileX = 12)), dual = DualSetup(round = 3, wins = listOf(1, 0)))
        val views = match.buildPlayerViews()
        assertEquals("ROUND 3" to "1 : 0", views.single { it.playerId == 0 }.hud.let { it.messageLine1 to it.messageLine2 })
        assertEquals("ROUND 3" to "0 : 1", views.single { it.playerId == 1 }.hud.let { it.messageLine1 to it.messageLine2 })
    }

    @Test
    fun `losing the fourth vehicle loses the round`() {
        val match = start(pads = listOf(pad(0, tileX = 2), pad(1, tileX = 12)))
        val p1 = match.tanksForTest().single { it.playerIndex == 0 }
        var won: Int? = null
        repeat(4) {
            p1.queueDestroy()
            repeat(310) {
                val frame = match.tick()
                frame.events.filterIsInstance<GameEvent.RoundWon>().firstOrNull()?.let { won = it.playerId }
            }
        }
        assertEquals(1, won, "player 2 wins once player 1 has lost four vehicles")
        assertTrue(match.buildPlayerViews().single { it.playerId == 1 }.hud.roundWon)
        assertTrue(match.buildPlayerViews().single { it.playerId == 0 }.hud.roundLost)
    }

    @Test
    fun `bringing the other player's flag home wins a flag hunt`() {
        // Player 1's pad at tile 2, player 2's flag at tile 5: drive east to it, then back.
        val match = start(
            pads = listOf(pad(0, tileX = 2, direction = 1), pad(1, tileX = 14)),
            flags = listOf(flag(type = 1, tileX = 5)),
        )
        val p1 = match.tanksForTest().single { it.playerIndex == 0 }
        var won: Int? = null
        var ticks = 0
        while (p1.flagsPicked == 0 && ticks++ < 1000) match.tick(mapOf(0 to PlayerIntentFrame(forward = true)))
        assertEquals(1, p1.flagsPicked, "picked up player 2's flag")
        assertEquals("GOT A FLAG!", match.buildPlayerViews().single { it.playerId == 0 }.hud.messageLine1)
        // Stop, turn round (west is 12) and drive home.
        repeat(400) { match.tick() }
        while (p1.bodyDirection != 12 && ticks++ < 2000) match.tick(mapOf(0 to PlayerIntentFrame(turnLeft = true)))
        while (won == null && ticks++ < 4000) {
            val frame = match.tick(mapOf(0 to PlayerIntentFrame(forward = p1.positionX - p1.spawnX > 20)))
            frame.events.filterIsInstance<GameEvent.RoundWon>().firstOrNull()?.let { won = it.playerId }
        }
        assertEquals(0, won, "ended at x=${p1.positionX} (pad ${p1.spawnX}), y=${p1.positionY} (pad ${p1.spawnY}), dir ${p1.bodyDirection}, ticks $ticks, armor ${p1.armor}")
        assertTrue(match.buildPlayerViews().single { it.playerId == 0 }.hud.flagHunt)
    }

    @Test
    fun `of several flags per player only one stays in play`() {
        val match = start(
            pads = listOf(pad(0, tileX = 2), pad(1, tileX = 14)),
            flags = listOf(flag(type = 0, tileX = 6), flag(type = 0, tileX = 8), flag(type = 1, tileX = 10)),
        )
        match.tick()
        val flags = match.snapshot().actors.filterIsInstance<com.tankarena.protocol.snapshot.FlagState>()
        assertEquals(listOf(0, 1), flags.map { it.flagType }.sorted())
    }

    @Test
    fun `a race is won by completing the laps through every checkpoint`() {
        val world = TankArenaWorld.entries.first { pictureOfType(it, "START") != null && pictureOfType(it, "CHECK_P1_RIGHT") != null }
        // Row 2: start line at tile 2, player 1's checkpoint at tile 8, open track up to tile 14.
        val base = mapOf(2 to pictureOfType(world, "START")!!, 8 to pictureOfType(world, "CHECK_P1_RIGHT")!!)
        val match = start(
            pads = listOf(pad(6, tileX = 2, direction = 1), pad(7, tileX = 2, tileY = 4, direction = 1)),
            dual = DualSetup(raceLaps = 1),
            base = base,
            world = world,
        )
        val car = match.tanksForTest().single { it.playerIndex == 0 }
        assertEquals(DualSetup.RACE_TANK, car.tankType)
        // Out past the checkpoint, turn round and back over the start line.
        var won: Int? = null
        var ticks = 0
        while (car.positionX < 9 * T && ticks++ < 2000) match.tick(mapOf(0 to PlayerIntentFrame(forward = true)))
        // Stop first, so the turn does not carry the car off the one-tile track.
        while (car.speed > 1f && ticks++ < 3000) match.tick(mapOf(0 to PlayerIntentFrame()))
        while (car.bodyDirection != 12 && ticks++ < 3000) match.tick(mapOf(0 to PlayerIntentFrame(turnLeft = true)))
        while (won == null && ticks++ < 6000) {
            val frame = match.tick(mapOf(0 to PlayerIntentFrame(forward = true)))
            frame.events.filterIsInstance<GameEvent.RoundWon>().firstOrNull()?.let { won = it.playerId }
        }
        assertNotNull(won)
        assertEquals(0, won)
    }

    private fun pictureOfType(world: TankArenaWorld, type: String): Int? =
        (0 until 400).firstOrNull { PictureCatalog.legacyRow(world, it)?.type == type }

    private fun pad(type: Int, tileX: Int, tileY: Int = ROW, direction: Int = 0, ammo: List<Int>? = null) = AuthoredObject(
        id = "pad$type-$tileX-$tileY",
        kind = ObjectKinds.PLAYER_START,
        x = tileX * T + T / 2,
        y = tileY * T + T / 2,
        properties = mapOf("startType" to type.toString(), "direction" to direction.toString()) +
            (ammo?.let { mapOf("ammo" to it.joinToString(",")) } ?: emptyMap()),
    )

    private fun flag(type: Int, tileX: Int) = AuthoredObject(
        id = "flag$type-$tileX",
        kind = ObjectKinds.FLAG,
        x = tileX * T + T / 2,
        y = ROW * T + T / 2,
        properties = mapOf("flagType" to type.toString()),
    )

    private fun start(
        pads: List<AuthoredObject>,
        flags: List<AuthoredObject> = emptyList(),
        dual: DualSetup = DualSetup(),
        base: Map<Int, Int> = emptyMap(),
        world: TankArenaWorld = TankArenaWorld.DESERT,
    ): Match {
        val width = 16
        val height = 6
        val cells = width * height
        val map = CanonicalMapDefinition(
            metadata = MapMetadata(name = "dual-fixture", widthTiles = width, heightTiles = height, missionCode = "TEST", world = world),
            layers = TileLayers(
                base = List(cells) { if (it / width == ROW) base[it % width] ?: -1 else -1 },
                solid = List(cells) { -1 },
                top = List(cells) { -1 },
                goalLayer = List(cells) { 0 },
                bonusLayer = List(cells) { 255 },
                manTypeLayer = List(cells) { 0 },
                manAmountLayer = List(cells) { 0 },
            ),
            missionText = MissionText(),
            objects = pads + flags,
        )
        val match = Match.fromCanonicalMap(map, MatchMode.DUAL, dual = dual)
        match.initialize()
        return match
    }

    private companion object {
        const val T = LEGACY_TILE_SIZE
        const val ROW = 2
        const val STANDARD = 0
        const val CHOPPER = 5
    }
}

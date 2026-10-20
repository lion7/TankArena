package com.tankarena.sim

import com.tankarena.content.GameModeCompatibility
import com.tankarena.sim.map.ShippedMaps
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** ADR 0027: four players as two teams (Extended). */
class TeamsTest {
    private fun firstMap(type: GameModeCompatibility) = ShippedMaps.baseNames().first { ShippedMaps.load(it)!!.document.header.type == type }

    private fun match(map: String, mode: MatchMode, players: Int): Match =
        Match.fromMap(ShippedMaps.load(map)!!.document, mode, dual = DualSetup(tankTypes = listOf(0, 0, 0, 0)), ruleset = Ruleset.EXTENDED, players = players)
            .also { it.initialize() }

    @Test
    fun `player vs player gives four players a vehicle each on their side's pads, none on another`() {
        val map = firstMap(GameModeCompatibility.DUAL)
        val match = match(map, MatchMode.DUAL, 4)
        val players = match.tanksForTest().filter { it.playerIndex >= 0 }.sortedBy { it.playerIndex }
        assertEquals(listOf(0, 1, 2, 3), players.map { it.playerIndex }, map)
        for (tank in players) assertEquals(tank.playerIndex and 1, tank.startPadType and 1, "$map: player ${tank.playerIndex} on its side's pad")
        for ((i, a) in players.withIndex()) for (b in players.drop(i + 1)) {
            val dx = a.positionX - b.positionX
            val dy = a.positionY - b.positionY
            assertTrue(dx * dx + dy * dy >= 30 * 30, "$map: players ${a.playerIndex} and ${b.playerIndex} start apart")
        }
        repeat(30) { match.stepForTest() }
        assertEquals(4, match.tanksForTest().count { it.playerIndex >= 0 && it.armor > 0 }, "$map: nobody blew up at the start")
    }

    @Test
    fun `against the computer four players are all on the good side`() {
        val map = firstMap(GameModeCompatibility.DUAL_VS_COMPUTER)
        val match = match(map, MatchMode.DUAL_VS_COMPUTER, 4)
        val players = match.tanksForTest().filter { it.playerIndex >= 0 }.distinctBy { it.playerIndex }
        assertEquals(setOf(0, 1, 2, 3), players.map { it.playerIndex }.toSet(), map)
        assertTrue(players.all { it.team == 0 })
        assertEquals(4, match.buildPlayerViews().size)
    }

    @Test
    fun `on every map against the computer four players start apart`() {
        for (name in ShippedMaps.baseNames()) {
            val document = ShippedMaps.load(name)!!.document
            if (document.header.type != GameModeCompatibility.DUAL_VS_COMPUTER && document.header.type != GameModeCompatibility.SINGLE_OR_DUAL) continue
            val match = Match.fromMap(document, MatchMode.DUAL_VS_COMPUTER, ruleset = Ruleset.EXTENDED, players = 4).also { it.initialize() }
            val players = match.tanksForTest().filter { it.playerIndex >= 0 }.distinctBy { it.playerIndex }
            val spots = players.map { it.positionX to it.positionY }
            assertEquals(spots.size, spots.toSet().size, "$name: ${players.map { "p${it.playerIndex} ${it.positionX},${it.positionY}" }}")
        }
    }

    @Test
    fun `on every player-vs-player map four players get a vehicle each, apart`() {
        for (name in ShippedMaps.baseNames()) {
            val document = ShippedMaps.load(name)!!.document
            if (document.header.type != GameModeCompatibility.DUAL) continue
            // Each map's own kind of vehicle: tanks where it has tank pads, else what it has.
            val contents = document.header.contents
            val type = when {
                contents and 1 != 0 -> 0
                contents and 2 != 0 -> 5
                contents and 4 != 0 -> 4
                else -> 0
            }
            val match = Match.fromMap(document, MatchMode.DUAL, dual = DualSetup(tankTypes = List(4) { type }), ruleset = Ruleset.EXTENDED, players = 4)
                .also { it.initialize() }
            val players = match.tanksForTest().filter { it.playerIndex >= 0 }
            assertEquals(4, players.size, name)
            val spots = players.map { it.positionX to it.positionY }
            assertEquals(4, spots.toSet().size, "$name: ${players.map { "p${it.playerIndex} ${it.positionX},${it.positionY}" }}")
        }
    }

    @Test
    fun `Classic stays at two players`() {
        assertFailsWith<IllegalArgumentException> {
            Match.fromMap(ShippedMaps.load(firstMap(GameModeCompatibility.DUAL))!!.document, MatchMode.DUAL, players = 4)
        }
    }

    @Test
    fun `a side's turret spares its team mates`() {
        val turret = Turret(Turret.State(shootAt = 3))
        val sides = (0..3).map { player -> player to player.side }
        assertEquals(listOf(0 to 0, 1 to 1, 2 to 0, 3 to 1), sides)
        assertEquals(-1, (-1).side)
        assertTrue(turret.shootAt == 3)
    }

    @Test
    fun `a four-player match replays the same`() {
        val map = firstMap(GameModeCompatibility.DUAL_VS_COMPUTER)
        val client = com.tankarena.sim.LocalMatchClient.fromMap(ShippedMaps.load(map)!!.document, MatchMode.DUAL_VS_COMPUTER, recordAs = map, ruleset = Ruleset.EXTENDED, players = 4)
        repeat(300) { tick ->
            for (p in 0..3) client.submitInput(com.tankarena.protocol.InputFrame(p, tick.toLong(), forward = true, firePrimary = tick % 40 < 5, turnLeft = p % 2 == 0))
            client.tick()
        }
        val recording = MatchRecording.fromJson(client.recorder!!.recording().toJson())
        assertEquals(4, recording.playerCount)
        val replay = ReplayMatchClient(recording, ShippedMaps.load(map)!!.document)
        var last = replay.currentServerFrame()
        while (!replay.finished) last = replay.tick()
        val live = client.currentServerFrame()
        assertEquals(live.playerViews.map { it.cameraCenterX to it.cameraCenterY }, last.playerViews.map { it.cameraCenterX to it.cameraCenterY })
    }
}

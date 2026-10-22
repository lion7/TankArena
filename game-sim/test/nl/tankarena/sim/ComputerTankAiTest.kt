package nl.tankarena.sim

import nl.tankarena.content.LEGACY_TILE_SIZE
import nl.tankarena.content.MapMetadata
import nl.tankarena.content.MissionText
import nl.tankarena.content.TileLayers
import nl.tankarena.sim.legacy.AuthoredObject
import nl.tankarena.sim.legacy.CanonicalMapDefinition
import nl.tankarena.sim.legacy.ObjectKinds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Legacy control_computer_tank() (original/src/tanks/computer.c), ported as [ComputerTankAi]. */
class ComputerTankAiTest {

    @Test
    fun `a computer tank drives to its waypoints in turn and stops following them after the last`() {
        // Waypoints are top-left corners: tile (10, 5), then tile (10, 2).
        val match = start(
            ai = ai(tileX = 3, tileY = 5, waypoints = "${10 * T},${5 * T};${10 * T},${2 * T}", wayType = ComputerTankAi.WAY_ACTIVE),
        )
        val tank = match.tanksForTest().single { it.computer }
        var reachedFirst = false
        repeat(3000) {
            match.tick()
            if (tank.wayCurrent == 1) reachedFirst = true
        }
        assertTrue(reachedFirst, "it gets within 30 px of the first waypoint")
        assertEquals(ComputerTankAi.WAY_INACTIVE, tank.wayType, "after the last waypoint the route is done")
    }

    @Test
    fun `a circular route starts over at the first waypoint`() {
        val match = start(
            ai = ai(tileX = 3, tileY = 5, waypoints = "${8 * T},${5 * T};${4 * T},${5 * T}", wayType = ComputerTankAi.WAY_CIRCULAR),
        )
        val tank = match.tanksForTest().single { it.computer }
        val seen = mutableListOf<Int>()
        repeat(4000) {
            match.tick()
            if (seen.lastOrNull() != tank.wayCurrent) seen += tank.wayCurrent
        }
        assertEquals(ComputerTankAi.WAY_CIRCULAR, tank.wayType)
        assertTrue(seen.windowed(3).any { it == listOf(0, 1, 0) }, "went 0 → 1 → 0: $seen")
    }

    @Test
    fun `a computer tank turns away from a building in front of it`() {
        // Driving east along row 5 with a building two tiles ahead and nothing else to do.
        val match = start(ai = ai(tileX = 3, tileY = 5), solid = mapOf(6 + 5 * WIDTH to BUILDING))
        val tank = match.tanksForTest().single { it.computer }
        var turned = false
        repeat(300) {
            match.tick()
            if (tank.bodyDirection != EAST) turned = true
        }
        assertTrue(turned, "it steered away instead of driving on into the building")
    }

    @Test
    fun `a computer tank only fires at targets within its attack range`() {
        val far = start(ai = ai(tileX = 3, tileY = 5, attackAt = 64), player = 3 + 8)
        val near = start(ai = ai(tileX = 3, tileY = 5, attackAt = 300), player = 3 + 8)
        assertEquals(0, shotsIn(far, 200), "8 tiles away is beyond 64 px")
        assertTrue(shotsIn(near, 200) > 0, "but within 300 px")
    }

    private fun shotsIn(match: Match, ticks: Int): Int {
        var shots = 0
        repeat(ticks) {
            shots += match.tick().events.count { it is nl.tankarena.protocol.snapshot.GameEvent.Sound && it.kind == nl.tankarena.protocol.snapshot.SoundKind.MAIN }
        }
        return shots
    }

    private fun ai(
        tileX: Int,
        tileY: Int,
        waypoints: String? = null,
        wayType: Int = ComputerTankAi.WAY_INACTIVE,
        attackAt: Int = 150,
    ) = AuthoredObject(
        id = "ai",
        kind = ObjectKinds.AI_TANK,
        x = tileX * T + T / 2,
        y = tileY * T + T / 2,
        properties = buildMap {
            put("direction", EAST.toString())
            put("activationRadius", "250")
            put("active", "1")
            put("stayActive", "1")
            put("attackAt", attackAt.toString())
            put("attackGoals", "0")
            put("wayType", wayType.toString())
            waypoints?.let { put("waypoints", it) }
        },
    )

    private fun start(ai: AuthoredObject, solid: Map<Int, Int> = emptyMap(), player: Int? = null): Match {
        val cells = WIDTH * HEIGHT
        val objects = mutableListOf(ai)
        if (player != null) {
            objects += AuthoredObject(
                id = "p1",
                kind = ObjectKinds.PLAYER_START,
                x = player * T + T / 2,
                y = 5 * T + T / 2,
                properties = mapOf("direction" to "12", "lives" to "1"),
            )
        }
        val map = CanonicalMapDefinition(
            metadata = MapMetadata(name = "ai-fixture", widthTiles = WIDTH, heightTiles = HEIGHT, missionCode = "TEST"),
            layers = TileLayers(
                base = List(cells) { -1 },
                solid = List(cells) { solid[it] ?: -1 },
                top = List(cells) { -1 },
                goalLayer = List(cells) { 0 },
                bonusLayer = List(cells) { 255 },
                manTypeLayer = List(cells) { 0 },
                manAmountLayer = List(cells) { 0 },
            ),
            missionText = MissionText(),
            objects = objects,
        )
        val match = Match.fromCanonicalMap(map, MatchMode.SINGLE)
        match.initialize()
        return match
    }

    private companion object {
        const val T = LEGACY_TILE_SIZE
        const val WIDTH = 20
        const val HEIGHT = 10
        const val EAST = 4
        const val BUILDING = 8
    }
}

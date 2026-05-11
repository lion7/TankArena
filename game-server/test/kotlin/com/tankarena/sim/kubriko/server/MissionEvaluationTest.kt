package com.tankarena.sim.kubriko.server

import com.tankarena.sim.kubriko.server.legacy.AuthoredObject
import com.tankarena.sim.kubriko.server.legacy.CanonicalMapDefinition
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.GameModeCompatibility
import com.tankarena.content.MapMetadata
import com.tankarena.content.MissionText
import com.tankarena.sim.kubriko.server.legacy.ObjectKinds
import com.tankarena.content.TileLayers
import com.tankarena.input.PlayerIntentFrame
import com.tankarena.protocol.snapshot.GameEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T29 — Full mission/objective evaluation.
 * One scenario test per mode to verify win/loss states and MissionResult emission.
 */
class MissionEvaluationTest {

    // --- SINGLE mode: goal capture win ---

    @Test
    fun `SINGLE mode capturing goals emits MissionWon with GOALS_CAPTURED and MissionResult`() {
        val prototype = ServerMatchPrototype.fromCanonicalMap(
            singlePlayerMap(goalContribution = 100),
        )
        prototype.initialize()

        var missionResult: GameEvent.MissionResult? = null
        loop@ for (i in 0..119) {
            val frame = prototype.tick(mapOf(0 to PlayerIntentFrame(forward = true)))
            missionResult = frame.events.filterIsInstance<GameEvent.MissionResult>().lastOrNull()
            if (prototype.mission != ServerMatchPrototype.MissionStatus.IN_PROGRESS) break@loop
        }

        assertEquals(ServerMatchPrototype.MissionStatus.WON, prototype.mission)
        assertTrue(missionResult != null, "MissionResult should be emitted")
        assertEquals(true, missionResult!!.won)
        assertEquals("GOALS_CAPTURED", missionResult!!.reason)
        assertEquals(1, missionResult!!.captures)
        assertEquals(500, missionResult!!.score)

        prototype.dispose()
    }

    // --- SINGLE mode: all enemies eliminated win ---

    @Test
    fun `SINGLE mode eliminating all enemies emits MissionWon with ALL_ENEMIES_ELIMINATED`() {
        val prototype = ServerMatchPrototype.fromCanonicalMap(
            singlePlayerMapWithEnemy(enemyArmor = 10, enemyLives = 1),
        )
        prototype.initialize()

        // Deal enough damage to kill the enemy tank
        prototype.queueDamageToEnemyForTest(999)

        var missionResult: GameEvent.MissionResult? = null
        loop@ for (i in 0..19) {
            val frame = prototype.tick()
            missionResult = frame.events.filterIsInstance<GameEvent.MissionResult>().lastOrNull()
            if (prototype.mission != ServerMatchPrototype.MissionStatus.IN_PROGRESS) break@loop
        }

        assertEquals(ServerMatchPrototype.MissionStatus.WON, prototype.mission)
        assertTrue(missionResult != null, "MissionResult should be emitted")
        assertEquals(true, missionResult!!.won)
        assertEquals("ALL_ENEMIES_ELIMINATED", missionResult!!.reason)
        assertEquals(1, missionResult!!.kills)

        prototype.dispose()
    }

    // --- SINGLE mode: all lives lost ---

    @Test
    fun `SINGLE mode all lives lost emits MissionLost with ALL_LIVES_LOST`() {
        val prototype = ServerMatchPrototype.fromCanonicalMap(
            singlePlayerMap(playerLives = 1, playerArmor = 10),
        )
        prototype.initialize()

        // Kill the player tank
        prototype.queueDamageForTest(0, 999)

        var missionResult: GameEvent.MissionResult? = null
        loop@ for (i in 0..399) {
            val frame = prototype.tick()
            missionResult = frame.events.filterIsInstance<GameEvent.MissionResult>().lastOrNull()
            if (prototype.mission != ServerMatchPrototype.MissionStatus.IN_PROGRESS) break@loop
        }

        assertEquals(ServerMatchPrototype.MissionStatus.LOST, prototype.mission)
        assertTrue(missionResult != null, "MissionResult should be emitted")
        assertEquals(false, missionResult!!.won)
        assertEquals("ALL_LIVES_LOST", missionResult!!.reason)

        prototype.dispose()
    }

    // --- DUAL mode: team elimination ---

    @Test
    fun `DUAL mode team1 eliminated emits MissionWon with OPPONENT_ELIMINATED`() {
        val prototype = ServerMatchPrototype.fromCanonicalMap(
            dualPlayerMap(team1Armor = 10, team1Lives = 1),
        )
        prototype.initialize()

        // Kill team 1 tank
        val team1Tank = prototype.playerTankForTest(1)
        if (team1Tank != null) {
            team1Tank.queueDamage(999)
        }

        var missionResult: GameEvent.MissionResult? = null
        loop@ for (i in 0..399) {
            val frame = prototype.tick()
            missionResult = frame.events.filterIsInstance<GameEvent.MissionResult>().lastOrNull()
            if (prototype.mission != ServerMatchPrototype.MissionStatus.IN_PROGRESS) break@loop
        }

        assertEquals(ServerMatchPrototype.MissionStatus.WON, prototype.mission)
        assertTrue(missionResult != null, "MissionResult should be emitted")
        assertEquals(true, missionResult!!.won)
        assertEquals("OPPONENT_ELIMINATED", missionResult!!.reason)

        prototype.dispose()
    }

    // --- DUAL mode: team 0 eliminated ---

    @Test
    fun `DUAL mode team0 eliminated emits MissionLost with PLAYER_ELIMINATED`() {
        val prototype = ServerMatchPrototype.fromCanonicalMap(
            dualPlayerMap(team0Armor = 10, team0Lives = 1),
        )
        prototype.initialize()

        // Kill team 0 tank
        prototype.queueDamageForTest(0, 999)

        var missionResult: GameEvent.MissionResult? = null
        loop@ for (i in 0..399) {
            val frame = prototype.tick()
            missionResult = frame.events.filterIsInstance<GameEvent.MissionResult>().lastOrNull()
            if (prototype.mission != ServerMatchPrototype.MissionStatus.IN_PROGRESS) break@loop
        }

        assertEquals(ServerMatchPrototype.MissionStatus.LOST, prototype.mission)
        assertTrue(missionResult != null, "MissionResult should be emitted")
        assertEquals(false, missionResult!!.won)
        assertEquals("PLAYER_ELIMINATED", missionResult!!.reason)

        prototype.dispose()
    }

    // --- DUAL_VS_COMPUTER mode: same as SINGLE ---

    @Test
    fun `DUAL_VS_COMPUTER mode eliminating enemies emits MissionWon`() {
        val prototype = ServerMatchPrototype.fromCanonicalMap(
            dualVcMap(enemyArmor = 10, enemyLives = 1),
        )
        prototype.initialize()

        prototype.queueDamageToEnemyForTest(999)

        var missionResult: GameEvent.MissionResult? = null
        loop@ for (i in 0..19) {
            val frame = prototype.tick()
            missionResult = frame.events.filterIsInstance<GameEvent.MissionResult>().lastOrNull()
            if (prototype.mission != ServerMatchPrototype.MissionStatus.IN_PROGRESS) break@loop
        }

        assertEquals(ServerMatchPrototype.MissionStatus.WON, prototype.mission)
        assertTrue(missionResult != null, "MissionResult should be emitted")
        assertEquals(true, missionResult!!.won)
        assertEquals("ALL_ENEMIES_ELIMINATED", missionResult!!.reason)

        prototype.dispose()
    }

    // --- DONT_CARE mode: falls through to generic evaluation ---

    @Test
    fun `DONT_CARE mode goal capture works as generic evaluation`() {
        val prototype = ServerMatchPrototype.fromCanonicalMap(
            dontCareMap(goalContribution = 100),
        )
        prototype.initialize()

        var missionResult: GameEvent.MissionResult? = null
        loop@ for (i in 0..119) {
            val frame = prototype.tick(mapOf(0 to PlayerIntentFrame(forward = true)))
            missionResult = frame.events.filterIsInstance<GameEvent.MissionResult>().lastOrNull()
            if (prototype.mission != ServerMatchPrototype.MissionStatus.IN_PROGRESS) break@loop
        }

        assertEquals(ServerMatchPrototype.MissionStatus.WON, prototype.mission)
        assertTrue(missionResult != null, "MissionResult should be emitted")
        assertEquals(true, missionResult!!.won)
        assertEquals("GOALS_CAPTURED", missionResult!!.reason)

        prototype.dispose()
    }

    // --- MissionResult includes correct tick count ---

    @Test
    fun `MissionResult includes elapsed ticks`() {
        val prototype = ServerMatchPrototype.fromCanonicalMap(
            singlePlayerMap(goalContribution = 100),
        )
        prototype.initialize()

        var missionResult: GameEvent.MissionResult? = null
        loop@ for (i in 0..119) {
            val frame = prototype.tick(mapOf(0 to PlayerIntentFrame(forward = true)))
            missionResult = frame.events.filterIsInstance<GameEvent.MissionResult>().lastOrNull()
            if (prototype.mission != ServerMatchPrototype.MissionStatus.IN_PROGRESS) break@loop
        }

        assertTrue(missionResult != null)
        assertTrue(missionResult!!.ticks > 0, "ticks should be > 0")

        prototype.dispose()
    }

    // --- MissionResult is emitted only once ---

    @Test
    fun `MissionResult is emitted only once even after mission ends`() {
        val prototype = ServerMatchPrototype.fromCanonicalMap(
            singlePlayerMap(goalContribution = 100),
        )
        prototype.initialize()

        // Run until mission ends
        loop@ for (i in 0..119) {
            prototype.tick(mapOf(0 to PlayerIntentFrame(forward = true)))
            if (prototype.mission != ServerMatchPrototype.MissionStatus.IN_PROGRESS) break@loop
        }

        // Count MissionResult events in subsequent ticks
        var resultCount = 0
        for (i in 0..49) {
            val frame = prototype.tick()
            resultCount += frame.events.filterIsInstance<GameEvent.MissionResult>().size
        }

        assertEquals(0, resultCount, "No additional MissionResult events after mission ends")

        prototype.dispose()
    }

    // --- Helper: build a SINGLE mode map with a goal ---

    private fun singlePlayerMap(
        goalContribution: Int = 50,
        playerLives: Int = 3,
        playerArmor: Int = 100,
    ): CanonicalMapDefinition {
        val widthTiles = 10
        val heightTiles = 3
        val cellCount = widthTiles * heightTiles
        return CanonicalMapDefinition(
            metadata = MapMetadata(
                name = "single-fixture",
                widthTiles = widthTiles,
                heightTiles = heightTiles,
                missionCode = "SINGLE01",
                modeCompatibility = GameModeCompatibility.SINGLE,
            ),
            layers = TileLayers(
                base = List(cellCount) { -1 },
                solid = MutableList(cellCount) { idx ->
                    val x = idx % widthTiles
                    val y = idx / widthTiles
                    if (x == 0 || y == 0 || x == widthTiles - 1 || y == heightTiles - 1) 0 else -1
                },
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
                    x = 1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    y = 1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    properties = mapOf(
                        "direction" to "4",
                        "lives" to playerLives.toString(),
                        "armor" to playerArmor.toString(),
                    ),
                ),
                AuthoredObject(
                    id = "g1",
                    kind = ObjectKinds.GOAL,
                    x = 8 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    y = 1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    properties = mapOf(
                        "who" to "0",
                        "contribution" to goalContribution.toString(),
                    ),
                ),
            ),
        )
    }

    // --- Helper: SINGLE mode map with an enemy tank ---

    private fun singlePlayerMapWithEnemy(
        enemyArmor: Int = 100,
        enemyLives: Int = 3,
    ): CanonicalMapDefinition {
        val widthTiles = 10
        val heightTiles = 5
        val cellCount = widthTiles * heightTiles
        return CanonicalMapDefinition(
            metadata = MapMetadata(
                name = "single-enemy-fixture",
                widthTiles = widthTiles,
                heightTiles = heightTiles,
                missionCode = "SINGLE02",
                modeCompatibility = GameModeCompatibility.SINGLE,
            ),
            layers = TileLayers(
                base = List(cellCount) { -1 },
                solid = MutableList(cellCount) { idx ->
                    val x = idx % widthTiles
                    val y = idx / widthTiles
                    if (x == 0 || y == 0 || x == widthTiles - 1 || y == heightTiles - 1) 0 else -1
                },
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
                    y = 3 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    properties = mapOf("direction" to "4", "lives" to "3"),
                ),
                AuthoredObject(
                    id = "e1",
                    kind = ObjectKinds.ENFORCER,
                    x = 7 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    y = 2 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    properties = mapOf(
                        "direction" to "8",
                        "lives" to enemyLives.toString(),
                        "armor" to enemyArmor.toString(),
                    ),
                ),
            ),
        )
    }

    // --- Helper: DUAL mode map with two player tanks ---

    private fun dualPlayerMap(
        team0Armor: Int = 100,
        team0Lives: Int = 3,
        team1Armor: Int = 100,
        team1Lives: Int = 3,
    ): CanonicalMapDefinition {
        val widthTiles = 10
        val heightTiles = 5
        val cellCount = widthTiles * heightTiles
        return CanonicalMapDefinition(
            metadata = MapMetadata(
                name = "dual-fixture",
                widthTiles = widthTiles,
                heightTiles = heightTiles,
                missionCode = "DUAL01",
                modeCompatibility = GameModeCompatibility.DUAL,
            ),
            layers = TileLayers(
                base = List(cellCount) { -1 },
                solid = MutableList(cellCount) { idx ->
                    val x = idx % widthTiles
                    val y = idx / widthTiles
                    if (x == 0 || y == 0 || x == widthTiles - 1 || y == heightTiles - 1) 0 else -1
                },
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
                    y = 3 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    properties = mapOf(
                        "direction" to "4",
                        "lives" to team0Lives.toString(),
                        "armor" to team0Armor.toString(),
                    ),
                ),
                AuthoredObject(
                    id = "p2",
                    kind = ObjectKinds.PLAYER_START,
                    x = 7 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    y = 2 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    properties = mapOf(
                        "direction" to "8",
                        "lives" to team1Lives.toString(),
                        "armor" to team1Armor.toString(),
                    ),
                ),
            ),
        )
    }

    // --- Helper: DUAL_VS_COMPUTER mode map ---

    private fun dualVcMap(
        enemyArmor: Int = 100,
        enemyLives: Int = 3,
    ): CanonicalMapDefinition {
        val widthTiles = 10
        val heightTiles = 5
        val cellCount = widthTiles * heightTiles
        return CanonicalMapDefinition(
            metadata = MapMetadata(
                name = "dualvc-fixture",
                widthTiles = widthTiles,
                heightTiles = heightTiles,
                missionCode = "DUALVC01",
                modeCompatibility = GameModeCompatibility.DUAL_VS_COMPUTER,
            ),
            layers = TileLayers(
                base = List(cellCount) { -1 },
                solid = MutableList(cellCount) { idx ->
                    val x = idx % widthTiles
                    val y = idx / widthTiles
                    if (x == 0 || y == 0 || x == widthTiles - 1 || y == heightTiles - 1) 0 else -1
                },
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
                    y = 3 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    properties = mapOf("direction" to "4", "lives" to "3"),
                ),
                AuthoredObject(
                    id = "e1",
                    kind = ObjectKinds.ENFORCER,
                    x = 7 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    y = 2 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    properties = mapOf(
                        "direction" to "8",
                        "lives" to enemyLives.toString(),
                        "armor" to enemyArmor.toString(),
                    ),
                ),
            ),
        )
    }

    // --- Helper: DONT_CARE mode map ---

    private fun dontCareMap(goalContribution: Int = 50): CanonicalMapDefinition {
        val widthTiles = 10
        val heightTiles = 3
        val cellCount = widthTiles * heightTiles
        return CanonicalMapDefinition(
            metadata = MapMetadata(
                name = "dontcare-fixture",
                widthTiles = widthTiles,
                heightTiles = heightTiles,
                missionCode = "DC01",
                modeCompatibility = GameModeCompatibility.DONT_CARE,
            ),
            layers = TileLayers(
                base = List(cellCount) { -1 },
                solid = MutableList(cellCount) { idx ->
                    val x = idx % widthTiles
                    val y = idx / widthTiles
                    if (x == 0 || y == 0 || x == widthTiles - 1 || y == heightTiles - 1) 0 else -1
                },
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
                    x = 1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    y = 1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    properties = mapOf("direction" to "4", "lives" to "3"),
                ),
                AuthoredObject(
                    id = "g1",
                    kind = ObjectKinds.GOAL,
                    x = 8 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    y = 1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    properties = mapOf(
                        "who" to "0",
                        "contribution" to goalContribution.toString(),
                    ),
                ),
            ),
        )
    }
}

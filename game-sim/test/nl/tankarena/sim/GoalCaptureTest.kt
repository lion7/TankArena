package nl.tankarena.sim

import nl.tankarena.sim.legacy.AuthoredObject
import nl.tankarena.sim.legacy.CanonicalMapDefinition
import nl.tankarena.content.LEGACY_TILE_SIZE
import nl.tankarena.content.MapMetadata
import nl.tankarena.content.MissionText
import nl.tankarena.sim.legacy.ObjectKinds
import nl.tankarena.content.TileLayers
import nl.tankarena.input.PlayerIntentFrame
import nl.tankarena.protocol.snapshot.GameEvent
import nl.tankarena.protocol.snapshot.GoalState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GoalCaptureTest {

    @Test
    fun `player tank driving over a goal captures it exactly once`() {
        val prototype = Match.fromCanonicalMap(
            goalMap(
                goalContribution = 50,
                playerDirection = 4,
            ),
        )
        prototype.initialize()

        assertFalse(prototype.snapshot().soleGoal().captured)
        repeat(600) {
            prototype.tick(mapOf(0 to PlayerIntentFrame(forward = true)))
        }

        assertTrue(prototype.snapshot().soleGoal().captured)
        assertEquals(50, prototype.goalProgressGood)
    }

    @Test
    fun `reaching a full-contribution goal emits MissionWon and freezes mission status`() {
        val prototype = Match.fromCanonicalMap(
            goalMap(
                goalContribution = 100,
                playerDirection = 4,
            ),
        )
        prototype.initialize()

        var sawWin = false
        repeat(600) {
            val frame = prototype.tick(mapOf(0 to PlayerIntentFrame(forward = true)))
            if (frame.events.any { it is GameEvent.MissionWon }) sawWin = true
        }

        assertTrue(sawWin, "MissionWon event should have fired once")
        assertEquals(Match.MissionStatus.WON, prototype.mission)

        val afterFrame = prototype.tick(mapOf(0 to PlayerIntentFrame(forward = true)))
        assertTrue(
            afterFrame.events.none { it is GameEvent.MissionWon },
            "MissionWon should not re-fire on subsequent ticks",
        )
    }

    private fun nl.tankarena.protocol.snapshot.WorldSnapshot.soleGoal(): GoalState =
        actors.filterIsInstance<GoalState>().single()

    private fun goalMap(goalContribution: Int, playerDirection: Int): CanonicalMapDefinition {
        val widthTiles = 8
        val heightTiles = 3
        val cellCount = widthTiles * heightTiles
        return CanonicalMapDefinition(
            metadata = MapMetadata(
                name = "goal-fixture",
                widthTiles = widthTiles,
                heightTiles = heightTiles,
                missionCode = "TEST",
            ),
            layers = TileLayers(
                base = List(cellCount) { -1 },
                solid = MutableList(cellCount) { -1 },
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
                    properties = mapOf("direction" to playerDirection.toString(), "lives" to "3"),
                ),
                AuthoredObject(
                    id = "g1",
                    kind = ObjectKinds.GOAL,
                    x = 5 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
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

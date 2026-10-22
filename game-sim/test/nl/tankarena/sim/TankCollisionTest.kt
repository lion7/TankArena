package nl.tankarena.sim

import nl.tankarena.sim.legacy.AuthoredObject
import nl.tankarena.sim.legacy.CanonicalMapDefinition
import nl.tankarena.content.LEGACY_TILE_SIZE
import nl.tankarena.content.MapMetadata
import nl.tankarena.content.MissionText
import nl.tankarena.sim.legacy.ObjectKinds
import nl.tankarena.content.TileLayers
import nl.tankarena.input.PlayerIntentFrame
import nl.tankarena.protocol.snapshot.TankState
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TankCollisionTest {

    @Test
    fun `tank cannot push through a single wall while driving diagonally`() {
        val prototype = Match.fromCanonicalMap(
            verticalWallMap(
                widthTiles = 5,
                heightTiles = 5,
                wallX = 3,
                players = listOf(
                    AuthoredObject(
                        id = "p1",
                        kind = ObjectKinds.PLAYER_START,
                        x = 1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                        y = 2 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                        properties = mapOf("direction" to "6", "lives" to "3"),
                    ),
                ),
            ),
        )
        prototype.initialize()

        repeat(40) { prototype.tick(mapOf(0 to PlayerIntentFrame(forward = true))) }
        val tank = prototype.snapshot().singlePlayerTank()

        assertTrue(
            tank.x < 3 * LEGACY_TILE_SIZE,
            "tank should not cross the wall column — x=${tank.x}",
        )
        assertTrue(
            tank.y > 2 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
            "tank should slide south along the wall — y=${tank.y}",
        )
        prototype.dispose()
    }

    @Test
    fun `two tanks driving into each other separate via mutual push`() {
        val prototype = Match.fromCanonicalMap(
            emptyMap(
                widthTiles = 8,
                heightTiles = 3,
                players = listOf(
                    AuthoredObject(
                        id = "a",
                        kind = ObjectKinds.PLAYER_START,
                        x = 2 * LEGACY_TILE_SIZE,
                        y = 1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                        properties = mapOf("direction" to "4", "lives" to "3"),
                    ),
                    AuthoredObject(
                        id = "b",
                        kind = ObjectKinds.PLAYER_START,
                        x = 2 * LEGACY_TILE_SIZE + 24,
                        y = 1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                        properties = mapOf("direction" to "12", "lives" to "3"),
                    ),
                ),
            ),
        )
        prototype.initialize()

        repeat(20) {
            prototype.tick(
                mapOf(
                    0 to PlayerIntentFrame(forward = true),
                    1 to PlayerIntentFrame(forward = true),
                ),
            )
        }

        val tanks = prototype.snapshot().actors
            .filterIsInstance<TankState>()
            .filter { it.controlled }
            .sortedBy { it.x }
        assertEquals(2, tanks.size)
        val gap = abs(tanks[1].x - tanks[0].x)
        // Legacy check_obj_collide(): vehicles keep their radii (10 + 10 px for two STANDARD tanks) apart.
        val radii = 2 * LegacyVehicles.spec(0).radius
        assertTrue(gap >= radii, "tanks should stay at least $radii px apart — gap=$gap")
        prototype.dispose()
    }

    @Test
    fun `perpendicular nudge slides along the contact axis without jitter`() {
        val prototype = Match.fromCanonicalMap(
            emptyMap(
                widthTiles = 8,
                heightTiles = 5,
                players = listOf(
                    AuthoredObject(
                        id = "mover",
                        kind = ObjectKinds.PLAYER_START,
                        x = 1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                        y = 2 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                        properties = mapOf("direction" to "4", "lives" to "3"),
                    ),
                    AuthoredObject(
                        id = "blocker",
                        kind = ObjectKinds.PLAYER_START,
                        x = 3 * LEGACY_TILE_SIZE,
                        y = 2 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2 - 4,
                        properties = mapOf("direction" to "0", "lives" to "3"),
                    ),
                ),
            ),
        )
        prototype.initialize()

        repeat(60) {
            prototype.tick(
                mapOf(
                    0 to PlayerIntentFrame(forward = true),
                    1 to PlayerIntentFrame(),
                ),
            )
        }

        val tanks = prototype.snapshot().actors
            .filterIsInstance<TankState>()
            .filter { it.controlled }
            .sortedBy { it.x }
        assertEquals(2, tanks.size)
        val gap = abs(tanks[1].x - tanks[0].x)
        assertTrue(gap >= 2 * SERVER_TANK_HALF, "tanks must not overlap after grazing — gap=$gap")
        prototype.dispose()
    }

    @Test
    fun `three tanks driving forward into a column do not overlap`() {
        val prototype = Match.fromCanonicalMap(
            emptyMap(
                widthTiles = 12,
                heightTiles = 3,
                players = listOf(
                    AuthoredObject(
                        id = "rear",
                        kind = ObjectKinds.PLAYER_START,
                        x = 1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                        y = 1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                        properties = mapOf("direction" to "4", "lives" to "3"),
                    ),
                    AuthoredObject(
                        id = "middle",
                        kind = ObjectKinds.PLAYER_START,
                        x = 1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2 + SERVER_TANK_FOOTPRINT + 4,
                        y = 1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                        properties = mapOf("direction" to "4", "lives" to "3"),
                    ),
                    AuthoredObject(
                        id = "front",
                        kind = ObjectKinds.PLAYER_START,
                        x = 1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2 + 2 * (SERVER_TANK_FOOTPRINT + 4),
                        y = 1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                        properties = mapOf("direction" to "4", "lives" to "3"),
                    ),
                ),
            ),
        )
        prototype.initialize()

        repeat(60) {
            prototype.tick(
                mapOf(
                    0 to PlayerIntentFrame(forward = true),
                    1 to PlayerIntentFrame(forward = true),
                    2 to PlayerIntentFrame(forward = true),
                ),
            )
        }

        val tanks = prototype.snapshot().actors
            .filterIsInstance<TankState>()
            .filter { it.controlled }
            .sortedBy { it.x }
        assertEquals(3, tanks.size)
        val gapAB = abs(tanks[1].x - tanks[0].x)
        val gapBC = abs(tanks[2].x - tanks[1].x)
        assertTrue(gapAB >= 2 * SERVER_TANK_HALF, "rear and middle must not overlap — gap=$gapAB")
        assertTrue(gapBC >= 2 * SERVER_TANK_HALF, "middle and front must not overlap — gap=$gapBC")
        prototype.dispose()
    }

    private fun nl.tankarena.protocol.snapshot.WorldSnapshot.singlePlayerTank(): TankState =
        actors.filterIsInstance<TankState>().single { it.controlled }

    private fun verticalWallMap(
        widthTiles: Int,
        heightTiles: Int,
        wallX: Int,
        players: List<AuthoredObject>,
    ): CanonicalMapDefinition {
        val cellCount = widthTiles * heightTiles
        val solid = MutableList(cellCount) { -1 }
        for (y in 0 until heightTiles) {
            solid[wallX + y * widthTiles] = 0
        }
        return canonicalMap(widthTiles, heightTiles, solid, players)
    }

    private fun emptyMap(
        widthTiles: Int,
        heightTiles: Int,
        players: List<AuthoredObject>,
    ): CanonicalMapDefinition {
        val cellCount = widthTiles * heightTiles
        return canonicalMap(widthTiles, heightTiles, MutableList(cellCount) { -1 }, players)
    }

    private fun canonicalMap(
        widthTiles: Int,
        heightTiles: Int,
        solid: List<Int>,
        players: List<AuthoredObject>,
    ): CanonicalMapDefinition {
        val cellCount = widthTiles * heightTiles
        return CanonicalMapDefinition(
            metadata = MapMetadata(
                name = "collision-fixture",
                widthTiles = widthTiles,
                heightTiles = heightTiles,
                missionCode = "TEST",
            ),
            layers = TileLayers(
                base = List(cellCount) { -1 },
                solid = solid,
                top = List(cellCount) { -1 },
                goalLayer = List(cellCount) { 0 },
                bonusLayer = List(cellCount) { -1 },
                manTypeLayer = List(cellCount) { 0 },
                manAmountLayer = List(cellCount) { 0 },
            ),
            missionText = MissionText(),
            objects = players,
        )
    }
}

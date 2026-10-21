package nl.tankarena.sim

import nl.tankarena.content.LEGACY_TILE_SIZE
import nl.tankarena.content.MapMetadata
import nl.tankarena.content.MissionText
import nl.tankarena.content.PictureCatalog
import nl.tankarena.content.TankArenaWorld
import nl.tankarena.content.TileLayers
import nl.tankarena.input.PlayerIntentFrame
import nl.tankarena.protocol.snapshot.B52State
import nl.tankarena.protocol.snapshot.ProjectileState
import nl.tankarena.sim.legacy.AuthoredObject
import nl.tankarena.sim.legacy.CanonicalMapDefinition
import nl.tankarena.sim.legacy.ObjectKinds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** B52 bombers and the zeppelin (original/src/objects/b52.c, zeppelin.c), force fields and ramps. */
class BombersFieldsRampsTest {

    private val row = 2

    @Test
    fun `a B52 waits, takes off, climbs to 19 and drops sticks of bombs`() {
        val match = start(width = 60, height = 30, objects = listOf(b52(tileX = 5, tileY = 15, heading = 1)), playerTileY = 0)
        val bomber = match.snapshot().actors.filterIsInstance<B52State>().single()
        repeat(450) { match.tick() }
        assertEquals(bomber.x, match.snapshot().actors.filterIsInstance<B52State>().single().x, "it waits at least 500 ticks")
        var highest = 1
        var bombs = 0
        repeat(6000) {
            val state = match.tick().actors.filterIsInstance<B52State>().single()
            highest = maxOf(highest, state.height)
            bombs = state.bombCount
        }
        assertEquals(B52.CRUISE_HEIGHT, highest, "it climbs to its cruising height")
        assertTrue(bombs >= 2, "it drops a stick every 650 ticks ($bombs)")
    }

    @Test
    fun `a B52 shot on the ground goes down and burns as a wreck`() {
        val match = start(width = 20, height = 5, objects = listOf(b52(tileX = 6, tileY = row, heading = 1, goal = 40)))
        var wreck = false
        repeat(600) {
            val state = match.tick(mapOf(0 to PlayerIntentFrame(firePrimary = true))).actors.filterIsInstance<B52State>().single()
            wreck = wreck || state.wreck
        }
        assertTrue(wreck, "three hits of 10 bring its armor of 30 down")
        assertEquals(40, match.goalProgressGood, "its goal share counts when it hits the ground")
    }

    @Test
    fun `the zeppelin drifts west and comes back in from the east`() {
        val match = start(width = 20, height = 10, objects = listOf(zeppelin(tileX = 10, tileY = 5)))
        val zeppelin = match.zeppelinsForTest().single()
        val x0 = zeppelin.positionX
        repeat(100) { match.tick() }
        assertEquals(x0 - 10, zeppelin.positionX, "10 px/s")
        var wrapped = false
        repeat(6000) {
            val before = zeppelin.positionX
            match.tick()
            wrapped = wrapped || zeppelin.positionX > before
        }
        assertTrue(wrapped, "past the west edge it comes back in from the east")
    }

    @Test
    fun `shots bounce off force fields`() {
        val field = picture(TankArenaWorld.CITY, "FIELD_V")
        val match = start(width = 12, height = 5, world = TankArenaWorld.CITY, solid = mapOf(6 to field))
        var bounced = false
        repeat(150) {
            val frame = match.tick(mapOf(0 to PlayerIntentFrame(firePrimary = it == 0)))
            bounced = bounced || frame.actors.any { it is ProjectileState && it.vx < 0 }
        }
        assertTrue(bounced, "the shell came back west")
        assertTrue(match.structures.isStanding(6, row), "the field (strength 200) holds")
        assertTrue(match.structures.strength(6, row, StructureLayer.SOLID) < 200, "but the hit wore it down")
    }

    @Test
    fun `a ramp throws a tank into the air`() {
        val ramp = picture(TankArenaWorld.DESERT, "B_RAMP_R")
        val match = start(width = 20, height = 5, base = mapOf(4 to ramp))
        val tank = match.playerTankForTest(0)!!
        var highest = 1
        repeat(300) {
            match.tick(mapOf(0 to PlayerIntentFrame(forward = true)))
            highest = maxOf(highest, tank.height)
        }
        assertTrue(highest >= 2, "it left the ground (highest $highest)")
        assertEquals(1, tank.height, "and came down again")
        assertTrue(tank.armor > 0)
    }

    // ---- Fixture ----

    private fun picture(world: TankArenaWorld, type: String): Int = (0 until 1000).first { PictureCatalog.legacyRow(world, it)?.type == type }

    private fun centre(tile: Int) = tile * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2

    private fun b52(tileX: Int, tileY: Int, heading: Int, goal: Int = 0) = AuthoredObject(
        id = "b52",
        kind = ObjectKinds.B52,
        x = centre(tileX),
        y = centre(tileY),
        properties = mapOf("heading" to heading.toString(), "armor" to "30", "goalContribution" to goal.toString()),
    )

    private fun zeppelin(tileX: Int, tileY: Int) = AuthoredObject(id = "zep", kind = ObjectKinds.ZEPPELIN, x = centre(tileX), y = centre(tileY))

    private fun start(
        width: Int,
        height: Int,
        world: TankArenaWorld = TankArenaWorld.DESERT,
        base: Map<Int, Int> = emptyMap(),
        solid: Map<Int, Int> = emptyMap(),
        objects: List<AuthoredObject> = emptyList(),
        playerTileY: Int = row,
    ): Match {
        val cells = width * height
        val map = CanonicalMapDefinition(
            metadata = MapMetadata(name = "air-fixture", widthTiles = width, heightTiles = height, missionCode = "TEST", world = world),
            layers = TileLayers(
                base = List(cells) { if (it / width == row) base[it % width] ?: -1 else -1 },
                solid = List(cells) { if (it / width == row) solid[it % width] ?: -1 else -1 },
                top = List(cells) { -1 },
                goalLayer = List(cells) { 0 },
                bonusLayer = List(cells) { 255 },
                manTypeLayer = List(cells) { 0 },
                manAmountLayer = List(cells) { 0 },
            ),
            missionText = MissionText(),
            objects = listOf(
                AuthoredObject(
                    id = "p1",
                    kind = ObjectKinds.PLAYER_START,
                    x = centre(2),
                    y = centre(playerTileY),
                    properties = mapOf("direction" to "4", "lives" to "1"),
                ),
            ) + objects,
        )
        val match = Match.fromCanonicalMap(map, MatchMode.SINGLE)
        match.initialize()
        return match
    }
}

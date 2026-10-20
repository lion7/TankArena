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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TankMovementTest {

    @Test
    fun `tank facing north drives forward for 10 ticks and moves up the world`() {
        val prototype = Match.fromCanonicalMap(openFieldWithPlayerFacing(direction = 0))
        prototype.initialize()
        val start = prototype.snapshot().playerTank()

        var last = start
        repeat(50) {
            last = prototype.tick(mapOf(0 to PlayerIntentFrame(forward = true))).playerTank()
        }

        assertEquals(start.x, last.x, "north-facing tank should not drift on the x axis")
        assertTrue(last.y < start.y, "tank should have advanced up (north) — y decreased from ${start.y} to ${last.y}")
        assertTrue(last.vy <= 0f, "velocity should point north (negative y)")
        prototype.dispose()
    }

    @Test
    fun `tank facing east drives forward and moves right`() {
        val prototype = Match.fromCanonicalMap(openFieldWithPlayerFacing(direction = 4))
        prototype.initialize()
        val start = prototype.snapshot().playerTank()

        var last = start
        repeat(50) {
            last = prototype.tick(mapOf(0 to PlayerIntentFrame(forward = true))).playerTank()
        }

        assertTrue(last.x > start.x)
        assertEquals(start.y, last.y)
        prototype.dispose()
    }

    @Test
    fun `turnRight turns once per rotation delay, rounded up to whole steps`() {
        val prototype = Match.fromCanonicalMap(openFieldWithPlayerFacing(direction = 0))
        prototype.initialize()
        val start = prototype.snapshot().playerTank()
        assertEquals(0, start.bodyDirection)

        prototype.tick(mapOf(0 to PlayerIntentFrame(turnRight = true)))
        val afterFirst = prototype.snapshot().playerTank()
        assertEquals(1, afterFirst.bodyDirection, "first tick with turnRight should step body direction by 1")

        // The standard tank's rotation is 7 ticks; tdone() is only looked at every third tick, so
        // the next turn comes on tick 9.
        repeat(8) { prototype.tick(mapOf(0 to PlayerIntentFrame(turnRight = true))) }
        val afterCooldown = prototype.snapshot().playerTank()
        assertEquals(1, afterCooldown.bodyDirection, "no turn before tick 9")

        prototype.tick(mapOf(0 to PlayerIntentFrame(turnRight = true)))
        val afterNinth = prototype.snapshot().playerTank()
        assertEquals(2, afterNinth.bodyDirection, "the second turn comes on tick 9")
        prototype.dispose()
    }

    @Test
    fun `a key tapped between two steps still counts on the next step`() {
        val prototype = Match.fromCanonicalMap(openFieldWithPlayerFacing(direction = 0))
        prototype.initialize()
        prototype.tick(emptyMap())
        // Pressed and released again on the ticks between steps (legacy controls.c latches it).
        prototype.tick(mapOf(0 to PlayerIntentFrame(turnRight = true)))
        prototype.tick(mapOf(0 to PlayerIntentFrame()))
        prototype.tick(mapOf(0 to PlayerIntentFrame()))
        assertEquals(1, prototype.snapshot().playerTank().bodyDirection)
        prototype.dispose()
    }

    @Test
    fun `aimRight rotates turret without affecting body direction`() {
        val prototype = Match.fromCanonicalMap(openFieldWithPlayerFacing(direction = 0))
        prototype.initialize()

        repeat(3) { prototype.tick(mapOf(0 to PlayerIntentFrame(aimRight = true))) }
        val tank = prototype.snapshot().playerTank()

        assertEquals(0, tank.bodyDirection)
        assertNotEquals(0, tank.turretDirection)
        prototype.dispose()
    }

    @Test
    fun `tank decelerates to zero after input is released`() {
        val prototype = Match.fromCanonicalMap(openFieldWithPlayerFacing(direction = 4))
        prototype.initialize()

        repeat(8) { prototype.tick(mapOf(0 to PlayerIntentFrame(forward = true))) }
        val accelerated = prototype.snapshot().playerTank()
        assertTrue(accelerated.vx > 0f, "tank should have positive x velocity after forward ticks")

        repeat(300) { prototype.tick() }
        val settled = prototype.snapshot().playerTank()

        assertEquals(0f, settled.vx, 0.01f)
        assertEquals(0f, settled.vy, 0.01f)
        prototype.dispose()
    }

    @Test
    fun `tank is held steady across 20 idle ticks when no intent is submitted`() {
        val prototype = Match.fromCanonicalMap(openFieldWithPlayerFacing(direction = 0))
        prototype.initialize()
        val start = prototype.snapshot().playerTank()

        repeat(20) { prototype.tick() }
        val after = prototype.snapshot().playerTank()

        assertEquals(start.x, after.x)
        assertEquals(start.y, after.y)
        prototype.dispose()
    }

    private fun nl.tankarena.protocol.snapshot.WorldSnapshot.playerTank(): TankState =
        actors.filterIsInstance<TankState>().single { it.controlled }

    private fun openFieldWithPlayerFacing(direction: Int): CanonicalMapDefinition {
        val width = 20
        val height = 15
        val cellCount = width * height
        val solid = MutableList(cellCount) { -1 }
        return CanonicalMapDefinition(
            metadata = MapMetadata(
                name = "movement-fixture",
                widthTiles = width,
                heightTiles = height,
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
            objects = listOf(
                AuthoredObject(
                    id = "p1",
                    kind = ObjectKinds.PLAYER_START,
                    x = (width / 2) * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    y = (height / 2) * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    properties = mapOf(
                        "direction" to direction.toString(),
                        "lives" to "3",
                    ),
                ),
            ),
        )
    }

    private fun assertEquals(expected: Float, actual: Float, tolerance: Float) {
        val diff = kotlin.math.abs(expected - actual)
        assertTrue(diff <= tolerance, "expected $expected±$tolerance, got $actual")
    }
}

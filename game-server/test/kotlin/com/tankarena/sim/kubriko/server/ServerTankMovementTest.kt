package com.tankarena.sim.kubriko.server

import com.tankarena.sim.kubriko.server.legacy.AuthoredObject
import com.tankarena.sim.kubriko.server.legacy.CanonicalMapDefinition
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.MapMetadata
import com.tankarena.content.MissionText
import com.tankarena.sim.kubriko.server.legacy.ObjectKinds
import com.tankarena.content.TileLayers
import com.tankarena.input.PlayerIntentFrame
import com.tankarena.protocol.snapshot.TankState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ServerTankMovementTest {

    @Test
    fun `tank facing north drives forward for 10 ticks and moves up the world`() {
        val prototype = ServerMatchPrototype.fromCanonicalMap(openFieldWithPlayerFacing(direction = 0))
        prototype.initialize()
        val start = prototype.snapshot().playerTank()

        var last = start
        repeat(10) {
            last = prototype.tick(mapOf(0 to PlayerIntentFrame(forward = true))).playerTank()
        }

        assertEquals(start.x, last.x, "north-facing tank should not drift on the x axis")
        assertTrue(last.y < start.y, "tank should have advanced up (north) — y decreased from ${start.y} to ${last.y}")
        assertTrue(last.vy <= 0f, "velocity should point north (negative y)")
        prototype.dispose()
    }

    @Test
    fun `tank facing east drives forward and moves right`() {
        val prototype = ServerMatchPrototype.fromCanonicalMap(openFieldWithPlayerFacing(direction = 4))
        prototype.initialize()
        val start = prototype.snapshot().playerTank()

        var last = start
        repeat(10) {
            last = prototype.tick(mapOf(0 to PlayerIntentFrame(forward = true))).playerTank()
        }

        assertTrue(last.x > start.x)
        assertEquals(start.y, last.y)
        prototype.dispose()
    }

    @Test
    fun `turnRight advances bodyDirection once every TURN_COOLDOWN_TICKS`() {
        val prototype = ServerMatchPrototype.fromCanonicalMap(openFieldWithPlayerFacing(direction = 0))
        prototype.initialize()
        val start = prototype.snapshot().playerTank()
        assertEquals(0, start.bodyDirection)

        prototype.tick(mapOf(0 to PlayerIntentFrame(turnRight = true)))
        val afterFirst = prototype.snapshot().playerTank()
        assertEquals(1, afterFirst.bodyDirection, "first tick with turnRight should step body direction by 1")

        repeat(6) { prototype.tick(mapOf(0 to PlayerIntentFrame(turnRight = true))) }
        val afterCooldown = prototype.snapshot().playerTank()
        assertEquals(1, afterCooldown.bodyDirection, "cooldown should keep direction stable for 7 ticks total")

        prototype.tick(mapOf(0 to PlayerIntentFrame(turnRight = true)))
        val afterEighth = prototype.snapshot().playerTank()
        assertEquals(2, afterEighth.bodyDirection, "eighth tick should step direction again after cooldown")
        prototype.dispose()
    }

    @Test
    fun `aimRight rotates turret without affecting body direction`() {
        val prototype = ServerMatchPrototype.fromCanonicalMap(openFieldWithPlayerFacing(direction = 0))
        prototype.initialize()

        repeat(3) { prototype.tick(mapOf(0 to PlayerIntentFrame(aimRight = true))) }
        val tank = prototype.snapshot().playerTank()

        assertEquals(0, tank.bodyDirection)
        assertNotEquals(0, tank.turretDirection)
        prototype.dispose()
    }

    @Test
    fun `tank decelerates to zero after input is released`() {
        val prototype = ServerMatchPrototype.fromCanonicalMap(openFieldWithPlayerFacing(direction = 4))
        prototype.initialize()

        repeat(8) { prototype.tick(mapOf(0 to PlayerIntentFrame(forward = true))) }
        val accelerated = prototype.snapshot().playerTank()
        assertTrue(accelerated.vx > 0f, "tank should have positive x velocity after forward ticks")

        repeat(60) { prototype.tick() }
        val settled = prototype.snapshot().playerTank()

        assertEquals(0f, settled.vx, 0.01f)
        assertEquals(0f, settled.vy, 0.01f)
        prototype.dispose()
    }

    @Test
    fun `tank is held steady across 20 idle ticks when no intent is submitted`() {
        val prototype = ServerMatchPrototype.fromCanonicalMap(openFieldWithPlayerFacing(direction = 0))
        prototype.initialize()
        val start = prototype.snapshot().playerTank()

        repeat(20) { prototype.tick() }
        val after = prototype.snapshot().playerTank()

        assertEquals(start.x, after.x)
        assertEquals(start.y, after.y)
        prototype.dispose()
    }

    private fun com.tankarena.protocol.snapshot.WorldSnapshot.playerTank(): TankState =
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

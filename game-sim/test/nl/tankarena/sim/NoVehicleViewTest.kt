package nl.tankarena.sim

import nl.tankarena.input.PlayerIntentFrame
import nl.tankarena.sim.map.ShippedMaps
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Legacy F1–F10 on an id without a vehicle: `game.tk[0] = NULL`, the window shows static. */
class NoVehicleViewTest {
    @Test
    fun `picking an id without a vehicle leaves the player without one until another key`() {
        val match = Match.fromMap(assertNotNull(ShippedMaps.load("begin1")).document)
        match.initialize()
        val own = assertNotNull(match.playerTankForTest(0))
        val unused = (1..10).first { id -> match.tanksForTest().none { it.controllerId == id } }

        match.stepForTest(mapOf(0 to PlayerIntentFrame(selectTankId = unused)))
        val view = match.buildPlayerViews().single()
        assertNull(view.controlledActorId)
        assertEquals(unused, view.hud.tankId)
        assertNull(match.playerTankForTest(0))

        match.stepForTest(mapOf(0 to PlayerIntentFrame(selectTankId = own.controllerId)))
        assertEquals(own, match.playerTankForTest(0))
        assertNotNull(match.buildPlayerViews().single().controlledActorId)
    }
}

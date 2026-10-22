package nl.tankarena.app

import androidx.compose.ui.input.key.Key
import nl.tankarena.input.PadReading
import nl.tankarena.input.Seat
import nl.tankarena.protocol.snapshot.HudState
import nl.tankarena.protocol.snapshot.PlayerView
import nl.tankarena.protocol.snapshot.ServerFrame
import nl.tankarena.protocol.snapshot.TankState
import nl.tankarena.protocol.snapshot.WorldSnapshot
import nl.tankarena.ui.compose.extended.JoinState
import nl.tankarena.ui.compose.menu.DEFAULT_CONTROLS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Extended's controls: player one with keyboard and mouse, the others with twin-stick gamepads. */
class ExtendedControlsTest {
    private val mouseSeat = Seat(keys = null, pad = null, mouse = true)

    /** A frame with player [seat]'s tank at (200, 200), body facing [body] and turret [turret]. */
    private fun frame(seat: Int = 0, body: Int = 0, turret: Int = 0) = ServerFrame(
        tick = 1,
        world = WorldSnapshot(tick = 1, actors = listOf(TankState(actorId = 7, x = 200, y = 200, bodyDirection = body, turretDirection = turret))),
        playerViews = listOf(PlayerView(playerId = seat, controlledActorId = 7, cameraCenterX = 200, cameraCenterY = 200, hud = HudState())),
    )

    private fun controls(vararg seats: Seat) = DesktopControls().apply {
        extended = true
        this.seats = seats.toList()
    }

    @Test
    fun `the turret turns the short way towards the mouse`() {
        val controls = controls(mouseSeat)
        // The turret faces north (0); the pointer is to the east: turn right.
        controls.mouse.pointAt(300, 200)
        val east = controls.toInputFrame(0, 0, frame(turret = 0))
        assertTrue(east.aimRight && !east.aimLeft)
        // The pointer to the west: left round.
        controls.mouse.pointAt(100, 200)
        assertTrue(controls.toInputFrame(0, 1, frame(turret = 0)).aimLeft)
        // Already pointing there: no turn. Right on the vehicle: nothing either.
        controls.mouse.pointAt(200, 100)
        assertFalse(controls.toInputFrame(0, 2, frame(turret = 0)).let { it.aimLeft || it.aimRight })
        controls.mouse.pointAt(203, 202)
        assertFalse(controls.toInputFrame(0, 3, frame(turret = 4)).let { it.aimLeft || it.aimRight }, "inside the dead zone")
    }

    @Test
    fun `the buttons fire and shield, and only while firing is the pointed place sent`() {
        val controls = controls(mouseSeat)
        controls.mouse.pointAt(250, 260)
        val idle = controls.toInputFrame(0, 0, frame())
        assertEquals(-1, idle.pointX, "no marker, no point")
        controls.mouse.primary = true
        controls.mouse.secondary = true
        val firing = controls.toInputFrame(0, 1, frame())
        assertTrue(firing.firePrimary && firing.shield)
        assertEquals(250 to 260, firing.pointX to firing.pointY)
        controls.mouse.away()
        assertEquals(-1, controls.toInputFrame(0, 2, frame()).pointX, "off the view: no point")
    }

    @Test
    fun `a wheel notch switches one weapon, held long enough to count`() {
        val controls = controls(mouseSeat)
        controls.mouse.addWheel(1)
        val frames = (0 until 40).map { controls.toInputFrame(0, it.toLong(), frame()) }
        val held = frames.indices.filter { frames[it].cycleWeaponRight }
        assertEquals(6, held.size, "held for two steps")
        assertEquals(held.first() + 5, held.last(), "in one go")
        assertTrue(frames.none { it.cycleWeaponLeft })
    }

    @Test
    fun `a gamepad is a twin stick in Extended`() {
        val state = PadReading(stickX = 0, stickY = -100, buttons = 1 shl 5, rightX = 90, rightY = 0, leftTrigger = 80, rightTrigger = 0).twinStick()
        assertTrue(state.directStick)
        assertEquals(0 to -100, state.stickX to state.stickY)
        assertEquals(90 to 0, state.aimX to state.aimY)
        assertTrue(state.shield && !state.fire && state.weaponRight && !state.weaponLeft)
        assertTrue(PadReading(0, 0, buttons = 1).twinStick().fire, "A fires too")

        // As player two: drives north with the left stick and aims east with the right one.
        val pads = listOf(PadReading(stickX = 0, stickY = -100, buttons = 0, rightX = 100, rightY = 0))
        val controls = controls(mouseSeat, Seat(keys = null, pad = 0)).apply { this.pads = { pads } }
        val two = controls.toInputFrame(1, 0, frame(seat = 1, body = 4, turret = 0), seat = 1)
        assertTrue(two.forward && two.turnLeft, "turns from east to north and drives")
        assertTrue(two.aimRight, "the turret turns from north to east")
    }

    @Test
    fun `player one keeps their place on the join screen and the second keyboard player joins only when allowed`() {
        val join = JoinState(maxPlayers = 4, first = mouseSeat)
        assertEquals(listOf(mouseSeat), join.seats)
        join.leave()
        assertEquals(listOf(mouseSeat), join.seats, "player one cannot leave")
        val fire = Key(DEFAULT_CONTROLS[1].keys[4])
        assertFalse(join.key(fire, DEFAULT_CONTROLS, allowed = emptySet()))
        assertTrue(join.key(fire, DEFAULT_CONTROLS, allowed = setOf(1)))
        assertEquals(Seat(keys = 1, pad = null), join.seats.last())
        assertTrue(join.pad(0))
        assertEquals(3, join.seats.size)
    }
}

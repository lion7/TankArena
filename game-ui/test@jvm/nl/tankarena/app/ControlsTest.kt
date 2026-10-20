package nl.tankarena.app

import androidx.compose.ui.input.key.Key
import nl.tankarena.input.ControlMapping
import nl.tankarena.input.Heading
import nl.tankarena.input.InputState
import nl.tankarena.input.PadReading
import nl.tankarena.ui.compose.menu.ControlAction
import nl.tankarena.ui.compose.menu.ControlDevice
import nl.tankarena.ui.compose.menu.ControlMode
import nl.tankarena.ui.compose.menu.DEFAULT_CONTROLS
import nl.tankarena.ui.compose.menu.JoystickAction
import nl.tankarena.ui.compose.menu.PlayerControls
import nl.tankarena.ui.compose.menu.withJoystickButtons
import nl.tankarena.ui.compose.menu.withKey
import nl.tankarena.ui.compose.menu.withSensitivity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ControlsTest {
    private fun keys(up: Boolean = false, down: Boolean = false, left: Boolean = false, right: Boolean = false, aim: Boolean = false) =
        InputState(up = up, down = down, left = left, right = right, aimLeft = aim)

    private fun direct(state: InputState, body: Int, turret: Int) =
        ControlMapping.frame(state, 0, 0, ControlMode.DIRECT, Heading(body, turret))

    private fun relative(state: InputState) = ControlMapping.frame(state, 0, 0, ControlMode.RELATIVE)

    @Test
    fun `direct mode turns the short way to where the keys point and drives forward`() {
        // Facing north (0), the right key points east (4): turn right, drive on.
        val east = direct(keys(right = true), body = 0, turret = 0)
        assertTrue(east.turnRight && !east.turnLeft && east.forward && !east.reverse)
        // Facing east, down points south (8): turn right; facing west (12) it is left round.
        assertTrue(direct(keys(down = true), 4, 4).turnRight)
        assertTrue(direct(keys(down = true), 12, 12).turnLeft)
        // Facing the way the keys point: no turn, forward — never in reverse.
        val ahead = direct(keys(down = true), 8, 8)
        assertTrue(ahead.forward && !ahead.reverse && !ahead.turnLeft && !ahead.turnRight)
        // Nothing pressed: stop.
        assertFalse(direct(keys(), 3, 3).forward)
        // Legacy: right wins over left, up over down.
        assertEquals(direct(keys(right = true), 0, 0), direct(keys(left = true, right = true), 0, 0))
    }

    @Test
    fun `with an aim key the direction keys turn the turret`() {
        val aimed = direct(keys(right = true, aim = true), body = 0, turret = 0)
        assertTrue(aimed.aimRight && !aimed.turnRight && !aimed.forward)
    }

    @Test
    fun `relative mode reads a stick past its dead zone like the keys`() {
        val turn = relative(InputState(stickX = -80, stickY = -70))
        assertTrue(turn.turnLeft && turn.forward && !turn.turnRight)
        val still = relative(InputState(stickX = 40, stickY = -40))
        assertFalse(still.turnRight || still.forward, "inside the dead zone of 50")
        assertTrue(relative(InputState(stickX = 90, aimHeld = true)).let { it.aimRight && !it.turnRight })
        assertTrue(relative(InputState(stickX = -90, weaponHeld = true)).let { it.cycleWeaponLeft && !it.turnLeft })
    }

    @Test
    fun `direct mode steers to the stick's angle in sixteen directions`() {
        // South-south-east (7) from facing south-east (6): a small turn right, driving on.
        val x = (100 * kotlin.math.sin(Math.toRadians(157.5))).toInt()
        val y = (-100 * kotlin.math.cos(Math.toRadians(157.5))).toInt()
        val frame = direct(InputState(stickX = x, stickY = y), body = 6, turret = 0)
        assertTrue(frame.turnRight && frame.forward)
        assertFalse(direct(InputState(stickX = x, stickY = y), body = 7, turret = 0).turnRight, "already facing it")
        assertFalse(direct(InputState(stickX = 20, stickY = 20), body = 0, turret = 0).forward, "the dead zone stops")
    }

    @Test
    fun `a key given to one slot leaves every other slot of both players`() {
        val changed = DEFAULT_CONTROLS.withKey(1, ControlAction.FIRE, Key.K)
        assertEquals(Key.K, changed[1].key(ControlAction.FIRE))
        assertNull(changed[0].key(ControlAction.UP), "K was player one's UP")
        assertNull(changed.withKey(0, ControlAction.SHIELD, null)[0].key(ControlAction.SHIELD))
    }

    @Test
    fun `a gamepad is a joystick with the original's four buttons`() {
        val controls = DEFAULT_CONTROLS[1].copy(device = ControlDevice.JOYSTICK)
        val fire = PadReading(0, 0, buttons = 1).inputState(controls)
        assertTrue(fire.fire && !fire.weaponHeld)
        // The weapon button (X) stops the fire button and turns the stick into weapon left/right.
        val weapon = PadReading(-90, 0, buttons = 1 or 4).inputState(controls)
        assertTrue(!weapon.fire && weapon.weaponHeld)
        assertTrue(relative(weapon).cycleWeaponLeft)
        // A mask of two buttons needs both.
        val together = controls.copy(joystickButtons = listOf(1 or 2, 0, 0, 0))
        assertFalse(PadReading(0, 0, buttons = 1).inputState(together).fire)
        assertTrue(PadReading(0, 0, buttons = 3).inputState(together).fire)
    }

    @Test
    fun `joystick buttons and sensitivity are set as in the original setup`() {
        val changed = DEFAULT_CONTROLS.withJoystickButtons(0, JoystickAction.SHIELD, 1)
        assertEquals(1, changed[0].buttons(JoystickAction.SHIELD))
        assertEquals(0, changed[0].buttons(JoystickAction.FIRE), "button A was player one's FIRE")
        assertEquals(0, changed[1].buttons(JoystickAction.FIRE), "and player two's")
        assertEquals(100, DEFAULT_CONTROLS.withSensitivity(0, 120)[0].sensitivity)
    }

    @Test
    fun `settings saved before gamepads still load`() {
        val old = """{"mode":"DIRECT","keys":[1,2,3,4,5,6,7,8,9,10]}"""
        val controls = kotlinx.serialization.json.Json.decodeFromString(PlayerControls.serializer(), old)
        assertEquals(ControlDevice.KEYBOARD, controls.device)
        assertEquals(PlayerControls.DEFAULT_JOYSTICK_BUTTONS, controls.joystickButtons)
    }
}

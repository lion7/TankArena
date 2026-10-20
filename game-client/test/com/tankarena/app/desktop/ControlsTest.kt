package com.tankarena.app.desktop

import androidx.compose.ui.input.key.Key
import com.tankarena.input.ControlMapping
import com.tankarena.input.Heading
import com.tankarena.input.InputState
import com.tankarena.ui.compose.menu.ControlMode
import com.tankarena.ui.compose.menu.ControlAction
import com.tankarena.ui.compose.menu.DEFAULT_CONTROLS
import com.tankarena.ui.compose.menu.withKey
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
}

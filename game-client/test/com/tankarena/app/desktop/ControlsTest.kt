package com.tankarena.app.desktop

import androidx.compose.ui.input.key.Key
import com.tankarena.protocol.InputFrame
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
        InputFrame(playerId = 0, inputSequence = 0, forward = up, reverse = down, turnLeft = left, turnRight = right, aimLeft = aim)

    @Test
    fun `direct mode turns the short way to where the keys point and drives forward`() {
        // Facing north (0), the right key points east (4): turn right, drive on.
        val east = directControls(keys(right = true), bodyDirection = 0, turretDirection = 0)
        assertTrue(east.turnRight && !east.turnLeft && east.forward && !east.reverse)
        // Facing east, down points south (8): turn right; facing west (12) it is left round.
        assertTrue(directControls(keys(down = true), 4, 4).turnRight)
        assertTrue(directControls(keys(down = true), 12, 12).turnLeft)
        // Facing the way the keys point: no turn, forward — never in reverse.
        val ahead = directControls(keys(down = true), 8, 8)
        assertTrue(ahead.forward && !ahead.reverse && !ahead.turnLeft && !ahead.turnRight)
        // Nothing pressed: stop.
        assertFalse(directControls(keys(), 3, 3).forward)
    }

    @Test
    fun `with an aim key the direction keys turn the turret`() {
        val aimed = directControls(keys(right = true, aim = true), bodyDirection = 0, turretDirection = 0)
        assertTrue(aimed.aimRight && !aimed.turnRight && !aimed.forward)
    }

    @Test
    fun `a key given to one slot leaves every other slot of both players`() {
        val changed = DEFAULT_CONTROLS.withKey(1, ControlAction.FIRE, Key.K)
        assertEquals(Key.K, changed[1].key(ControlAction.FIRE))
        assertNull(changed[0].key(ControlAction.UP), "K was player one's UP")
        assertNull(changed.withKey(0, ControlAction.SHIELD, null)[0].key(ControlAction.SHIELD))
    }
}

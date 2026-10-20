package nl.tankarena.input

import nl.tankarena.ui.compose.menu.JoystickAction
import nl.tankarena.ui.compose.menu.PlayerControls

/** One gamepad at one moment: the left stick (or the D-pad) from -100 to 100, up and left negative, and its buttons as a mask in [nl.tankarena.ui.compose.menu.PAD_BUTTONS] order. */
data class PadReading(val stickX: Int, val stickY: Int, val buttons: Int) {
    /** Legacy but_pressed(): every button of [mask] held (an empty mask is never pressed). */
    fun pressed(mask: Int): Boolean = mask != 0 && buttons and mask == mask

    /** The pad as a player's joystick (legacy read_controls()): the weapon button stops the fire button. */
    fun inputState(controls: PlayerControls): InputState {
        val weapon = pressed(controls.buttons(JoystickAction.WEAPON))
        return InputState(
            stickX = stickX,
            stickY = stickY,
            aimHeld = pressed(controls.buttons(JoystickAction.AIM)),
            weaponHeld = weapon,
            shield = pressed(controls.buttons(JoystickAction.SHIELD)),
            fire = pressed(controls.buttons(JoystickAction.FIRE)) && !weapon,
        )
    }
}

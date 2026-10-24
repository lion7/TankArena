package nl.tankarena.input

import nl.tankarena.ui.compose.menu.JoystickAction
import nl.tankarena.ui.compose.menu.PlayerControls

/**
 * One gamepad at one moment: the left stick (or the D-pad) from -100 to 100, up and left negative,
 * its buttons as a mask in [nl.tankarena.ui.compose.menu.PAD_BUTTONS] order, the right stick the same
 * way and the triggers from 0 to 100.
 */
data class PadReading(
    val stickX: Int,
    val stickY: Int,
    val buttons: Int,
    val rightX: Int = 0,
    val rightY: Int = 0,
    val leftTrigger: Int = 0,
    val rightTrigger: Int = 0,
) {
    /** Legacy but_pressed(): every button of [mask] held (an empty mask is never pressed). */
    fun pressed(mask: Int): Boolean = mask != 0 && buttons and mask == mask

    /**
     * Extended's twin sticks: the left stick drives the way it points, the right stick turns the turret
     * that way; the right trigger (or A) fires, the left trigger (or X) shields, LB and RB switch
     * weapons, Y takes the next friendly vehicle.
     */
    fun twinStick(): InputState = InputState(
        stickX = stickX,
        stickY = stickY,
        directStick = true,
        aimX = rightX,
        aimY = rightY,
        fire = rightTrigger > TRIGGER_PRESSED || pressed(BUTTON_A),
        shield = leftTrigger > TRIGGER_PRESSED || pressed(BUTTON_X),
        weaponLeft = pressed(BUTTON_LB),
        weaponRight = pressed(BUTTON_RB),
        nextTank = pressed(BUTTON_Y),
    )

    /**
     * The pad as a player's joystick (legacy read_controls()): the weapon button stops the fire button.
     * Back takes the next friendly vehicle (Extended only).
     */
    fun inputState(controls: PlayerControls): InputState {
        val weapon = pressed(controls.buttons(JoystickAction.WEAPON))
        return InputState(
            stickX = stickX,
            stickY = stickY,
            aimHeld = pressed(controls.buttons(JoystickAction.AIM)),
            weaponHeld = weapon,
            shield = pressed(controls.buttons(JoystickAction.SHIELD)),
            fire = pressed(controls.buttons(JoystickAction.FIRE)) && !weapon,
            nextTank = pressed(BUTTON_BACK),
        )
    }
}

/** A trigger counts as pressed past halfway. */
private const val TRIGGER_PRESSED = 50

/** Buttons in [nl.tankarena.ui.compose.menu.PAD_BUTTONS] order. */
private const val BUTTON_A = 1
private const val BUTTON_X = 1 shl 2
private const val BUTTON_Y = 1 shl 3
private const val BUTTON_LB = 1 shl 4
private const val BUTTON_RB = 1 shl 5
private const val BUTTON_BACK = 1 shl 6

package com.tankarena.input

import com.studiohartman.jamepad.ControllerManager
import com.studiohartman.jamepad.ControllerState
import com.tankarena.ui.compose.menu.JoystickAction
import com.tankarena.ui.compose.menu.PlayerControls
import kotlin.math.roundToInt

/** One gamepad at one moment: the left stick (or the D-pad) from -100 to 100, up and left negative, and its buttons as a mask in [com.tankarena.ui.compose.menu.PAD_BUTTONS] order. */
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

/**
 * The connected gamepads through Jamepad (SDL2's game controllers, ADR 0026): Xbox, PlayStation,
 * Switch and other pads, wired or Bluetooth. Without the native library there are simply none.
 */
object Gamepads {
    private var manager: ControllerManager? = null
    private var started = false

    /** Up to four pads, in the order SDL numbers them; disconnected ones are left out. */
    @Synchronized
    fun read(): List<PadReading> {
        val pads = start() ?: return emptyList()
        return runCatching {
            pads.update()
            (0 until pads.numControllers).mapNotNull { index -> pads.getState(index).takeIf { it.isConnected }?.let(::reading) }
        }.getOrElse { emptyList() }
    }

    private fun start(): ControllerManager? {
        if (!started) {
            started = true
            manager = runCatching { ControllerManager().also { it.initSDLGamepad() } }
                .onFailure { System.err.println("No gamepads: ${it.message}") }
                .getOrNull()
        }
        return manager
    }

    private fun reading(state: ControllerState): PadReading {
        fun axis(value: Float) = (value * 100).roundToInt().coerceIn(-100, 100)
        val buttons = listOf(state.a, state.b, state.x, state.y, state.lb, state.rb, state.back, state.start)
            .foldIndexed(0) { bit, mask, held -> if (held) mask or (1 shl bit) else mask }
        // The D-pad pushes the stick all the way (Jamepad's sticks have up positive).
        val x = when {
            state.dpadLeft -> -100
            state.dpadRight -> 100
            else -> axis(state.leftStickX)
        }
        val y = when {
            state.dpadUp -> -100
            state.dpadDown -> 100
            else -> -axis(state.leftStickY)
        }
        return PadReading(x, y, buttons)
    }
}

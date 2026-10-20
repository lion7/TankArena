package nl.tankarena.input

import com.studiohartman.jamepad.ControllerManager
import com.studiohartman.jamepad.ControllerState
import kotlin.math.roundToInt

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
        fun trigger(value: Float) = (value * 100).roundToInt().coerceIn(0, 100)
        return PadReading(
            x, y, buttons,
            rightX = axis(state.rightStickX),
            rightY = -axis(state.rightStickY),
            leftTrigger = trigger(state.leftTrigger),
            rightTrigger = trigger(state.rightTrigger),
        )
    }
}

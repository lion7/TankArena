package nl.tankarena.input

import java.awt.event.KeyEvent

/**
 * Menus with a gamepad (ADR 0026): outside a game, every pad works the menus as the keyboard does.
 * The stick or D-pad give the arrow keys (held: again after [FIRST_REPEAT_NANOS], then every
 * [REPEAT_NANOS]), A and START give ENTER, B and BACK give ESC, LB and RB PAGE UP and PAGE DOWN.
 * [step] returns the AWT key codes to press this frame.
 */
class GamepadMenuKeys {
    private var direction = 0
    private var nextRepeat = 0L
    private var lastButtons = 0

    fun step(now: Long, pads: List<PadReading>): List<Int> {
        val keys = mutableListOf<Int>()
        val pushed = pads.firstOrNull { kotlin.math.abs(it.stickX) > DEAD_ZONE || kotlin.math.abs(it.stickY) > DEAD_ZONE }
        val wanted = when {
            pushed == null -> 0
            kotlin.math.abs(pushed.stickY) >= kotlin.math.abs(pushed.stickX) -> if (pushed.stickY < 0) KeyEvent.VK_UP else KeyEvent.VK_DOWN
            else -> if (pushed.stickX < 0) KeyEvent.VK_LEFT else KeyEvent.VK_RIGHT
        }
        if (wanted != direction) {
            direction = wanted
            if (wanted != 0) {
                keys += wanted
                nextRepeat = now + FIRST_REPEAT_NANOS
            }
        } else if (wanted != 0 && now >= nextRepeat) {
            keys += wanted
            nextRepeat = now + REPEAT_NANOS
        }
        val buttons = pads.fold(0) { mask, pad -> mask or pad.buttons }
        val pressed = buttons and lastButtons.inv()
        lastButtons = buttons
        for ((bit, key) in BUTTON_KEYS) if (pressed and (1 shl bit) != 0) keys += key
        return keys
    }

    companion object {
        private const val DEAD_ZONE = 50
        const val FIRST_REPEAT_NANOS = 400_000_000L
        const val REPEAT_NANOS = 110_000_000L

        /** Pad button bits (A B X Y LB RB BACK START) and the keys they give. */
        private val BUTTON_KEYS = listOf(
            0 to KeyEvent.VK_ENTER,
            1 to KeyEvent.VK_ESCAPE,
            4 to KeyEvent.VK_PAGE_UP,
            5 to KeyEvent.VK_PAGE_DOWN,
            6 to KeyEvent.VK_ESCAPE,
            7 to KeyEvent.VK_ENTER,
        )
    }
}

package nl.tankarena.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import nl.tankarena.input.hiddenPointerIcon
import java.util.concurrent.atomic.AtomicInteger

/**
 * Extended's player one's mouse (keyboard and mouse controls): where it points on the map, in world
 * pixels (-1 when it is off the player's view), its buttons and the wheel's notches not yet used.
 * Written by the player's view on the UI thread, read by the simulation thread.
 */
class MousePointer {
    /** Where the cursor is in the window, or null when it is not over the game (drawn as a crosshair). */
    var cursor: Offset? by mutableStateOf(null)

    /** The player's map window in the window, once laid out: the touch controls go beside it. */
    var view: Rect? by mutableStateOf(null)

    @Volatile
    var worldX: Int = -1
        private set

    @Volatile
    var worldY: Int = -1
        private set

    /** Left button: fire. */
    @Volatile
    var primary: Boolean = false

    /** Right button: shield. */
    @Volatile
    var secondary: Boolean = false

    private val wheel = AtomicInteger()

    val pointing: Boolean get() = worldX >= 0 && worldY >= 0

    fun pointAt(x: Int, y: Int) {
        worldX = x
        worldY = y
    }

    fun away() = pointAt(-1, -1)

    /** The wheel turned [notches] (positive: towards the player, the next weapon). */
    fun addWheel(notches: Int) {
        wheel.addAndGet(notches)
    }

    /** One notch to use now: -1, 0 or 1. */
    fun takeWheel(): Int {
        while (true) {
            val now = wheel.get()
            if (now == 0) return 0
            val step = if (now > 0) 1 else -1
            if (wheel.compareAndSet(now, now - step)) return step
        }
    }

    fun reset() {
        cursor = null
        away()
        primary = false
        secondary = false
        wheel.set(0)
    }
}

/**
 * The game screen's mouse, for [mouse]: it sees the events first (before the touch controls on
 * top), leaves them to the others, and hides the system cursor; the player's view turns the cursor
 * into a map position and draws a crosshair.
 */
fun Modifier.mouseInput(mouse: MousePointer): Modifier {
    var origin = Offset.Zero
    return onGloballyPositioned { origin = it.positionInRoot() }
        .pointerHoverIcon(hiddenPointerIcon())
        .pointerInput(mouse) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull() ?: continue
                    if (change.type != PointerType.Mouse) continue
                    when (event.type) {
                        PointerEventType.Exit -> mouse.cursor = null
                        PointerEventType.Scroll -> {
                            val notches = change.scrollDelta.y
                            if (notches != 0f) mouse.addWheel(if (notches > 0) 1 else -1)
                        }
                        else -> mouse.cursor = origin + change.position
                    }
                    mouse.primary = event.buttons.isPrimaryPressed
                    mouse.secondary = event.buttons.isSecondaryPressed
                }
            }
        }
}

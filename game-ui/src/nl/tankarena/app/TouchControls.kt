package nl.tankarena.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import nl.tankarena.input.InputState
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Player one's controls on a touch screen, written by [TouchControls] and read with the other
 * devices for every game tick: a move stick (steering directly, as a stick does), an aim stick for
 * the turret, fire, shield and the weapon before and after.
 */
class TouchPad {
    @Volatile private var current: InputState? = null

    /** What the fingers hold now, or null when none is down. */
    fun state(): InputState? = current

    internal fun set(state: InputState?) {
        current = state
    }
}

private enum class TouchRole { MOVE, AIM, FIRE, SHIELD, WEAPON_LEFT, WEAPON_RIGHT, PAUSE, EXIT }

/** A round control on the screen: centre and radius in pixels. */
private data class Spot(val role: TouchRole, val centre: Offset, val radius: Float, val label: String)

/**
 * The virtual controller over the game (Extended's look, cyan rings): the left half of the screen
 * is a move stick wherever the thumb lands, the right has the aim stick, FIRE, SHIELD and the weapon
 * buttons, the top PAUSE and EXIT. Every finger keeps the role of what it first touched.
 */
@Composable
internal fun TouchControls(pad: TouchPad, onPause: () -> Unit, onExit: () -> Unit, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val stick = h * 0.15f
        val margin = h * 0.07f
        val moveHome = Offset(margin + stick, h - margin - stick)
        val aimHome = Offset(w - margin - stick, h - margin - stick)
        val fire = Offset(aimHome.x - stick * 0.2f, aimHome.y - stick - h * 0.15f)
        val spots = remember(w, h) {
            listOf(
                Spot(TouchRole.FIRE, fire, h * 0.09f, "FIRE"),
                Spot(TouchRole.SHIELD, Offset(fire.x - h * 0.23f, fire.y + h * 0.06f), h * 0.07f, "SHIELD"),
                Spot(TouchRole.WEAPON_LEFT, Offset(fire.x - h * 0.2f, fire.y - h * 0.17f), h * 0.055f, "◀"),
                Spot(TouchRole.WEAPON_RIGHT, Offset(fire.x + h * 0.02f, fire.y - h * 0.2f), h * 0.055f, "▶"),
                Spot(TouchRole.PAUSE, Offset(w / 2 - h * 0.09f, h * 0.07f), h * 0.045f, "II"),
                Spot(TouchRole.EXIT, Offset(w / 2 + h * 0.09f, h * 0.07f), h * 0.045f, "✕"),
            )
        }
        // The labels are measured once per screen size, not in every frame.
        val density = LocalDensity.current.density
        val labels = remember(spots, density) {
            spots.map { spot ->
                measurer.measure(spot.label, TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = (spot.radius * 0.45f / density).sp))
            }
        }
        // Per finger: its role and, for a stick, where the stick's centre is.
        val fingers = remember { HashMap<PointerId, Pair<TouchRole, Offset>>() }
        var moveKnob by remember { mutableStateOf<Pair<Offset, Offset>?>(null) }
        var aimKnob by remember { mutableStateOf(Offset.Zero) }
        var held by remember { mutableStateOf(emptySet<TouchRole>()) }

        fun stickValue(from: Offset, to: Offset): Pair<Int, Int> {
            val dx = (to.x - from.x) / stick
            val dy = (to.y - from.y) / stick
            val length = hypot(dx, dy).coerceAtLeast(1f)
            return (dx / length * 100).roundToInt() to (dy / length * 100).roundToInt()
        }

        Canvas(
            modifier = Modifier.fillMaxSize().pointerInput(w, h) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        var move: Pair<Int, Int>? = null
                        var aim: Pair<Int, Int>? = null
                        var knob: Pair<Offset, Offset>? = null
                        var aimAt = Offset.Zero
                        for (change in event.changes) {
                            val id = change.id
                            if (change.pressed && id !in fingers) {
                                val at = change.position
                                val spot = spots.firstOrNull { (it.centre - at).getDistance() <= it.radius * 1.3f }
                                val role = when {
                                    spot != null -> spot.role
                                    (at - aimHome).getDistance() <= stick * 1.6f -> TouchRole.AIM
                                    at.x < w / 2 -> TouchRole.MOVE
                                    else -> null
                                }
                                if (role != null) {
                                    fingers[id] = role to if (role == TouchRole.MOVE) at else aimHome
                                    when (role) {
                                        TouchRole.PAUSE -> onPause()
                                        TouchRole.EXIT -> onExit()
                                        else -> Unit
                                    }
                                }
                            }
                            if (!change.pressed) {
                                fingers.remove(id)
                                continue
                            }
                            val (role, origin) = fingers[id] ?: continue
                            change.consume()
                            when (role) {
                                TouchRole.MOVE -> {
                                    move = stickValue(origin, change.position)
                                    knob = origin to change.position
                                }
                                TouchRole.AIM -> {
                                    aim = stickValue(origin, change.position)
                                    aimAt = change.position - origin
                                }
                                else -> Unit
                            }
                        }
                        val roles = fingers.values.map { it.first }.toSet()
                        held = roles
                        moveKnob = knob
                        aimKnob = aimAt
                        pad.set(
                            if (roles.isEmpty()) {
                                null
                            } else {
                                InputState(
                                    stickX = move?.first ?: 0,
                                    stickY = move?.second ?: 0,
                                    directStick = true,
                                    aimX = aim?.first ?: 0,
                                    aimY = aim?.second ?: 0,
                                    fire = TouchRole.FIRE in roles,
                                    shield = TouchRole.SHIELD in roles,
                                    weaponLeft = TouchRole.WEAPON_LEFT in roles,
                                    weaponRight = TouchRole.WEAPON_RIGHT in roles,
                                )
                            },
                        )
                    }
                }
            },
        ) {
            val ring = Color(0xFF6FE6FF)
            fun circle(centre: Offset, radius: Float, active: Boolean) {
                drawCircle(ring.copy(alpha = if (active) 0.35f else 0.12f), radius, centre)
                drawCircle(ring.copy(alpha = 0.7f), radius, centre, style = Stroke(width = radius * 0.08f))
            }
            // The move stick sits where the thumb went down, or at home.
            val (moveCentre, moveThumb) = moveKnob ?: (moveHome to moveHome)
            circle(moveCentre, stick, moveKnob != null)
            val thumb = moveThumb - moveCentre
            val clamp = if (thumb.getDistance() > stick) thumb * (stick / thumb.getDistance()) else thumb
            circle(moveCentre + clamp, stick * 0.4f, moveKnob != null)
            circle(aimHome, stick, aimKnob != Offset.Zero)
            val aimClamp = if (aimKnob.getDistance() > stick) aimKnob * (stick / aimKnob.getDistance()) else aimKnob
            circle(aimHome + aimClamp, stick * 0.4f, aimKnob != Offset.Zero)
            for (index in spots.indices) {
                val spot = spots[index]
                val layout = labels[index]
                circle(spot.centre, spot.radius, spot.role in held)
                drawText(layout, topLeft = spot.centre - Offset(layout.size.width / 2f, layout.size.height / 2f))
            }
        }
    }
}

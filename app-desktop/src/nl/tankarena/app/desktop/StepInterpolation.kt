package nl.tankarena.app.desktop

import nl.tankarena.protocol.snapshot.ActorState
import nl.tankarena.protocol.snapshot.B52State
import nl.tankarena.protocol.snapshot.FlamePartState
import nl.tankarena.protocol.snapshot.ManState
import nl.tankarena.protocol.snapshot.ProjectileState
import nl.tankarena.protocol.snapshot.ServerFrame
import nl.tankarena.protocol.snapshot.TankState
import nl.tankarena.protocol.snapshot.TrainState
import nl.tankarena.protocol.snapshot.TurretState
import nl.tankarena.protocol.snapshot.ZeppelinState
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Extended's smooth motion: the world changes on every legacy step (33 a second), so the screen shows
 * the moving things — and the cameras — [alpha] (0..1) of the way from [previous] to [next]. Anything
 * that jumped further than [SNAP_PX] (a warp, the map wrap) is shown where it is.
 */
internal fun interpolate(previous: ServerFrame, next: ServerFrame, alpha: Float): ServerFrame {
    if (alpha >= 1f) return next
    val before = previous.world.actors.associateBy { it.actorId }
    fun lerp(a: Int, b: Int): Int = if (abs(b - a) > SNAP_PX) b else (a + (b - a) * alpha).roundToInt()
    fun moved(state: ActorState): ActorState {
        val old = before[state.actorId] ?: return state
        return when {
            state is TankState && old is TankState -> state.copy(x = lerp(old.x, state.x), y = lerp(old.y, state.y))
            state is ProjectileState && old is ProjectileState -> state.copy(x = lerp(old.x, state.x), y = lerp(old.y, state.y))
            state is ManState && old is ManState -> state.copy(x = lerp(old.x, state.x), y = lerp(old.y, state.y))
            state is FlamePartState && old is FlamePartState -> state.copy(x = lerp(old.x, state.x), y = lerp(old.y, state.y))
            state is TrainState && old is TrainState -> state.copy(x = lerp(old.x, state.x), y = lerp(old.y, state.y))
            state is ZeppelinState && old is ZeppelinState -> state.copy(x = lerp(old.x, state.x), y = lerp(old.y, state.y))
            state is B52State && old is B52State -> state.copy(x = lerp(old.x, state.x), y = lerp(old.y, state.y))
            state is TurretState && old is TurretState -> state.copy(x = lerp(old.x, state.x), y = lerp(old.y, state.y))
            else -> state
        }
    }
    val oldViews = previous.playerViews.associateBy { it.playerId }
    return next.copy(
        world = next.world.copy(actors = next.world.actors.map(::moved)),
        playerViews = next.playerViews.map { view ->
            val old = oldViews[view.playerId] ?: return@map view
            view.copy(
                cameraCenterX = lerp(old.cameraCenterX, view.cameraCenterX),
                cameraCenterY = lerp(old.cameraCenterY, view.cameraCenterY),
            )
        },
    )
}

/** Further than this between two steps is a jump, not movement. */
private const val SNAP_PX = 96

/** How long a legacy step takes on the screen. */
internal const val STEP_NANOS = 30_000_000L

/**
 * Keeps the last two worlds the game showed and when the newer one arrived, to draw between them.
 * A new world is one made by the next step.
 */
internal class StepInterpolator {
    private var previous: ServerFrame? = null
    private var current: ServerFrame? = null
    private var arrived = 0L

    fun frame(latest: ServerFrame, now: Long): ServerFrame {
        val shown = current
        // A snapshot's tick is one past the step that made it: steps start at ticks 1, 4, 7, ...
        if (shown == null || (latest.world.tick - 1) / 3 != (shown.world.tick - 1) / 3) {
            previous = shown ?: latest
            current = latest
            arrived = now
        } else {
            current = latest
        }
        val before = previous ?: return latest
        return interpolate(before, latest, ((now - arrived).toFloat() / STEP_NANOS).coerceIn(0f, 1f))
    }
}

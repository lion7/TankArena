package nl.tankarena.app

import androidx.compose.ui.geometry.Offset
import nl.tankarena.protocol.snapshot.LockMarkerState
import nl.tankarena.protocol.snapshot.PlayerView
import kotlin.math.hypot

/** The outer part of the view, per side, where the cursor scrolls the map. */
private const val EDGE_BAND = 0.20f

/** Map pixels a second with the cursor at the very edge. */
private const val EDGE_SPEED = 400f

/** The marker is kept at least this far inside the view. */
private const val MARKER_MARGIN = 16f

/** Legacy LOCK_MAX_RANGE_PX: the camera goes no further from the vehicle than the marker can. */
private const val AIM_RANGE = 450f

/** A frame longer than this counts as this long (a stall must not throw the view across the map). */
private const val MAX_FRAME_SECONDS = 0.05f

/**
 * Extended's keyboard-and-mouse player's view while they aim rockets or soldiers: the sim's camera
 * follows the marker, and the marker follows the cursor, so the view ran away from under the mouse.
 * Instead the view stays where it was and scrolls only while the cursor is near its edge (or to
 * keep the marker in sight when the keys steer it). It ends when the marker is gone.
 */
internal class AimCamera {
    private var camera: Offset? = null
    private var lastNanos = 0L

    /**
     * The camera centre (map pixels) for [view] now, or null for the sim's own. [cursor] is the
     * cursor relative to the view's centre in map pixels, null when it is not over the view; the view
     * shows [halfWidth] × [halfHeight] map pixels each way of its centre of a [worldWidth] ×
     * [worldHeight] map.
     */
    fun follow(
        view: PlayerView,
        cursor: Offset?,
        halfWidth: Float,
        halfHeight: Float,
        worldWidth: Int,
        worldHeight: Int,
        nanos: Long,
    ): Offset? {
        val marker = view.lockMarker
        val dt = ((nanos - lastNanos) / 1e9f).coerceIn(0f, MAX_FRAME_SECONDS)
        lastNanos = nanos
        if (marker == null) {
            camera = null
            return null
        }
        // Aiming starts from what the player saw: their vehicle.
        var at = camera ?: if (marker.state != LockMarkerState.LAUNCHED) {
            Offset(view.viewerX.toFloat(), view.viewerY.toFloat())
        } else {
            return null
        }
        // After the launch the view stays where it was aimed until the sim's camera takes over again.
        if (marker.state != LockMarkerState.LAUNCHED) {
            if (cursor != null) at += Offset(edgeSpeed(cursor.x, halfWidth), edgeSpeed(cursor.y, halfHeight)) * (EDGE_SPEED * dt)
            at = Offset(
                keepInside(at.x, marker.x.toFloat(), halfWidth),
                keepInside(at.y, marker.y.toFloat(), halfHeight),
            )
            val dx = at.x - view.viewerX
            val dy = at.y - view.viewerY
            val distance = hypot(dx, dy)
            if (distance > AIM_RANGE) at = Offset(view.viewerX + dx * AIM_RANGE / distance, view.viewerY + dy * AIM_RANGE / distance)
            at = Offset(clampToMap(at.x, halfWidth, worldWidth), clampToMap(at.y, halfHeight, worldHeight))
        }
        camera = at
        return at
    }

    /** -1..1: how far into the edge band [offset] (from the centre, [half] each way) is. */
    private fun edgeSpeed(offset: Float, half: Float): Float {
        val band = 2 * half * EDGE_BAND
        if (band <= 0f) return 0f
        return when {
            offset < -half + band -> -((-half + band - offset) / band).coerceAtMost(1f)
            offset > half - band -> ((offset - (half - band)) / band).coerceAtMost(1f)
            else -> 0f
        }
    }

    /** The camera [at] moved just enough that [marker] is inside the view. */
    private fun keepInside(at: Float, marker: Float, half: Float): Float {
        val reach = (half - MARKER_MARGIN).coerceAtLeast(0f)
        return at.coerceIn(marker - reach, marker + reach)
    }

    /** Legacy windows stop at the map's edges; the camera does too, so scrolling back is immediate. */
    private fun clampToMap(at: Float, half: Float, world: Int): Float =
        if (world <= 2 * half) world / 2f else at.coerceIn(half, world - half)
}

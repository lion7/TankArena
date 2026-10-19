package com.tankarena.render

import com.tankarena.protocol.snapshot.GameEvent

/**
 * Buffers per-tick [GameEvent]s between the 100 Hz match loop and the viewport. Compose may
 * coalesce several server frames into one recomposition; without this buffer the events of
 * the skipped frames (explosions, destroyed tanks) would never reach the renderer.
 */
class ViewportEventQueue {
    private val pending = ArrayList<GameEvent>()

    @Synchronized
    fun push(events: List<GameEvent>) {
        if (events.isNotEmpty()) pending.addAll(events)
    }

    @Synchronized
    fun drain(): List<GameEvent> {
        if (pending.isEmpty()) return emptyList()
        return pending.toList().also { pending.clear() }
    }
}

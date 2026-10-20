package nl.tankarena.net

import kotlinx.serialization.Serializable
import nl.tankarena.protocol.snapshot.ActorState
import nl.tankarena.protocol.snapshot.GameEvent
import nl.tankarena.protocol.snapshot.PROTOCOL_VERSION
import nl.tankarena.protocol.snapshot.PlayerView
import nl.tankarena.protocol.snapshot.ServerFrame
import nl.tankarena.protocol.snapshot.TileState
import nl.tankarena.protocol.snapshot.WorldSnapshot

/**
 * One step as the difference with the frame sent before it to the same machine (ADR 0024): the
 * actors that are new or changed, the ids of those gone, the map tiles changed since, and what is
 * always new (events, views, flash). The connection delivers every frame in order, so the previous
 * one is always there.
 */
@Serializable
data class DeltaFrame(
    val tick: Long,
    val changed: List<ActorState> = emptyList(),
    val removed: List<Long> = emptyList(),
    /** The actors' order, only when it is not the old order with the new ones at the end. */
    val order: List<Long>? = null,
    val tiles: List<TileState> = emptyList(),
    val events: List<GameEvent> = emptyList(),
    val flash: Int = 0,
    val playerViews: List<PlayerView> = emptyList(),
)

/** The server's side: what one machine was sent last, to make the next frame a delta of it. */
internal class DeltaEncoder {
    private var actors: LinkedHashMap<Long, ActorState>? = null
    private val tiles = HashMap<Int, TileState>()

    /** The next frame must be whole (at the start, or after one was dropped). */
    fun reset() {
        actors = null
        tiles.clear()
    }

    /** [frame] as this machine gets it next: whole when there is nothing to compare with. */
    fun encode(frame: ServerFrame): ServerMessage {
        val previous = actors
        val next = LinkedHashMap<Long, ActorState>(frame.world.actors.size * 2)
        for (actor in frame.world.actors) next[actor.actorId] = actor
        actors = next
        val newTiles = frame.world.changedTiles.filter { tiles[it.index] != it }
        for (tile in newTiles) tiles[tile.index] = tile
        if (previous == null) return ServerMessage.Frame(frame)
        val changed = frame.world.actors.filter { previous[it.actorId] != it }
        val removed = previous.keys.filter { it !in next }
        val expected = previous.keys.filter { it in next } + frame.world.actors.map { it.actorId }.filter { it !in previous }
        val order = frame.world.actors.map { it.actorId }.takeIf { it != expected }
        return ServerMessage.Delta(
            DeltaFrame(frame.tick, changed, removed, order, newTiles, frame.world.events, frame.world.flash, frame.playerViews),
        )
    }
}

/** The client's side: the frames as they were on the server, rebuilt from whole frames and deltas. */
internal class DeltaDecoder {
    private var actors = LinkedHashMap<Long, ActorState>()
    private val tiles = LinkedHashMap<Int, TileState>()

    fun whole(frame: ServerFrame): ServerFrame {
        actors = LinkedHashMap<Long, ActorState>(frame.world.actors.size * 2).apply {
            for (actor in frame.world.actors) put(actor.actorId, actor)
        }
        tiles.clear()
        for (tile in frame.world.changedTiles) tiles[tile.index] = tile
        return frame
    }

    fun apply(delta: DeltaFrame): ServerFrame {
        for (id in delta.removed) actors.remove(id)
        for (actor in delta.changed) actors[actor.actorId] = actor
        delta.order?.let { order ->
            val reordered = LinkedHashMap<Long, ActorState>(order.size * 2)
            for (id in order) actors[id]?.let { reordered[id] = it }
            actors = reordered
        }
        for (tile in delta.tiles) tiles[tile.index] = tile
        val world = WorldSnapshot(
            tick = delta.tick,
            actors = actors.values.toList(),
            events = delta.events,
            changedTiles = tiles.values.toList(),
            flash = delta.flash,
            protocolVersion = PROTOCOL_VERSION,
        )
        return ServerFrame(delta.tick, world, delta.playerViews)
    }
}

package com.tankarena.sim.map

import com.tankarena.content.MapMetadata
import com.tankarena.content.PictureCatalog
import com.tankarena.content.TankArenaWorld
import kotlinx.serialization.json.JsonObject

/** The three picture layers of a map (legacy `mapm.m[0..2]`). */
enum class MapLayer { FLOOR, SOLID, TOP }

/**
 * A map being edited (ADR 0021): its picture layers can be painted, every stroke can be undone and
 * redone, and [toDocument] gives the map to save or play. Headers, the goal, bonus and men layers and
 * the objects are kept as they were loaded; editing them comes later.
 */
class MapEditor(document: MapDocument) {
    private val sidecar = MapConversion.sidecarOf(document)
    private val records: List<JsonObject> = document.objects
    private val layers: Map<MapLayer, IntArray>

    val metadata: MapMetadata get() = sidecar.metadata
    val width: Int get() = metadata.widthTiles
    val height: Int get() = metadata.heightTiles
    val world: TankArenaWorld get() = metadata.world

    /** Number of pictures of the map's world; a cell holds `-1` (none) or `0 until pictureCount`. */
    val pictureCount: Int = PictureCatalog.pictureCount(world)

    private data class Change(val layer: MapLayer, val index: Int, val before: Int, val after: Int)

    private val undoStack = ArrayDeque<List<Change>>()
    private val redoStack = ArrayDeque<List<Change>>()
    private var stroke: MutableList<Change>? = null

    /** Strokes made since the map was loaded or last [markSaved]. */
    var modified: Boolean = false
        private set

    init {
        val tiles = requireNotNull(sidecar.tileLayers) { "${metadata.name} has no picture layers" }
        layers = mapOf(
            MapLayer.FLOOR to tiles.base.toIntArray(),
            MapLayer.SOLID to tiles.solid.toIntArray(),
            MapLayer.TOP to tiles.top.toIntArray(),
        )
    }

    fun picture(layer: MapLayer, x: Int, y: Int): Int = layers.getValue(layer)[x + y * width]

    /** The whole layer, row by row (for drawing). */
    fun layer(layer: MapLayer): IntArray = layers.getValue(layer).copyOf()

    fun beginStroke() {
        endStroke()
        stroke = mutableListOf()
    }

    /**
     * Puts [picture] (-1 erases) on a cell; returns false outside the map, for an unknown picture or
     * when the cell already holds it. Outside a stroke every paint is its own undo step.
     */
    fun paint(layer: MapLayer, x: Int, y: Int, picture: Int): Boolean {
        if (x !in 0 until width || y !in 0 until height) return false
        require(picture in -1 until pictureCount) { "picture $picture is not in the ${world.name} table (0..${pictureCount - 1})" }
        val cells = layers.getValue(layer)
        val index = x + y * width
        if (cells[index] == picture) return false
        val change = Change(layer, index, cells[index], picture)
        cells[index] = picture
        redoStack.clear()
        modified = true
        val open = stroke
        if (open != null) open += change else undoStack.addLast(listOf(change))
        return true
    }

    fun endStroke() {
        val open = stroke ?: return
        stroke = null
        if (open.isNotEmpty()) undoStack.addLast(open)
    }

    val canUndo: Boolean get() = undoStack.isNotEmpty() || !stroke.isNullOrEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun undo(): Boolean {
        endStroke()
        val changes = undoStack.removeLastOrNull() ?: return false
        for (change in changes.asReversed()) layers.getValue(change.layer)[change.index] = change.before
        redoStack.addLast(changes)
        modified = true
        return true
    }

    fun redo(): Boolean {
        endStroke()
        val changes = redoStack.removeLastOrNull() ?: return false
        for (change in changes) layers.getValue(change.layer)[change.index] = change.after
        undoStack.addLast(changes)
        modified = true
        return true
    }

    fun markSaved() {
        modified = false
    }

    fun toDocument(): MapDocument {
        endStroke()
        val tiles = requireNotNull(sidecar.tileLayers).copy(
            base = layers.getValue(MapLayer.FLOOR).toList(),
            solid = layers.getValue(MapLayer.SOLID).toList(),
            top = layers.getValue(MapLayer.TOP).toList(),
        )
        return MapConversion.documentOf(sidecar.copy(tileLayers = tiles), records)
    }
}

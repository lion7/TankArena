package com.tankarena.sim.map

import com.tankarena.content.MapMetadata
import com.tankarena.content.MapSceneSidecar
import com.tankarena.content.PictureCatalog
import com.tankarena.content.Structure
import com.tankarena.content.TankArenaWorld
import com.tankarena.content.TileLayers
import kotlinx.serialization.json.JsonObject

/** The three picture layers of a map (legacy `mapm.m[0..2]`). */
enum class MapLayer { FLOOR, SOLID, TOP }

/** How a new map starts (the original's "standard map" choice). */
enum class NewMapStart {
    EMPTY,

    /** Barriers all round, on the solid and top layers. */
    BORDER,

    /** The floor all water (all pit in the city world). */
    WATER,
}

/** The paratroopers' weapons a building can hold (legacy HT_CHAIN, HT_FLAME, HT_SROCK). */
enum class MenWeapon(val legacyType: Int) { CHAIN_GUN(1), FLAME_THROWER(2), SMALL_ROCKETS(10) }

/**
 * A map being edited (ADR 0021): its picture layers, structures, and goal, bonus and men layers can be
 * changed, every stroke can be undone and redone, and [toDocument] gives the map to save or play. The
 * header and the objects are kept as they were loaded; editing them comes later.
 */
/** [unsaved]: a new map, not yet saved anywhere. */
class MapEditor(document: MapDocument, unsaved: Boolean = false) {
    private val sidecar = MapConversion.sidecarOf(document)
    private val records: List<JsonObject> = document.objects

    /** Every per-cell layer: the three picture layers first, in [MapLayer] order. */
    private enum class Grid { FLOOR, SOLID, TOP, GOAL, BONUS, MAN_TYPE, MAN_COUNT }

    private val grids: Map<Grid, IntArray>

    val metadata: MapMetadata get() = sidecar.metadata
    val width: Int get() = metadata.widthTiles
    val height: Int get() = metadata.heightTiles
    val world: TankArenaWorld get() = metadata.world

    /** Number of pictures of the map's world; a cell holds `-1` (none) or `0 until pictureCount`. */
    val pictureCount: Int = PictureCatalog.pictureCount(world)

    private data class Change(val grid: Grid, val index: Int, val before: Int, val after: Int)

    private val undoStack = ArrayDeque<List<Change>>()
    private val redoStack = ArrayDeque<List<Change>>()
    private var stroke: MutableList<Change>? = null

    /** Strokes made since the map was loaded or last [markSaved]. */
    var modified: Boolean = unsaved
        private set

    init {
        val tiles = requireNotNull(sidecar.tileLayers) { "${metadata.name} has no picture layers" }
        grids = mapOf(
            Grid.FLOOR to tiles.base.toIntArray(),
            Grid.SOLID to tiles.solid.toIntArray(),
            Grid.TOP to tiles.top.toIntArray(),
            Grid.GOAL to tiles.goalLayer.toIntArray(),
            Grid.BONUS to tiles.bonusLayer.toIntArray(),
            Grid.MAN_TYPE to tiles.manTypeLayer.toIntArray(),
            Grid.MAN_COUNT to tiles.manAmountLayer.toIntArray(),
        )
    }

    private val MapLayer.grid: Grid get() = Grid.entries[ordinal]

    private fun get(grid: Grid, x: Int, y: Int): Int = grids.getValue(grid)[x + y * width]

    /** Changes one cell of one layer as part of the open stroke; false when it already held [value]. */
    private fun set(grid: Grid, x: Int, y: Int, value: Int): Boolean {
        val cells = grids.getValue(grid)
        val index = x + y * width
        if (cells[index] == value) return false
        val change = Change(grid, index, cells[index], value)
        cells[index] = value
        redoStack.clear()
        modified = true
        val open = stroke
        if (open != null) open += change else undoStack.addLast(listOf(change))
        return true
    }

    private fun inside(x: Int, y: Int) = x in 0 until width && y in 0 until height

    fun picture(layer: MapLayer, x: Int, y: Int): Int = get(layer.grid, x, y)

    /** The whole layer, row by row (for drawing). */
    fun layer(layer: MapLayer): IntArray = grids.getValue(layer.grid).copyOf()

    fun beginStroke() {
        endStroke()
        stroke = mutableListOf()
    }

    /**
     * Puts [picture] (-1 erases) on a cell; returns false outside the map, for an unknown picture or
     * when the cell already holds it. Outside a stroke every paint is its own undo step.
     */
    fun paint(layer: MapLayer, x: Int, y: Int, picture: Int): Boolean {
        if (!inside(x, y)) return false
        require(picture in -1 until pictureCount) { "picture $picture is not in the ${world.name} table (0..${pictureCount - 1})" }
        return set(layer.grid, x, y, picture)
    }

    /** Legacy `mapm.g`: the share of the goal counter destroying the building on a cell gives, -100..100. */
    fun goal(x: Int, y: Int): Int = get(Grid.GOAL, x, y)

    /** Adds [by] to a cell's goal share, kept within -100..100 (legacy `increase_goal1/10()`). */
    fun changeGoal(x: Int, y: Int, by: Int): Boolean = inside(x, y) && set(Grid.GOAL, x, y, (goal(x, y) + by).coerceIn(-100, 100))

    /** Legacy `mapm.b`: the bonus (index in [BONUS_NAMES]) a cell leaves when its building goes, or null. */
    fun bonus(x: Int, y: Int): Int? = get(Grid.BONUS, x, y).takeIf { it != MapConversion.NO_BONUS }

    fun setBonus(x: Int, y: Int, bonus: Int?): Boolean {
        require(bonus == null || bonus in BONUS_NAMES.indices) { "no bonus $bonus" }
        return inside(x, y) && set(Grid.BONUS, x, y, bonus ?: MapConversion.NO_BONUS)
    }

    /** Legacy `mapm.man[0..1]`: the men in a cell's building, or null. */
    fun men(x: Int, y: Int): Pair<MenWeapon, Int>? {
        val count = get(Grid.MAN_COUNT, x, y)
        val weapon = MenWeapon.entries.firstOrNull { it.legacyType == get(Grid.MAN_TYPE, x, y) }
        return if (count > 0 && weapon != null) weapon to count else null
    }

    /**
     * One more man with [weapon] (at most 50) — legacy `incman_fun()`: a building holds one kind, and
     * adding another kind turns the men there into that kind without adding one.
     */
    fun addMan(x: Int, y: Int, weapon: MenWeapon) = step {
        if (!inside(x, y)) return@step
        val count = get(Grid.MAN_COUNT, x, y)
        if ((get(Grid.MAN_TYPE, x, y) == weapon.legacyType || count == 0) && count < 50) set(Grid.MAN_COUNT, x, y, count + 1)
        set(Grid.MAN_TYPE, x, y, weapon.legacyType)
    }

    /** One man fewer (legacy `decman_fun()`). */
    fun removeMan(x: Int, y: Int): Boolean = inside(x, y) && get(Grid.MAN_COUNT, x, y).let { it > 0 && set(Grid.MAN_COUNT, x, y, it - 1) }

    /** Runs [edit] as one undo step, unless a stroke is already open. */
    private inline fun step(edit: () -> Unit) {
        if (stroke != null) return edit()
        beginStroke()
        edit()
        endStroke()
    }

    /**
     * Puts [structure] with its top-left cell on ([x], [y]): every picture it has goes on its layer;
     * cells outside the map are left out (legacy `recursive_add_struct()`).
     */
    fun placeStructure(structure: Structure, x: Int, y: Int) = step {
        for (cell in structure.cells) {
            for (layer in MapLayer.entries) {
                val picture = cell.pictures[layer.ordinal]
                if (picture >= 0 && picture < pictureCount) paint(layer, x + cell.dx, y + cell.dy, picture)
            }
        }
    }

    /**
     * Clears a cell as the original's remove does: a building (solid or top) goes, solid and top
     * together; on a cell without one, the floor goes (legacy `do_remove_struct()`).
     */
    fun removeAt(x: Int, y: Int) = step {
        if (!inside(x, y)) return@step
        if (picture(MapLayer.SOLID, x, y) >= 0 || picture(MapLayer.TOP, x, y) >= 0) {
            paint(MapLayer.SOLID, x, y, -1)
            paint(MapLayer.TOP, x, y, -1)
        } else {
            paint(MapLayer.FLOOR, x, y, -1)
        }
    }

    /** [structure] on every cell from one corner to the other (legacy `add_region()`). */
    fun fillRegion(structure: Structure, x0: Int, y0: Int, x1: Int, y1: Int) = step {
        for (x in minOf(x0, x1)..maxOf(x0, x1)) for (y in minOf(y0, y1)..maxOf(y0, y1)) placeStructure(structure, x, y)
    }

    /** [removeAt] on every cell from one corner to the other (legacy `remove_region()`). */
    fun clearRegion(x0: Int, y0: Int, x1: Int, y1: Int) = step {
        for (x in minOf(x0, x1)..maxOf(x0, x1)) for (y in minOf(y0, y1)..maxOf(y0, y1)) removeAt(x, y)
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
        for (change in changes.asReversed()) grids.getValue(change.grid)[change.index] = change.before
        redoStack.addLast(changes)
        modified = true
        return true
    }

    fun redo(): Boolean {
        endStroke()
        val changes = redoStack.removeLastOrNull() ?: return false
        for (change in changes) grids.getValue(change.grid)[change.index] = change.after
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
            base = grids.getValue(Grid.FLOOR).toList(),
            solid = grids.getValue(Grid.SOLID).toList(),
            top = grids.getValue(Grid.TOP).toList(),
            goalLayer = grids.getValue(Grid.GOAL).toList(),
            bonusLayer = grids.getValue(Grid.BONUS).toList(),
            manTypeLayer = grids.getValue(Grid.MAN_TYPE).toList(),
            manAmountLayer = grids.getValue(Grid.MAN_COUNT).toList(),
        )
        return MapConversion.documentOf(sidecar.copy(tileLayers = tiles), records)
    }

    companion object {
        /** The original editor's bonus menu: the bonus kinds a building can leave. */
        val BONUS_NAMES: List<String> = listOf(
            "Grenades", "Extra machine gun", "Fuel", "Mines", "Rockets", "Mortar shell", "A-bomb",
            "Men with chain guns", "Men with flame throwers", "Smoke screen", "Invisibility", "Speed-up",
            "Invulnerability", "Armor",
        )

        const val MIN_SIDE = 8

        /** The original's limit: 200 × 200 cells. */
        const val MAX_CELLS = 200 * 200

        /**
         * A new map (legacy `create_fun()` and `clear_map()`): the size rounded up to a multiple of 4,
         * at least 8, and back to 200 × 200 when too big; no codes, any mode, random bonuses on.
         */
        fun newDocument(name: String, world: TankArenaWorld, width: Int, height: Int, start: NewMapStart = NewMapStart.EMPTY): MapDocument {
            var w = ((width + 3) and 3.inv()).coerceAtLeast(MIN_SIDE)
            var h = ((height + 3) and 3.inv()).coerceAtLeast(MIN_SIDE)
            if (w * h > MAX_CELLS) {
                w = 200
                h = 200
            }
            val cells = w * h
            fun first(type: String) = (0 until PictureCatalog.pictureCount(world)).firstOrNull { PictureCatalog.legacyRow(world, it)?.type == type } ?: -1
            val floor = IntArray(cells) { -1 }
            val solid = IntArray(cells) { -1 }
            when (start) {
                NewMapStart.EMPTY -> Unit
                NewMapStart.WATER -> floor.fill(first(if (world == TankArenaWorld.CITY) "PIT_C" else "WATER"))
                NewMapStart.BORDER -> {
                    fun set(x: Int, y: Int, type: String) {
                        solid[x + y * w] = first(type)
                    }
                    for (y in 0 until h) {
                        set(0, y, "BARRIER_L")
                        set(w - 1, y, "BARRIER_R")
                    }
                    for (x in 0 until w) {
                        set(x, 0, "BARRIER_T")
                        set(x, h - 1, "BARRIER_B")
                    }
                    set(0, 0, "BARRIER_TL")
                    set(w - 1, 0, "BARRIER_TR")
                    set(0, h - 1, "BARRIER_BL")
                    set(w - 1, h - 1, "BARRIER_BR")
                }
            }
            val sidecar = MapSceneSidecar(
                metadata = MapMetadata(name = name, widthTiles = w, heightTiles = h, missionCode = "", world = world),
                tileLayers = TileLayers(
                    base = floor.toList(),
                    solid = solid.toList(),
                    // The original puts the barriers on both layers.
                    top = solid.toList(),
                    goalLayer = List(cells) { 0 },
                    bonusLayer = List(cells) { MapConversion.NO_BONUS },
                    manTypeLayer = List(cells) { 0 },
                    manAmountLayer = List(cells) { 0 },
                ),
            )
            return MapConversion.documentOf(sidecar, emptyList())
        }
    }
}

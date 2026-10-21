package nl.tankarena.sim.map

import nl.tankarena.content.GameModeCompatibility
import nl.tankarena.content.MapMetadata
import nl.tankarena.content.MissionText
import nl.tankarena.content.MapSceneSidecar
import nl.tankarena.content.PictureCatalog
import nl.tankarena.content.Structure
import nl.tankarena.content.TankArenaWorld
import nl.tankarena.content.TileLayers
import nl.tankarena.sim.Goal
import nl.tankarena.sim.Lock
import nl.tankarena.sim.Tank
import nl.tankarena.sim.Train
import nl.tankarena.sim.Turret
import nl.tankarena.sim.Warp

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

/** The header settings of the original editor's SPECIAL menu. */
data class MapSettings(
    /** Legacy `this_mission_code`, at most 20 characters; empty: the map's name when saved. */
    val code: String,
    /** Legacy `next_mission_code`: the mission that follows, if any. */
    val next: String,
    val type: GameModeCompatibility,
    /** Legacy `public_pwd`: the code is listed for everyone. */
    val publicCode: Boolean,
    val night: Boolean,
    val randomBonus: Boolean,
) {
    companion object {
        const val CODE_LENGTH = 20
    }
}

/** The paratroopers' weapons a building can hold (legacy HT_CHAIN, HT_FLAME, HT_SROCK). */
enum class MenWeapon(val legacyType: Int) { CHAIN_GUN(1), FLAME_THROWER(2), SMALL_ROCKETS(10) }

/**
 * A map being edited (ADR 0021): its picture layers, structures, and goal, bonus and men layers can be
 * changed, as can its objects; every stroke can be undone and redone, and [toDocument] gives the map
 * to save or play, with its header settings and texts.
 */
/** [unsaved]: a new map, not yet saved anywhere. */
class MapEditor(document: MapDocument, unsaved: Boolean = false) {
    private var sidecar = MapConversion.sidecarOf(document)
    private var objectList: List<MapObject> = document.objects.map { MapConversion.restoreObject(it).save() }

    /** An imported map's `.MAP` leftovers (ADR 0022, option C): per object, and for the header. */
    private var objectPatches: List<List<PatchRun>?> = List(objectList.size) { document.legacy?.objects?.get(it.toString()) }
    private var headPatch: List<PatchRun>? = document.legacy?.head
    private val rawTexts: List<String>? = document.legacy?.texts

    /** Every per-cell layer: the three picture layers first, in [MapLayer] order. */
    private enum class Grid { FLOOR, SOLID, TOP, GOAL, BONUS, MAN_TYPE, MAN_COUNT }

    private val grids: Map<Grid, IntArray>

    val metadata: MapMetadata get() = sidecar.metadata
    val width: Int get() = metadata.widthTiles
    val height: Int get() = metadata.heightTiles
    val world: TankArenaWorld get() = metadata.world

    /** Number of pictures of the map's world; a cell holds `-1` (none) or `0 until pictureCount`. */
    val pictureCount: Int = PictureCatalog.pictureCount(world)

    private sealed interface Change

    private data class CellChange(val grid: Grid, val index: Int, val before: Int, val after: Int) : Change

    private data class ObjectsChange(
        val before: List<MapObject>,
        val after: List<MapObject>,
        val patchesBefore: List<List<PatchRun>?>,
        val patchesAfter: List<List<PatchRun>?>,
    ) : Change

    /** The header and texts ([MapSceneSidecar] without its layers, which [CellChange] covers). */
    private data class SidecarChange(
        val before: MapSceneSidecar,
        val after: MapSceneSidecar,
        val texts: Boolean,
        val headBefore: List<PatchRun>?,
        val headAfter: List<PatchRun>?,
    ) : Change

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
        val change = CellChange(grid, index, cells[index], value)
        cells[index] = value
        record(change)
        return true
    }

    private fun record(change: Change) {
        redoStack.clear()
        modified = true
        val open = stroke
        if (open != null) open += change else undoStack.addLast(listOf(change))
    }

    val settings: MapSettings
        get() = with(sidecar.metadata) {
            MapSettings(missionCode, nextMissionCode.orEmpty(), modeCompatibility, publicPassword, night, randomBonus)
        }

    fun changeSettings(settings: MapSettings) {
        val metadata = sidecar.metadata.copy(
            missionCode = settings.code.take(MapSettings.CODE_LENGTH),
            nextMissionCode = settings.next.take(MapSettings.CODE_LENGTH).ifBlank { null },
            modeCompatibility = settings.type,
            publicPassword = settings.publicCode,
            night = settings.night,
            randomBonus = settings.randomBonus,
        )
        changeSidecar(sidecar.copy(metadata = metadata), texts = false)
    }

    /** The briefing and the texts after a won and a lost mission (legacy `original/MAPS/name.0..2`). */
    val texts: MissionText get() = sidecar.missionText

    /** Changes the texts; typing on in the same text is one undo step. */
    fun changeTexts(texts: MissionText) = changeSidecar(sidecar.copy(missionText = texts), texts = true)

    private fun changeSidecar(after: MapSceneSidecar, texts: Boolean) {
        if (after == sidecar) return
        // The header's leftovers stay only over bytes the change leaves as they were.
        val head = headPatch?.let { runs ->
            LegacyMapExport.keepRuns(runs, LegacyMapExport.head(sidecar, rawTexts), LegacyMapExport.head(after, rawTexts)).ifEmpty { null }
        }
        val last = undoStack.lastOrNull()?.singleOrNull()
        if (texts && stroke == null && last is SidecarChange && last.texts && redoStack.isEmpty()) {
            undoStack[undoStack.lastIndex] = listOf(last.copy(after = after, headAfter = head))
            modified = true
        } else {
            record(SidecarChange(sidecar, after, texts, headPatch, head))
        }
        sidecar = after
        headPatch = head
    }

    /**
     * Legacy save_map(): what the start pads and flags give room for (MAPC_TANK 1, MAPC_CHOP 2,
     * MAPC_PLANE 4, MAPC_RACE 8, MAPC_FLAG 16).
     */
    val contents: Int
        get() = objectList.fold(0) { bits, state ->
            when {
                state is nl.tankarena.sim.Flag.State -> bits or 16
                state is Tank.State && state.startPadType in 0 until 6 -> bits or (1 shl (state.startPadType shr 1))
                state is Tank.State && state.startPadType >= 6 -> bits or 8
                else -> bits
            }
        }

    /** Legacy save_map()'s "STARTING POINT CONFLICT": race pads with other pads or with flags. */
    val startConflict: Boolean
        get() = contents.let { it and 8 != 0 && it and (1 or 2 or 4 or 16) != 0 }

    /** The map's objects, in the order they are saved. */
    val objects: List<MapObject> get() = objectList

    /**
     * Replaces the objects; [origins] says for each new one which old one it is (null: a new object).
     * An object's leftovers from its `.MAP` stay only over bytes an edit leaves as they were.
     */
    private fun setObjects(after: List<MapObject>, origins: List<Int?> = after.indices.map { it.takeIf { i -> i < objectList.size } }) {
        if (after == objectList) return
        val patches = after.indices.map { j ->
            val i = origins[j] ?: return@map null
            val runs = objectPatches[i] ?: return@map null
            val before = objectList[i]
            if (before == after[j]) return@map runs
            val old = LegacyMapExport.bareRecord(before) ?: return@map null
            val new = LegacyMapExport.bareRecord(after[j]) ?: return@map null
            LegacyMapExport.keepRuns(runs, old, new).ifEmpty { null }
        }
        record(ObjectsChange(objectList, after, objectPatches, patches))
        objectList = after
        objectPatches = patches
    }

    /** Adds [added] (an object, or a wagon with its turret); returns the index of the first. */
    fun addObjects(added: List<MapObject>): Int {
        val index = objectList.size
        setObjects(objectList + added)
        return index
    }

    /** Adds what [template] builds with its centre on ([x], [y]); returns its index, or null. */
    fun addObject(template: MapObjects.Template, x: Int, y: Int): Int? {
        val made = template.create(x, y, objectList) ?: return null
        // Legacy create_start(): one start pad of each kind; placing it again moves it.
        if (made is Tank.State && made.startPadType >= 0) {
            val existing = objectList.indexOfFirst { it is Tank.State && it.startPadType == made.startPadType && it.padPlayer == made.padPlayer }
            if (existing >= 0) {
                moveObject(existing, x, y)
                return existing
            }
        }
        return addObjects(listOf(made))
    }

    fun replaceObject(index: Int, state: MapObject) {
        setObjects(objectList.toMutableList().also { it[index] = state })
    }

    /** Moves an object; a train car takes the turret it carries along. */
    fun moveObject(index: Int, x: Int, y: Int) {
        val state = objectList[index]
        val moved = MapObjects.movedTo(state, x, y)
        val list = objectList.toMutableList()
        list[index] = moved
        if (moved is Train.State) carriedTurret(moved.ref)?.let { list[it] = MapObjects.movedTo(list[it], moved.x, moved.y) }
        setObjects(list)
    }

    fun turnObject(index: Int) {
        MapObjects.turned(objectList[index])?.let { replaceObject(index, it) }
    }

    /** Legacy copyobj_fun(): a copy of an object, at [x], [y]. */
    fun cloneObject(index: Int, x: Int, y: Int): Int {
        var copy = MapObjects.movedTo(objectList[index], x, y)
        if (copy is Tank.State && copy.ref != 0) copy = copy.copy(ref = MapObjects.freeRef(objectList))
        if (copy is Tank.State && !copy.computer && copy.startPadType < 0) copy = copy.copy(controllerId = MapObjects.freeControllerId(objectList))
        return addObjects(listOf(copy))
    }

    /** Removes an object; a train car takes its turret along, and its neighbours are coupled to each other. */
    fun removeObject(index: Int) {
        val state = objectList[index]
        val gone = mutableSetOf(index)
        var list = objectList
        if (state is Train.State) {
            carriedTurret(state.ref)?.let { gone += it }
            list = list.map {
                when {
                    it is Train.State && state.frontRef != 0 && it.ref == state.frontRef -> it.copy(rearRef = state.rearRef)
                    it is Train.State && state.rearRef != 0 && it.ref == state.rearRef -> it.copy(frontRef = state.frontRef)
                    else -> it
                }
            }
        }
        val kept = list.indices.filter { it !in gone }
        setObjects(kept.map { list[it] }, kept)
    }

    /** Couples a new wagon of [picture] (1..8) behind the train car at [index] (legacy add_loco()). */
    fun addWagon(index: Int, picture: Int): Int? {
        val front = objectList[index] as? Train.State ?: return null
        val added = MapObjects.wagon(front, picture, objectList)
        val wagon = added.first() as Train.State
        val list = objectList.toMutableList()
        list[index] = front.copy(rearRef = wagon.ref)
        setObjects(list + added)
        return list.size
    }

    private fun carriedTurret(ref: Int): Int? =
        if (ref == 0) null else objectList.indexOfFirst { it is Turret.State && it.ownerRef == ref }.takeIf { it >= 0 }

    /** The object nearest to ([x], [y]) within [reach] pixels of its centre (legacy closest_object()). */
    fun objectAt(x: Int, y: Int, reach: Int = 20): Int? = objectList.indices
        .map { it to MapObjects.centre(objectList[it]) }
        .map { (i, c) -> i to (c.first - x) * (c.first - x) + (c.second - y) * (c.second - y) }
        .filter { it.second < reach * reach }
        .minByOrNull { it.second }?.first

    /**
     * Sets one of the object's points (legacy warp_exit(), lock_position(), edit_waypoint(),
     * select_goal(), tur_forbid_toggle()) from a click at ([x], [y]); [slot] is the waypoint.
     * [clear] empties a waypoint. Returns false when the click sets nothing.
     */
    fun setPoint(index: Int, point: ObjectPoint, x: Int, y: Int, slot: Int = 0, clear: Boolean = false): Boolean {
        val state = objectList[index]
        val changed: MapObject = when {
            // Warp exits and lock keys are kept as points on the map (the saved corner + 16).
            point == ObjectPoint.WARP_EXIT && state is Warp.State -> state.copy(targetX = x, targetY = y)
            point == ObjectPoint.LOCK_KEY && state is Lock.State -> state.copy(lockX = x, lockY = y)
            point == ObjectPoint.WAYPOINT && state is Tank.State -> MapObjects.withWaypoint(state, slot, if (clear) null else x to y)
            point == ObjectPoint.FORBIDDEN_DIRECTION && state is Turret.State -> {
                val (cx, cy) = MapObjects.centre(state)
                val direction = (Math.round(Math.toDegrees(kotlin.math.atan2((x - cx).toDouble(), (cy - y).toDouble())) / 22.5).toInt()).mod(16)
                state.copy(forbidDirections = state.forbidDirections xor (1 shl direction))
            }
            point == ObjectPoint.GOAL_TARGET && state is Goal.State -> {
                val target = objectAt(x, y)?.takeIf { it != index } ?: return false
                var ref = MapObjects.ref(objectList[target])
                val list = objectList.toMutableList()
                if (ref == 0) {
                    ref = MapObjects.freeRef(objectList)
                    list[target] = when (val t = list[target]) {
                        is Tank.State -> t.copy(ref = ref)
                        is Train.State -> t.copy(ref = ref)
                        else -> return false
                    }
                }
                list[index] = state.copy(who = 3, targetRef = ref)
                setObjects(list)
                return true
            }
            else -> return false
        }
        replaceObject(index, changed)
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
        for (change in changes.asReversed()) when (change) {
            is CellChange -> grids.getValue(change.grid)[change.index] = change.before
            is ObjectsChange -> {
                objectList = change.before
                objectPatches = change.patchesBefore
            }
            is SidecarChange -> {
                sidecar = change.before
                headPatch = change.headBefore
            }
        }
        redoStack.addLast(changes)
        modified = true
        return true
    }

    fun redo(): Boolean {
        endStroke()
        val changes = redoStack.removeLastOrNull() ?: return false
        for (change in changes) when (change) {
            is CellChange -> grids.getValue(change.grid)[change.index] = change.after
            is ObjectsChange -> {
                objectList = change.after
                objectPatches = change.patchesAfter
            }
            is SidecarChange -> {
                sidecar = change.after
                headPatch = change.headAfter
            }
        }
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
        // Legacy save_map(): the contents follow the pads and flags; no code is the map's name.
        val metadata = sidecar.metadata.let { it.copy(mapContents = contents, missionCode = it.missionCode.ifBlank { it.name.uppercase() }) }
        val records = objectList.map { requireNotNull(MapConversion.objectRecord(it.restore())) { "cannot save $it" } }
        val objects = objectPatches.withIndex().filter { it.value != null }.associate { it.index.toString() to it.value!! }
        val legacy = LegacyPatch(headPatch, rawTexts, objects.ifEmpty { null }).takeIf { it != LegacyPatch() }
        return MapConversion.documentOf(sidecar.copy(metadata = metadata, tileLayers = tiles), records).copy(legacy = legacy)
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

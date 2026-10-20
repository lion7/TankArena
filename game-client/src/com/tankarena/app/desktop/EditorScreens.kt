package com.tankarena.app.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.LegacyPictureVariant
import com.tankarena.content.LegacySpriteResources
import com.tankarena.content.PictureCatalog
import com.tankarena.content.Structure
import com.tankarena.content.StructureLibrary
import com.tankarena.content.TankArenaWorld
import com.tankarena.render.LegacySpriteCatalog
import com.tankarena.render.PictureCache
import com.tankarena.render.backgroundTileName
import com.tankarena.sim.map.MapDocument
import com.tankarena.sim.map.MapEditor
import com.tankarena.sim.map.MapLayer
import com.tankarena.sim.map.MenWeapon
import com.tankarena.sim.map.NewMapStart
import com.tankarena.sim.map.ShippedMaps
import com.tankarena.ui.compose.menu.PickListLayout
import com.tankarena.ui.compose.menu.PickListScreen
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.floor

/** A map the editor can open: a shipped one, or one saved by the player (which wins on a name clash). */
data class EditorMapChoice(val baseName: String, val userMap: Boolean) {
    val label: String get() = baseName.uppercase() + if (userMap) " *" else ""

    internal fun load(): MapDocument = if (userMap) UserMapStore.load(baseName) else ShippedMaps.load(baseName)!!.document

    companion object {
        internal fun all(): List<EditorMapChoice> {
            val saved = UserMapStore.baseNames().toSet()
            return (ShippedMaps.baseNames().filterNot { it in saved }.map { EditorMapChoice(it, false) } +
                saved.map { EditorMapChoice(it, true) })
                .sortedBy { it.baseName }
        }
    }
}

private const val NEW_MAP_ENTRY = "- NEW MAP -"

/** The editor's first screen: a new map, or which map to open (`*` marks the player's own maps). */
@Composable
internal fun EditorOpenScreen(onOpen: (EditorMapChoice) -> Unit, onNew: () -> Unit, onBack: () -> Unit) {
    val choices = remember { EditorMapChoice.all() }
    PickListScreen(
        title = "EDIT MAP",
        entries = listOf(NEW_MAP_ENTRY) + choices.map { it.label },
        layout = PickListLayout.MAPS,
        onSelect = { label -> if (label == NEW_MAP_ENTRY) onNew() else choices.firstOrNull { it.label == label }?.let(onOpen) },
        onCancel = onBack,
    )
}

/** A map name as the original allows: 1 to 8 letters and digits. */
internal fun validMapName(name: String): Boolean = name.length in 1..8 && name.all { it.isLetterOrDigit() && it.code < 128 }

/**
 * A new map (legacy `create_fun()`): its name, world, size (rounded up to blocks of 4, at most
 * 200 × 200 cells) and how it starts.
 */
@Composable
internal fun NewMapScreen(onCreate: (EditorMapChoice, MapDocument) -> Unit, onBack: () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        var name by remember { mutableStateOf("") }
        var world by remember { mutableStateOf(TankArenaWorld.DESERT) }
        var width by remember { mutableStateOf("48") }
        var height by remember { mutableStateOf("48") }
        var start by remember { mutableStateOf(NewMapStart.BORDER) }
        val taken = remember { EditorMapChoice.all().map { it.baseName } }
        val lower = name.lowercase()
        val sizeX = width.toIntOrNull()
        val sizeY = height.toIntOrNull()
        val problem = when {
            !validMapName(name) -> "A name has 1 to 8 letters or digits."
            sizeX == null || sizeY == null || sizeX < 1 || sizeY < 1 -> "The size is two numbers."
            else -> null
        }
        fun create() {
            if (problem != null) return
            onCreate(EditorMapChoice(lower, userMap = true), MapEditor.newDocument(lower, world, sizeX!!, sizeY!!, start))
        }
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF202020))
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Escape -> onBack()
                        Key.Enter, Key.NumPadEnter -> create()
                        else -> return@onPreviewKeyEvent false
                    }
                    true
                }
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("New map", color = Color.White, fontSize = 28.sp)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(8) },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.width(260.dp).focusRequester(focus),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("World", color = Color.White, modifier = Modifier.width(80.dp))
                for (entry in TankArenaWorld.entries) {
                    FilterChip(selected = world == entry, onClick = { world = entry }, label = { Text(entry.name.lowercase()) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Size", color = Color.White, modifier = Modifier.width(80.dp))
                OutlinedTextField(width, { width = it.filter(Char::isDigit).take(3) }, label = { Text("across") }, singleLine = true, modifier = Modifier.width(110.dp))
                OutlinedTextField(height, { height = it.filter(Char::isDigit).take(3) }, label = { Text("down") }, singleLine = true, modifier = Modifier.width(110.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Start", color = Color.White, modifier = Modifier.width(80.dp))
                for ((entry, label) in listOf(
                    NewMapStart.EMPTY to "empty",
                    NewMapStart.BORDER to "barriers all round",
                    NewMapStart.WATER to if (world == TankArenaWorld.CITY) "all pit" else "all water",
                )) {
                    FilterChip(selected = start == entry, onClick = { start = entry }, label = { Text(label) })
                }
            }
            Text(
                problem ?: if (lower in taken) "${lower.uppercase()} exists; saving replaces it in your maps." else "Enter creates the map.",
                color = if (problem != null) Color(0xFFFF8080) else Color.LightGray,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = ::create, enabled = problem == null) { Text("Create") }
                Button(onClick = onBack) { Text("Back") }
            }
        }
    }
}

private const val MIN_ZOOM = 0.25f
private const val MAX_ZOOM = 3f

/** What the mouse does on the map. */
internal enum class EditorTool(val label: String) {
    PICTURES("pictures"),
    STRUCTURES("structures"),
    GOALS("goals"),
    BONUSES("bonuses"),
    MEN("men"),
}

/** Short names of the bonus kinds, drawn on their cells. */
private val BONUS_SHORT = listOf("GR", "MG", "FU", "MI", "RO", "MO", "AB", "CG", "FL", "SM", "IN", "SP", "IV", "AR")

/**
 * The map editor (ADR 0021). Tools (keys 1–7): the three picture layers (left button paints, right
 * picks up the picture under the cursor); structures from the world's library (left places, right
 * removes the building or else the floor, Shift and drag fills or clears a region); goals (left +10,
 * right −10, with Shift ±1); bonuses (left puts the chosen one, right removes); men (left adds one with
 * the chosen weapon, right removes one). Middle button or the arrow keys scroll, the wheel zooms.
 * Ctrl+Z / Ctrl+Y undo and redo, Ctrl+S saves, F5 play-tests the map as it is, ESC leaves (twice with
 * unsaved changes).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun EditorScreen(
    choice: EditorMapChoice,
    editor: MapEditor,
    onPlayTest: (MapDocument) -> Unit,
    onClose: () -> Unit,
    initialTool: EditorTool = EditorTool.PICTURES,
) {
    MaterialTheme(colorScheme = darkColorScheme()) { EditorContent(choice, editor, onPlayTest, onClose, initialTool) }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun EditorContent(
    choice: EditorMapChoice,
    editor: MapEditor,
    onPlayTest: (MapDocument) -> Unit,
    onClose: () -> Unit,
    initialTool: EditorTool,
) {
    val pictures = PictureCache.shared
    val sprites = LegacySpriteCatalog.shared
    val world = editor.world
    var tool by remember { mutableStateOf(initialTool) }
    var layer by remember { mutableStateOf(MapLayer.FLOOR) }
    var selected by remember { mutableIntStateOf(0) }
    val structures = remember(world) { StructureLibrary.forWorld(world) }
    var structure by remember { mutableIntStateOf(0) }
    var bonus by remember { mutableIntStateOf(0) }
    var menWeapon by remember { mutableStateOf(MenWeapon.CHAIN_GUN) }
    // Shift-drag with the structure tool: the first corner, and whether the region is filled or cleared.
    var regionStart by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var regionFill by remember { mutableStateOf(true) }
    var lastCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val textMeasurer = rememberTextMeasurer()
    var zoom by remember { mutableFloatStateOf(1f) }
    var scrollX by remember { mutableFloatStateOf(0f) }
    var scrollY by remember { mutableFloatStateOf(0f) }
    var showObjects by remember { mutableStateOf(true) }
    var hover by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    // Bumped on every change so the canvas redraws (the editor itself is not observable).
    var revision by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("") }
    var leaveArmed by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }

    val objectBoxes = remember(editor) {
        editor.toDocument().objects.mapNotNull { record ->
            val at = record["at"]?.jsonArray ?: return@mapNotNull null
            val size = record["size"]?.jsonArray
            val type = record["type"]?.jsonPrimitive?.content ?: "?"
            ObjectBox(
                type = type,
                x = at[0].jsonPrimitive.double.toFloat(),
                y = at[1].jsonPrimitive.double.toFloat(),
                width = size?.get(0)?.jsonPrimitive?.double?.toFloat() ?: 8f,
                height = size?.get(1)?.jsonPrimitive?.double?.toFloat() ?: 8f,
            )
        }
    }

    fun save() {
        status = runCatching {
            val file = UserMapStore.save(choice.baseName, editor.toDocument())
            editor.markSaved()
            "Saved to $file"
        }.getOrElse { "Not saved: ${it.message}" }
        revision++
    }

    fun cellAt(position: Offset): Pair<Int, Int> {
        val tile = LEGACY_TILE_SIZE * zoom
        return floor((position.x + scrollX) / tile).toInt() to floor((position.y + scrollY) / tile).toInt()
    }

    /** The tool's action on a cell: [primary] is the left button, [fine] Shift. */
    fun apply(cell: Pair<Int, Int>, primary: Boolean, fine: Boolean) {
        val (x, y) = cell
        if (x !in 0 until editor.width || y !in 0 until editor.height) return
        when (tool) {
            EditorTool.PICTURES -> if (primary) editor.paint(layer, x, y, selected) else selected = editor.picture(layer, x, y)
            EditorTool.STRUCTURES -> if (primary) structures.getOrNull(structure)?.let { editor.placeStructure(it, x, y) } else editor.removeAt(x, y)
            EditorTool.GOALS -> editor.changeGoal(x, y, (if (fine) 1 else 10) * (if (primary) 1 else -1))
            EditorTool.BONUSES -> editor.setBonus(x, y, if (primary) bonus else null)
            EditorTool.MEN -> if (primary) editor.addMan(x, y, menWeapon) else editor.removeMan(x, y)
        }
        revision++
    }

    /** Tools that keep working while the button is held and the mouse moves. */
    fun dragsAlong() = tool == EditorTool.PICTURES || tool == EditorTool.STRUCTURES || tool == EditorTool.BONUSES

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF202020))
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val step = LEGACY_TILE_SIZE * zoom * 2
                when {
                    event.isCtrlPressed && event.key == Key.Z && event.isShiftPressed -> if (editor.redo()) revision++
                    event.isCtrlPressed && event.key == Key.Z -> if (editor.undo()) revision++
                    event.isCtrlPressed && event.key == Key.Y -> if (editor.redo()) revision++
                    event.isCtrlPressed && event.key == Key.S -> save()
                    event.key == Key.F5 -> onPlayTest(editor.toDocument())
                    event.key == Key.One -> { tool = EditorTool.PICTURES; layer = MapLayer.FLOOR }
                    event.key == Key.Two -> { tool = EditorTool.PICTURES; layer = MapLayer.SOLID }
                    event.key == Key.Three -> { tool = EditorTool.PICTURES; layer = MapLayer.TOP }
                    event.key == Key.Four -> tool = EditorTool.STRUCTURES
                    event.key == Key.Five -> tool = EditorTool.GOALS
                    event.key == Key.Six -> tool = EditorTool.BONUSES
                    event.key == Key.Seven -> tool = EditorTool.MEN
                    event.key == Key.DirectionLeft -> scrollX -= step
                    event.key == Key.DirectionRight -> scrollX += step
                    event.key == Key.DirectionUp -> scrollY -= step
                    event.key == Key.DirectionDown -> scrollY += step
                    event.key == Key.Escape -> {
                        if (!editor.modified || leaveArmed) {
                            onClose()
                        } else {
                            leaveArmed = true
                            status = "Unsaved changes: ESC again leaves without saving, Ctrl+S saves"
                        }
                        return@onPreviewKeyEvent true
                    }
                    else -> return@onPreviewKeyEvent false
                }
                leaveArmed = false
                true
            },
    ) {
        // Tool bar.
        Row(
            modifier = Modifier.fillMaxWidth().padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val dirty = revision.let { editor.modified }
            Text(
                "${choice.baseName.uppercase()}${if (dirty) " *" else ""}  ${world.name.lowercase()}  ${editor.width}×${editor.height}",
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { if (editor.undo()) revision++ }, enabled = revision.let { editor.canUndo }) { Text("Undo") }
            Button(onClick = { if (editor.redo()) revision++ }, enabled = revision.let { editor.canRedo }) { Text("Redo") }
            Button(onClick = ::save) { Text("Save") }
            Button(onClick = { onPlayTest(editor.toDocument()) }) { Text("Play") }
            Button(onClick = onClose) { Text("Close") }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for ((index, entry) in MapLayer.entries.withIndex()) {
                FilterChip(
                    selected = tool == EditorTool.PICTURES && layer == entry,
                    onClick = { tool = EditorTool.PICTURES; layer = entry },
                    label = { Text("${index + 1} ${entry.name.lowercase()}") },
                )
            }
            for ((index, entry) in EditorTool.entries.drop(1).withIndex()) {
                FilterChip(selected = tool == entry, onClick = { tool = entry }, label = { Text("${index + 4} ${entry.label}") })
            }
            FilterChip(selected = showObjects, onClick = { showObjects = !showObjects }, label = { Text("objects") })
        }
        Row(modifier = Modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(editor) {
                        awaitPointerEventScope {
                            var last: Offset? = null
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.first()
                                val position = change.position
                                when (event.type) {
                                    PointerEventType.Scroll -> {
                                        val old = zoom
                                        val factor = if (change.scrollDelta.y < 0) 1.25f else 0.8f
                                        zoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
                                        // Keep the point under the cursor in place.
                                        scrollX = (scrollX + position.x) * zoom / old - position.x
                                        scrollY = (scrollY + position.y) * zoom / old - position.y
                                    }
                                    PointerEventType.Press -> {
                                        leaveArmed = false
                                        val primary = event.buttons.isPrimaryPressed
                                        val shift = event.keyboardModifiers.isShiftPressed
                                        if (primary || event.buttons.isSecondaryPressed) {
                                            val cell = cellAt(position)
                                            lastCell = cell
                                            if (tool == EditorTool.STRUCTURES && shift) {
                                                regionStart = cell
                                                regionFill = primary
                                            } else {
                                                editor.beginStroke()
                                                apply(cell, primary, shift)
                                            }
                                        }
                                        last = position
                                    }
                                    PointerEventType.Move -> {
                                        val cell = cellAt(position)
                                        hover = cell
                                        val held = event.buttons.isPrimaryPressed || event.buttons.isSecondaryPressed
                                        when {
                                            held && regionStart == null && dragsAlong() && cell != lastCell &&
                                                // The right button picks up a picture; dragging it does nothing more.
                                                (tool != EditorTool.PICTURES || event.buttons.isPrimaryPressed) -> {
                                                apply(cell, event.buttons.isPrimaryPressed, false)
                                                lastCell = cell
                                            }
                                            event.buttons.isTertiaryPressed -> last?.let {
                                                scrollX -= position.x - it.x
                                                scrollY -= position.y - it.y
                                            }
                                        }
                                        last = position
                                    }
                                    PointerEventType.Release -> {
                                        regionStart?.let { (x0, y0) ->
                                            val (x1, y1) = cellAt(position)
                                            val clip = { v: Int, max: Int -> v.coerceIn(0, max - 1) }
                                            val (ax, ay, bx, by) = listOf(clip(x0, editor.width), clip(y0, editor.height), clip(x1, editor.width), clip(y1, editor.height))
                                            if (regionFill) {
                                                structures.getOrNull(structure)?.let { editor.fillRegion(it, ax, ay, bx, by) }
                                            } else {
                                                editor.clearRegion(ax, ay, bx, by)
                                            }
                                        }
                                        regionStart = null
                                        lastCell = null
                                        editor.endStroke()
                                        revision++
                                    }
                                    PointerEventType.Exit -> hover = null
                                }
                            }
                        }
                    },
            ) {
                // Reading the revision redraws the canvas after every edit.
                revision.let { }
                drawEditorMap(editor, world, if (tool == EditorTool.PICTURES) layer else MapLayer.TOP, zoom, scrollX, scrollY, pictures, sprites)
                if (showObjects) {
                    for (box in objectBoxes) {
                        drawRect(
                            color = Color(0xFFFFD040),
                            topLeft = Offset(box.x * zoom - scrollX, box.y * zoom - scrollY),
                            size = Size(box.width * zoom, box.height * zoom),
                            style = Stroke(width = 1f),
                        )
                    }
                }
                if (tool == EditorTool.GOALS || tool == EditorTool.BONUSES || tool == EditorTool.MEN) {
                    drawCellLabels(editor, tool, zoom, scrollX, scrollY, textMeasurer)
                }
                val tile = LEGACY_TILE_SIZE * zoom
                val start = regionStart
                hover?.let { (x, y) ->
                    val footprint = if (tool == EditorTool.STRUCTURES && start == null) structures.getOrNull(structure) else null
                    val (x0, y0) = start ?: (x to y)
                    val left = minOf(x0, x)
                    val top = minOf(y0, y)
                    val across = if (start != null) kotlin.math.abs(x - x0) + 1 else footprint?.width ?: 1
                    val down = if (start != null) kotlin.math.abs(y - y0) + 1 else footprint?.height ?: 1
                    drawRect(
                        color = if (start != null && !regionFill) Color(0xFFFF6060) else Color.White,
                        topLeft = Offset(left * tile - scrollX, top * tile - scrollY),
                        size = Size(across * tile, down * tile),
                        style = Stroke(width = 2f),
                    )
                }
            }
            // The tool's palette.
            Column(modifier = Modifier.width(300.dp).fillMaxHeight().padding(6.dp)) {
                when (tool) {
                    EditorTool.PICTURES -> PicturePalette(editor, world, filter, { filter = it }, selected, { selected = it }, pictures, sprites)
                    EditorTool.STRUCTURES -> StructurePalette(world, structures, structure, { structure = it }, pictures, sprites)
                    EditorTool.GOALS -> Text(
                        "Destroying a building adds its share to the goal counter; at 100 % the mission is won. " +
                            "Left +10, right −10, with Shift ±1. Negative shares count down.",
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                    )
                    EditorTool.BONUSES -> Column(modifier = Modifier.weight(1f)) {
                        Text("Left puts the bonus a building leaves, right removes it.", color = Color.White, fontSize = 13.sp)
                        for ((index, name) in MapEditor.BONUS_NAMES.withIndex()) {
                            FilterChip(selected = bonus == index, onClick = { bonus = index }, label = { Text("${BONUS_SHORT[index]}  $name") })
                        }
                    }
                    EditorTool.MEN -> Column(modifier = Modifier.weight(1f)) {
                        Text("Paratroopers in a building, at most 50. Left adds one, right removes one.", color = Color.White, fontSize = 13.sp)
                        for (weapon in MenWeapon.entries) {
                            FilterChip(selected = menWeapon == weapon, onClick = { menWeapon = weapon }, label = { Text(weapon.name.lowercase().replace('_', ' ')) })
                        }
                    }
                }
                val hovered = hover?.takeIf { (x, y) -> x in 0 until editor.width && y in 0 until editor.height }
                Text(
                    buildString {
                        when (tool) {
                            EditorTool.PICTURES -> append("Brush: ").append(if (selected < 0) "erase" else pictureLabel(world, selected))
                            EditorTool.STRUCTURES -> append("Structure ${structure + 1} of ${structures.size}")
                            else -> Unit
                        }
                        if (hovered != null) revision.let {
                            val (x, y) = hovered
                            append("\nCell $x, $y: ")
                            append(MapLayer.entries.joinToString(" / ") { l -> editor.picture(l, x, y).let { p -> if (p < 0) "–" else pictureLabel(world, p) } })
                            if (editor.goal(x, y) != 0) append("\ngoal ${editor.goal(x, y)} %")
                            editor.bonus(x, y)?.let { append("\nbonus: ${MapEditor.BONUS_NAMES[it]}") }
                            editor.men(x, y)?.let { (w, n) -> append("\n$n men, ${w.name.lowercase().replace('_', ' ')}") }
                        }
                        if (status.isNotEmpty()) append("\n").append(status)
                    },
                    color = Color.White,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.PicturePalette(
    editor: MapEditor,
    world: TankArenaWorld,
    filter: String,
    onFilter: (String) -> Unit,
    selected: Int,
    onSelect: (Int) -> Unit,
    pictures: PictureCache,
    sprites: LegacySpriteCatalog,
) {
    OutlinedTextField(
        value = filter,
        onValueChange = onFilter,
        label = { Text("Find picture") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    val indices = remember(world, filter) {
        (-1 until editor.pictureCount).filter { index ->
            filter.isBlank() || index >= 0 && pictureLabel(world, index).contains(filter, ignoreCase = true)
        }
    }
    LazyVerticalGrid(columns = GridCells.Adaptive(52.dp), modifier = Modifier.weight(1f)) {
        items(indices) { index ->
            Box(
                modifier = Modifier
                    .padding(2.dp)
                    .size(48.dp)
                    .border(2.dp, if (index == selected) Color.Yellow else Color.DarkGray)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                if (index < 0) {
                    Text("erase", color = Color.White, fontSize = 10.sp)
                } else {
                    Canvas(modifier = Modifier.size(44.dp)) {
                        drawPicture(world, index, Offset.Zero, size.width, pictures, sprites)
                    }
                }
            }
        }
    }
}

/** The world's structures, each drawn with its cells' pictures on all three layers. */
@Composable
private fun ColumnScope.StructurePalette(
    world: TankArenaWorld,
    structures: List<Structure>,
    selected: Int,
    onSelect: (Int) -> Unit,
    pictures: PictureCache,
    sprites: LegacySpriteCatalog,
) {
    Text("Left places, right removes; Shift and drag fills or clears a region.", color = Color.White, fontSize = 13.sp)
    LazyVerticalGrid(columns = GridCells.Adaptive(52.dp), modifier = Modifier.weight(1f)) {
        items(structures.indices.toList()) { index ->
            val structure = structures[index]
            Box(
                modifier = Modifier
                    .padding(2.dp)
                    .size(48.dp)
                    .border(2.dp, if (index == selected) Color.Yellow else Color.DarkGray)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(44.dp)) {
                    val side = size.width / maxOf(structure.width, structure.height)
                    for (cell in structure.cells) for (picture in cell.pictures) {
                        if (picture >= 0) drawPicture(world, picture, Offset(cell.dx * side, cell.dy * side), side, pictures, sprites)
                    }
                }
            }
        }
    }
}

/** The goal shares, bonuses or men on their cells. */
private fun DrawScope.drawCellLabels(editor: MapEditor, tool: EditorTool, zoom: Float, scrollX: Float, scrollY: Float, measurer: TextMeasurer) {
    val tile = LEGACY_TILE_SIZE * zoom
    val style = TextStyle(color = Color.White, fontSize = (9 * zoom).coerceIn(7f, 16f).sp, background = Color(0xB0000000))
    for (y in 0 until editor.height) for (x in 0 until editor.width) {
        val at = Offset(x * tile - scrollX + 2, y * tile - scrollY + 2)
        if (at.x < -tile || at.y < -tile || at.x > size.width || at.y > size.height) continue
        val label = when (tool) {
            EditorTool.GOALS -> editor.goal(x, y).takeIf { it != 0 }?.let { "$it" }
            EditorTool.BONUSES -> editor.bonus(x, y)?.let { BONUS_SHORT[it] }
            else -> editor.men(x, y)?.let { (weapon, count) -> "$count${weapon.name.first()}" }
        } ?: continue
        drawText(measurer, label, at, style)
    }
}

private data class ObjectBox(val type: String, val x: Float, val y: Float, val width: Float, val height: Float)

/** A picture's name and terrain type, e.g. `12 WAT1 (WATER)`. */
private fun pictureLabel(world: TankArenaWorld, index: Int): String {
    val name = PictureCatalog.pictureNames(world, index)?.firstOrNull() ?: "?"
    val type = PictureCatalog.legacyRow(world, index)?.type ?: "?"
    return "$index $name ($type)"
}

/**
 * The map as the game shows it before play: background, floor, solid layer and roofs, with the layer
 * being edited on top at full strength and the layers above it faded, so the painted layer is visible.
 */
private fun DrawScope.drawEditorMap(
    editor: MapEditor,
    world: TankArenaWorld,
    active: MapLayer,
    zoom: Float,
    scrollX: Float,
    scrollY: Float,
    pictures: PictureCache,
    sprites: LegacySpriteCatalog,
) {
    val tile = LEGACY_TILE_SIZE * zoom
    val firstX = floor(scrollX / tile).toInt().coerceAtLeast(0)
    val firstY = floor(scrollY / tile).toInt().coerceAtLeast(0)
    val lastX = minOf(editor.width - 1, floor((scrollX + size.width) / tile).toInt())
    val lastY = minOf(editor.height - 1, floor((scrollY + size.height) / tile).toInt())
    val layers = MapLayer.entries.associateWith { editor.layer(it) }
    for (y in firstY..lastY) {
        for (x in firstX..lastX) {
            val at = Offset(x * tile - scrollX, y * tile - scrollY)
            sprites.findResource(backgroundTileName(world, x, y))?.let(pictures::get)?.let { image ->
                drawImage(image, IntOffset.Zero, IntSize(image.width, image.height), at.round(), IntSize(tile.toInt() + 1, tile.toInt() + 1), filterQuality = FilterQuality.None)
            }
            for (layer in MapLayer.entries) {
                val picture = layers.getValue(layer)[x + y * editor.width]
                if (picture < 0) continue
                val alpha = if (layer.ordinal > active.ordinal) 0.35f else 1f
                drawPicture(world, picture, at, tile, pictures, sprites, alpha)
            }
        }
    }
    // The map's edge.
    drawRect(
        color = Color.Red,
        topLeft = Offset(-scrollX, -scrollY),
        size = Size(editor.width * tile, editor.height * tile),
        style = Stroke(width = 1f),
    )
}

private fun DrawScope.drawPicture(
    world: TankArenaWorld,
    picture: Int,
    at: Offset,
    side: Float,
    pictures: PictureCache,
    sprites: LegacySpriteCatalog,
    alpha: Float = 1f,
) {
    val name = LegacySpriteResources.nameForTile(world, picture, LegacyPictureVariant.INTACT) ?: return
    val image = sprites.findResource(name)?.let(pictures::get)
    if (image == null) {
        drawRect(Color.Magenta.copy(alpha = 0.5f * alpha), at, Size(side, side))
        return
    }
    val source = IntSize(minOf(image.width, LEGACY_TILE_SIZE), minOf(image.height, LEGACY_TILE_SIZE))
    drawImage(
        image,
        IntOffset.Zero,
        source,
        at.round(),
        IntSize((source.width * side / LEGACY_TILE_SIZE).toInt() + 1, (source.height * side / LEGACY_TILE_SIZE).toInt() + 1),
        alpha = alpha,
        filterQuality = FilterQuality.None,
    )
}

private fun Offset.round() = IntOffset(x.toInt(), y.toInt())

package com.tankarena.app.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.LegacyPictureVariant
import com.tankarena.content.LegacySpriteResources
import com.tankarena.content.PictureCatalog
import com.tankarena.content.TankArenaWorld
import com.tankarena.render.LegacySpriteCatalog
import com.tankarena.render.PictureCache
import com.tankarena.render.backgroundTileName
import com.tankarena.sim.map.MapDocument
import com.tankarena.sim.map.MapEditor
import com.tankarena.sim.map.MapLayer
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

/** The editor's first screen: which map to open (`*` marks the player's own maps). */
@Composable
internal fun EditorOpenScreen(onOpen: (EditorMapChoice) -> Unit, onBack: () -> Unit) {
    val choices = remember { EditorMapChoice.all() }
    PickListScreen(
        title = "EDIT MAP",
        entries = choices.map { it.label },
        layout = PickListLayout.MAPS,
        onSelect = { label -> choices.firstOrNull { it.label == label }?.let(onOpen) },
        onCancel = onBack,
    )
}

private const val MIN_ZOOM = 0.25f
private const val MAX_ZOOM = 3f

/**
 * The map editor (ADR 0021), first version: the three picture layers painted with the world's
 * pictures, undo and redo, saving to the player's maps. Left button paints, right button picks the
 * picture under the cursor, middle button or the arrow keys scroll, the wheel zooms. Keys: 1/2/3 pick
 * the layer, Ctrl+Z / Ctrl+Y undo and redo, Ctrl+S saves, ESC leaves (twice with unsaved changes).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun EditorScreen(choice: EditorMapChoice, onClose: () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme()) { EditorContent(choice, onClose) }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun EditorContent(choice: EditorMapChoice, onClose: () -> Unit) {
    val editor = remember(choice) { MapEditor(choice.load()) }
    val pictures = PictureCache.shared
    val sprites = LegacySpriteCatalog.shared
    val world = editor.world
    var layer by remember { mutableStateOf(MapLayer.FLOOR) }
    var selected by remember { mutableIntStateOf(0) }
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

    val objectBoxes = remember(choice) {
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

    fun paintAt(position: Offset) {
        val (x, y) = cellAt(position)
        if (editor.paint(layer, x, y, selected)) revision++
    }

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
                    event.key == Key.One -> layer = MapLayer.FLOOR
                    event.key == Key.Two -> layer = MapLayer.SOLID
                    event.key == Key.Three -> layer = MapLayer.TOP
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
            )
            for ((index, entry) in MapLayer.entries.withIndex()) {
                FilterChip(
                    selected = layer == entry,
                    onClick = { layer = entry },
                    label = { Text("${index + 1} ${entry.name.lowercase()}") },
                )
            }
            FilterChip(selected = showObjects, onClick = { showObjects = !showObjects }, label = { Text("objects") })
            Button(onClick = { if (editor.undo()) revision++ }, enabled = revision.let { editor.canUndo }) { Text("Undo") }
            Button(onClick = { if (editor.redo()) revision++ }, enabled = revision.let { editor.canRedo }) { Text("Redo") }
            Button(onClick = ::save) { Text("Save") }
            Button(onClick = onClose) { Text("Close") }
        }
        Row(modifier = Modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(choice) {
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
                                        when {
                                            event.buttons.isPrimaryPressed -> {
                                                editor.beginStroke()
                                                paintAt(position)
                                            }
                                            event.buttons.isSecondaryPressed -> {
                                                val (x, y) = cellAt(position)
                                                if (x in 0 until editor.width && y in 0 until editor.height) {
                                                    selected = editor.picture(layer, x, y)
                                                }
                                            }
                                        }
                                        last = position
                                    }
                                    PointerEventType.Move -> {
                                        hover = cellAt(position)
                                        when {
                                            event.buttons.isPrimaryPressed -> paintAt(position)
                                            event.buttons.isTertiaryPressed -> last?.let {
                                                scrollX -= position.x - it.x
                                                scrollY -= position.y - it.y
                                            }
                                        }
                                        last = position
                                    }
                                    PointerEventType.Release -> {
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
                drawEditorMap(editor, world, layer, zoom, scrollX, scrollY, pictures, sprites)
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
                hover?.let { (x, y) ->
                    val tile = LEGACY_TILE_SIZE * zoom
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(x * tile - scrollX, y * tile - scrollY),
                        size = Size(tile, tile),
                        style = Stroke(width = 2f),
                    )
                }
            }
            // Picture palette.
            Column(modifier = Modifier.width(300.dp).fillMaxHeight().padding(6.dp)) {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
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
                                .clickable { selected = index },
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
                val hovered = hover?.takeIf { (x, y) -> x in 0 until editor.width && y in 0 until editor.height }
                val under = hovered?.let { (x, y) -> revision.let { editor.picture(layer, x, y) } }
                Text(
                    buildString {
                        append("Brush: ").append(if (selected < 0) "erase" else pictureLabel(world, selected))
                        if (hovered != null) {
                            append("\nCell ${hovered.first}, ${hovered.second}: ")
                            append(if (under == null || under < 0) "none" else pictureLabel(world, under))
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

package nl.tankarena.app.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.areAnyPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.tankarena.audio.AudioManager
import nl.tankarena.audio.DesktopAudioBackend
import nl.tankarena.protocol.snapshot.GameEvent
import nl.tankarena.protocol.snapshot.ServerFrame
import nl.tankarena.render.ViewportEventQueue
import nl.tankarena.sim.MatchMode
import nl.tankarena.sim.MatchRecording
import nl.tankarena.sim.ReplayMatchClient
import nl.tankarena.sim.SimulationLoop
import nl.tankarena.ui.compose.GameMode
import nl.tankarena.ui.compose.MissionOutcome
import nl.tankarena.ui.compose.extended.ExtendedColors
import nl.tankarena.ui.compose.extended.ExtendedList
import nl.tankarena.ui.compose.extended.ExtendedPage
import nl.tankarena.ui.compose.extended.KeyRouter
import nl.tankarena.ui.compose.extended.ListEntry
import nl.tankarena.ui.compose.extended.ListWithDetails
import nl.tankarena.ui.compose.menu.MissionEntry
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** A saved recording in the replay list: `<mission>-<yyyyMMdd>-<HHmmss>-<outcome>.json`. */
internal data class ReplayEntry(val file: File, val label: String) {
    companion object {
        private val NAME = Regex("""(.+)-(\d{4})(\d{2})(\d{2})-(\d{2})(\d{2})\d{2}-(\w+)""")

        fun of(file: File): ReplayEntry {
            val match = NAME.matchEntire(file.nameWithoutExtension)
            val label = if (match == null) {
                file.nameWithoutExtension
            } else {
                val (mission, _, month, day, hour, minute, outcome) = match.destructured
                "${mission.uppercase()} $day-$month $hour:$minute $outcome"
            }
            return ReplayEntry(file, label)
        }

        /** The saved recordings, newest first. */
        fun all(): List<ReplayEntry> = RecordingStore.directory()
            .listFiles { file -> file.extension == "json" }
            .orEmpty()
            .sortedByDescending { it.lastModified() }
            .map(::of)
    }
}

/** What the replay list shows of a recording, read when it is selected. */
private data class ReplayDetails(val recording: MatchRecording?, val error: String?)

private fun readDetails(entry: ReplayEntry): ReplayDetails =
    runCatching { ReplayDetails(MatchRecording.fromJson(entry.file.readText()), null) }
        .getOrElse { ReplayDetails(null, it.message ?: "unreadable") }

/**
 * Extended: the saved recordings, newest first, with what each one is beside the list. Enter
 * watches, F2 names, Delete removes (after Y).
 */
@Composable
internal fun ReplayListScreen(onWatch: (ReplayEntry) -> Unit, onBack: () -> Unit) {
    var entries by remember { mutableStateOf(ReplayEntry.all()) }
    var selected by remember { mutableIntStateOf(0) }
    var deleting by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    val details = remember(entries, selected) { entries.getOrNull(selected)?.let(::readDetails) }
    val titles = remember(entries) { entries.map(::readTitle) }
    val keys = remember { KeyRouter() }
    val nameFocus = remember { FocusRequester() }

    fun reload() {
        entries = ReplayEntry.all()
        selected = selected.coerceAtMost((entries.size - 1).coerceAtLeast(0))
    }

    ExtendedPage(
        title = "Replays",
        subtitle = "Your recorded games, newest first",
        onBack = {
            when {
                deleting -> deleting = false
                renaming -> renaming = false
                else -> onBack()
            }
        },
        footer = when {
            deleting -> "Delete this replay? Y deletes, N keeps it"
            renaming -> "Enter keeps the name, ESC cancels"
            entries.isEmpty() -> "Every game you finish is recorded here"
            else -> "Enter watches   F2 names   Delete removes"
        },
        onKey = { event ->
            val entry = entries.getOrNull(selected)
            when {
                renaming -> false
                deleting -> {
                    when (event.key) {
                        Key.Y -> {
                            entry?.file?.delete()
                            deleting = false
                            reload()
                        }
                        Key.N -> deleting = false
                        else -> return@ExtendedPage false
                    }
                    true
                }
                entry != null && event.key == Key.Delete -> {
                    deleting = true
                    true
                }
                entry != null && event.key == Key.F2 -> {
                    name = details?.recording?.title ?: ""
                    renaming = true
                    true
                }
                else -> keys.handle(event)
            }
        },
    ) {
        if (entries.isEmpty()) {
            Text("No replays yet — play a game first.", color = ExtendedColors.dim, fontSize = 18.sp)
            return@ExtendedPage
        }
        ListWithDetails(
            list = {
                ExtendedList(
                    entries.mapIndexed { index, entry ->
                        ListEntry(titles[index] ?: entry.label.substringBefore(' '), entry.label.substringAfter(' '))
                    },
                    onPick = { onWatch(entries[it]) },
                    initial = selected,
                    onSelect = { selected = it },
                    keys = keys,
                )
            },
            details = {
                Column {
                    val recording = details?.recording
                    if (recording == null) {
                        Text("This file cannot be read: ${details?.error}", color = ExtendedColors.dim, fontSize = 15.sp)
                        return@Column
                    }
                    if (renaming) {
                        LaunchedEffect(Unit) { nameFocus.requestFocus() }
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(40) },
                            label = { Text("Name") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(nameFocus)
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                        val entry = entries[selected]
                                        entry.file.writeText(recording.copy(title = name.trim().ifEmpty { null }).toJson())
                                        renaming = false
                                        reload()
                                        true
                                    } else {
                                        false
                                    }
                                },
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                    Text(recording.title ?: recording.mission.uppercase(), color = ExtendedColors.accent, fontSize = 22.sp)
                    val mode = when (recording.mode) {
                        MatchMode.SINGLE -> "one player"
                        MatchMode.DUAL_VS_COMPUTER -> "two players against the computer"
                        MatchMode.DUAL -> "player against player"
                    }
                    val result = when (recording.result?.won) {
                        true -> "won"
                        false -> "lost"
                        null -> "not finished"
                    }
                    val seconds = recording.ticks / 100
                    Text("${recording.mission.uppercase()} — $mode", color = ExtendedColors.dim, fontSize = 14.sp)
                    Spacer(Modifier.height(16.dp))
                    Text("$result after ${seconds / 60}:${"%02d".format(seconds % 60)}", color = ExtendedColors.text, fontSize = 16.sp)
                    Text("recorded ${entries[selected].label.substringAfter(' ').substringBeforeLast(' ')}", color = ExtendedColors.text, fontSize = 16.sp)
                    Text("${recording.ruleset.name.lowercase()} rules v${recording.rulesVersion}", color = ExtendedColors.dim, fontSize = 14.sp)
                    if (recording.rulesVersion != recording.ruleset.rulesVersion) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Played under older rules: it will not play out as it was recorded.",
                            color = ExtendedColors.accent,
                            fontSize = 14.sp,
                        )
                    }
                }
            },
        )
    }
}

/** The player's name for a replay, if it has one (read cheaply from the file's start). */
private fun readTitle(entry: ReplayEntry): String? =
    runCatching { MatchRecording.fromJson(entry.file.readText()).title }.getOrNull()

/** Replay speeds in quarter ticks per tick of the clock: 1/4×, 1/2×, 1×, 2×, 4×, 8×. */
private val SPEED_QUARTERS = intArrayOf(1, 2, 4, 8, 16, 32)
private const val NORMAL_SPEED = 2

private class ReplayControl {
    @Volatile var paused = false
    @Volatile var speed = NORMAL_SPEED
}

private data class ReplayView(val frame: ServerFrame, val banner: MissionOutcome? = null, val finished: Boolean = false)

/**
 * Extended: watches a recording through the game screen. Space pauses, Left and Right change the
 * speed, ESC leaves. The match is replayed from its inputs, so it plays exactly as it was played.
 */
@Composable
internal fun ReplayScreen(
    recording: MatchRecording,
    mission: MissionEntry,
    soundVolume: Int,
    onClose: () -> Unit,
) {
    val mode = when (recording.mode) {
        MatchMode.SINGLE -> GameMode.SINGLE_PLAYER_VS_COMPUTER
        MatchMode.DUAL -> GameMode.PLAYER_VS_PLAYER
        MatchMode.DUAL_VS_COMPUTER -> GameMode.DUAL_PLAYER_VS_COMPUTER
    }
    val playerIds = (0 until recording.playerCount).toList()
    // R at the end starts the replay again: a new client from the first tick.
    var run by remember { mutableIntStateOf(0) }
    val host = remember(recording, run) { ReplayMatchClient(recording, mission.document) }
    val control = remember(host) { ReplayControl() }
    val audio = remember { AudioManager(DesktopAudioBackend()) }
    SideEffect { audio.soundVolume = soundVolume }
    DisposableEffect(audio) { onDispose(audio::dispose) }
    val eventQueues = remember(host) { List(MAX_PLAYERS) { ViewportEventQueue() } }
    val published = remember(host) { AtomicReference(ReplayView(host.currentServerFrame())) }
    var view by remember(host) { mutableStateOf(published.get()) }
    var paused by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(NORMAL_SPEED) }
    // C, dragging or the wheel: a free camera, moved with the arrow keys or the mouse and zoomed,
    // instead of following the players.
    var freeCamera by remember { mutableStateOf<Offset?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    // Screen pixels per map pixel in the free camera view, to move it with the mouse.
    var cameraScale by remember { mutableFloatStateOf(2f) }
    val heldArrows = remember { mutableStateSetOf<Key>() }
    val worldWidth = mission.sidecar.metadata.widthTiles * nl.tankarena.content.LEGACY_TILE_SIZE.toFloat()
    val worldHeight = mission.sidecar.metadata.heightTiles * nl.tankarena.content.LEGACY_TILE_SIZE.toFloat()
    fun clamp(at: Offset) = Offset(at.x.coerceIn(0f, worldWidth), at.y.coerceIn(0f, worldHeight))
    fun startCamera(): Offset =
        freeCamera ?: view.frame.playerViews.firstOrNull()?.let { Offset(it.cameraCenterX.toFloat(), it.cameraCenterY.toFloat()) } ?: Offset.Zero
    /** Zooms by [factor], keeping the map point under [at] (screen pixels from the view's centre) in place. */
    fun zoomBy(factor: Float, at: Offset = Offset.Zero) {
        val camera = startCamera()
        val old = zoom
        zoom = (zoom * factor).coerceIn(MIN_CAMERA_ZOOM, MAX_CAMERA_ZOOM)
        val point = camera + at / cameraScale
        freeCamera = clamp(point - (point - camera) * (old / zoom))
    }
    // Held arrow keys move the free camera smoothly: a screen's height a second, whatever the zoom.
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val seconds = if (last == 0L) 0f else (now - last) / 1e9f
                last = now
                val camera = freeCamera ?: return@withFrameNanos
                if (heldArrows.isEmpty()) return@withFrameNanos
                val speed = 900f / cameraScale * seconds
                var dx = 0f
                var dy = 0f
                if (Key.DirectionLeft in heldArrows) dx -= speed
                if (Key.DirectionRight in heldArrows) dx += speed
                if (Key.DirectionUp in heldArrows) dy -= speed
                if (Key.DirectionDown in heldArrows) dy += speed
                freeCamera = clamp(camera + Offset(dx, dy))
            }
        }
    }

    DisposableEffect(host) {
        var credit = 0
        var outcome: MissionOutcome? = null
        val loop = SimulationLoop {
            if (!control.paused && !host.finished) {
                credit += SPEED_QUARTERS[control.speed]
                while (credit >= 4 && !host.finished) {
                    credit -= 4
                    val frame = host.tick()
                    eventQueues.forEach { it.push(frame.world.events) }
                    if (control.speed == NORMAL_SPEED) audio.processEvents(frame.world.events, frame.playerViews)
                    if (outcome == null) {
                        outcome = frame.world.events.firstNotNullOfOrNull { event ->
                            when (event) {
                                is GameEvent.MissionWon -> MissionOutcome.WON
                                is GameEvent.MissionLost -> MissionOutcome.LOST
                                else -> null
                            }
                        }
                    }
                    published.set(ReplayView(frame, outcome, host.finished))
                }
            }
            true
        }
        loop.start()
        onDispose {
            loop.stop()
            host.dispose()
        }
    }
    LaunchedEffect(host) {
        while (true) {
            val latest = withFrameNanos { published.get() }
            if (latest !== view) view = latest
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val arrow = event.key in listOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown)
                if (event.type == KeyEventType.KeyUp) {
                    if (arrow) heldArrows -= event.key
                    return@onPreviewKeyEvent arrow
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val camera = freeCamera
                when {
                    event.key == Key.Escape -> onClose()
                    event.key == Key.R && view.finished -> {
                        run++
                        paused = false
                    }
                    event.key == Key.Spacebar -> paused = !paused
                    event.key == Key.C -> {
                        freeCamera = if (camera == null) startCamera() else null
                        heldArrows.clear()
                    }
                    camera != null && arrow -> heldArrows += event.key
                    event.key == Key.PageUp -> zoomBy(1.25f)
                    event.key == Key.PageDown -> zoomBy(0.8f)
                    event.key == Key.DirectionRight || event.key == Key.Plus || event.key == Key.Equals ->
                        speed = (speed + 1).coerceAtMost(SPEED_QUARTERS.lastIndex)
                    event.key == Key.DirectionLeft || event.key == Key.Minus -> speed = (speed - 1).coerceAtLeast(0)
                    else -> return@onPreviewKeyEvent false
                }
                control.paused = paused
                control.speed = speed
                true
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        var last: Offset? = null
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.first()
                            when (event.type) {
                                PointerEventType.Scroll -> {
                                    zoomBy(if (change.scrollDelta.y < 0) 1.25f else 0.8f, change.position - Offset(size.width / 2f, size.height / 2f))
                                    change.consume()
                                }
                                PointerEventType.Press -> last = change.position
                                PointerEventType.Move -> {
                                    val from = last
                                    if (from != null && event.buttons.areAnyPressed) {
                                        freeCamera = clamp(startCamera() - (change.position - from) / cameraScale)
                                        last = change.position
                                    }
                                }
                                PointerEventType.Release -> last = null
                            }
                        }
                    }
                },
        ) {
            val camera = freeCamera
            if (camera == null) {
                GameplayFrame(
                    mission = mission,
                    mode = mode,
                    serverFrame = view.frame,
                    playerIds = playerIds,
                    eventQueues = eventQueues,
                    missionEndBanner = view.banner,
                    brightness = 1f,
                )
            } else {
                FreeCameraView(
                    mission = mission,
                    serverFrame = view.frame,
                    events = eventQueues[0],
                    cameraX = camera.x,
                    cameraY = camera.y,
                    zoom = zoom,
                    onScale = { cameraScale = it },
                )
            }
        }
        if (view.finished) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 32.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("END OF REPLAY", color = Color.White, fontSize = 28.sp)
                val result = when (view.banner) {
                    MissionOutcome.WON -> "The mission was won."
                    MissionOutcome.LOST -> "The mission was lost."
                    null -> "The recording stops here."
                }
                Text(result, color = Color.LightGray, fontSize = 16.sp)
                Text("R watch again    C free camera    Esc back", color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
            }
        }
        val rate = SPEED_QUARTERS[speed].let { if (it >= 4) "${it / 4}x" else "1/${4 / it}x" }
        val state = when {
            view.finished -> "end"
            paused -> "paused"
            else -> rate
        }
        Text(
            text = "REPLAY ${recording.title ?: recording.mission.uppercase()}  $state" +
                (if (freeCamera != null) "  free camera ${"%.2f".format(zoom)}x   arrows or drag move, wheel or PgUp/PgDn zoom" else "") +
                "   Space pause  +/- speed  C free camera  Esc back",
            color = Color.White,
            fontSize = 13.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

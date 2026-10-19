package com.tankarena.app.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tankarena.audio.AudioManager
import com.tankarena.audio.DesktopAudioBackend
import com.tankarena.protocol.snapshot.GameEvent
import com.tankarena.protocol.snapshot.ServerFrame
import com.tankarena.render.ViewportEventQueue
import com.tankarena.sim.MatchMode
import com.tankarena.sim.MatchRecording
import com.tankarena.sim.ReplayMatchClient
import com.tankarena.sim.SimulationLoop
import com.tankarena.ui.compose.GameMode
import com.tankarena.ui.compose.MissionOutcome
import com.tankarena.ui.compose.menu.MessageBarScreen
import com.tankarena.ui.compose.menu.MissionEntry
import com.tankarena.ui.compose.menu.PickListLayout
import com.tankarena.ui.compose.menu.PickListScreen
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

/** Extended: the list of saved recordings; Enter watches one. */
@Composable
internal fun ReplayListScreen(onWatch: (ReplayEntry) -> Unit, onBack: () -> Unit) {
    val entries = remember { ReplayEntry.all() }
    if (entries.isEmpty()) {
        MessageBarScreen(message = "No replays yet - play a game first", onDismiss = onBack)
        return
    }
    // Labels can repeat (two games in the same minute); the index tells them apart.
    val labels = entries.mapIndexed { i, entry -> "${i + 1}. ${entry.label}" }
    PickListScreen(
        title = "REPLAYS",
        entries = labels,
        layout = PickListLayout.PASSWORDS,
        onSelect = { label -> onWatch(entries[labels.indexOf(label)]) },
        onCancel = onBack,
    )
}

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
    val playerIds = if (mode == GameMode.SINGLE_PLAYER_VS_COMPUTER) listOf(0) else listOf(0, 1)
    val host = remember(recording) { ReplayMatchClient(recording, mission.document) }
    val control = remember(host) { ReplayControl() }
    val audio = remember { AudioManager(DesktopAudioBackend()) }
    SideEffect { audio.soundVolume = soundVolume }
    DisposableEffect(audio) { onDispose(audio::dispose) }
    val eventQueues = remember(host) { List(2) { ViewportEventQueue() } }
    val published = remember(host) { AtomicReference(ReplayView(host.currentServerFrame())) }
    var view by remember(host) { mutableStateOf(published.get()) }
    var paused by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(NORMAL_SPEED) }

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
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Escape -> onClose()
                    Key.Spacebar -> paused = !paused
                    Key.DirectionRight, Key.Plus, Key.Equals -> speed = (speed + 1).coerceAtMost(SPEED_QUARTERS.lastIndex)
                    Key.DirectionLeft, Key.Minus -> speed = (speed - 1).coerceAtLeast(0)
                    else -> return@onPreviewKeyEvent false
                }
                control.paused = paused
                control.speed = speed
                true
            },
    ) {
        GameplayFrame(
            mission = mission,
            mode = mode,
            serverFrame = view.frame,
            playerIds = playerIds,
            eventQueues = eventQueues,
            missionEndBanner = view.banner,
            brightness = 1f,
        )
        val rate = SPEED_QUARTERS[speed].let { if (it >= 4) "${it / 4}x" else "1/${4 / it}x" }
        val state = when {
            view.finished -> "end"
            paused -> "paused"
            else -> rate
        }
        Text(
            text = "REPLAY ${recording.mission.uppercase()}  $state   Space pause  Left/Right speed  Esc back",
            color = Color.White,
            fontSize = 13.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

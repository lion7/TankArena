package com.tankarena.app.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.tankarena.audio.AudioManager
import com.tankarena.audio.DesktopAudioBackend
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.core.FixedStepClock
import com.tankarena.protocol.InputFrame
import com.tankarena.protocol.snapshot.GameEvent
import com.tankarena.protocol.snapshot.PlayerView
import com.tankarena.protocol.snapshot.ServerFrame
import com.tankarena.render.kubriko.LEGACY_PLAYFIELD_ASPECT_RATIO
import com.tankarena.render.kubriko.TankArenaViewport
import com.tankarena.render.kubriko.ViewportEventQueue
import com.tankarena.render.kubriko.ViewportGeometry
import com.tankarena.sim.kubriko.server.LocalMatchClient
import com.tankarena.sim.kubriko.server.MatchMode
import com.tankarena.ui.compose.DesktopShellScreen
import com.tankarena.ui.compose.GameMode
import com.tankarena.ui.compose.MissionOutcome
import com.tankarena.ui.compose.hud.HudOverlay
import com.tankarena.ui.compose.menu.DebriefScreen
import com.tankarena.ui.compose.menu.findByCode
import com.tankarena.ui.compose.menu.GameModeMenuScreen
import com.tankarena.ui.compose.menu.HelpFile
import com.tankarena.ui.compose.menu.MainMenuScreen
import com.tankarena.ui.compose.menu.MessageBarScreen
import com.tankarena.ui.compose.menu.MISSION_END_BANNER_MILLIS
import com.tankarena.ui.compose.menu.MissionCatalog
import com.tankarena.ui.compose.menu.MissionCodes
import com.tankarena.ui.compose.menu.MissionEndBanner
import com.tankarena.ui.compose.menu.MissionEntry
import com.tankarena.ui.compose.menu.MissionGate
import com.tankarena.ui.compose.menu.PasswordStore
import com.tankarena.ui.compose.menu.PickListScreen
import com.tankarena.ui.compose.menu.PromptBarScreen
import com.tankarena.ui.compose.menu.RetroColors
import com.tankarena.ui.compose.menu.TextViewerScreen
import com.tankarena.ui.compose.menu.TiledPanelBackground
import kotlinx.coroutines.delay

/** `-Dtankarena.debugOverlay=true` draws viewport/camera geometry over the playfield. */
private val showDebugOverlay: Boolean = System.getProperty("tankarena.debugOverlay") == "true"

/** Split-screen halves show 320 px of the 640 px legacy playfield each. */
private const val SPLIT_PLAYFIELD_WIDTH = 320

fun main() = application {
    val controls = remember { DesktopControls() }
    var screen by remember { mutableStateOf<DesktopShellScreen>(DesktopShellScreen.MainMenu) }
    var missions by remember { mutableStateOf<List<MissionEntry>?>(null) }
    val missionGate = remember { MissionGate(PasswordStore()) }
    val exitToMainMenu: () -> Unit = {
        controls.resetAll()
        screen = DesktopShellScreen.MainMenu
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Tank Arena Rewrite",
        onPreviewKeyEvent = { event ->
            handleGlobalKeys(event, screen, controls, exitToMainMenu)
        },
    ) {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                when (val current = screen) {
                    DesktopShellScreen.MainMenu -> MainMenuScreen(
                        onStartNewGame = { screen = DesktopShellScreen.GameModeSelect },
                        onExit = ::exitApplication,
                        onArenaInfo = { screen = DesktopShellScreen.Help(HelpFile.ARENA_INFO) },
                        onDefaultKeys = { screen = DesktopShellScreen.Help(HelpFile.DEFAULT_KEYS) },
                    )

                    is DesktopShellScreen.Help -> TextViewerScreen(
                        text = remember(current.file) { current.file.load() },
                        onClose = { screen = DesktopShellScreen.MainMenu },
                    )

                    DesktopShellScreen.GameModeSelect -> {
                        LaunchedEffect(Unit) {
                            if (missions == null) missions = MissionCatalog.load()
                        }
                        GameModeMenuScreen(
                            onSelectPlayerVsPlayer = { screen = DesktopShellScreen.CodePrompt(GameMode.PLAYER_VS_PLAYER) },
                            onSelectSinglePlayer = { screen = DesktopShellScreen.CodePrompt(GameMode.SINGLE_PLAYER_VS_COMPUTER) },
                            onSelectDualVsComputer = { screen = DesktopShellScreen.CodePrompt(GameMode.DUAL_PLAYER_VS_COMPUTER) },
                            onCancel = { screen = DesktopShellScreen.MainMenu },
                        )
                    }

                    is DesktopShellScreen.CodePrompt -> PromptBarScreen(
                        prompt = MissionCodes.prompt(current.mode),
                        initialText = missionGate.currentCode(current.mode),
                        maxLength = MissionCodes.maxLength(current.mode),
                        onSubmit = { code ->
                            missions?.let { screen = missionGate.submit(it, code, current.mode) }
                        },
                        onCancel = { screen = DesktopShellScreen.CodeList(current.mode) },
                    )

                    is DesktopShellScreen.CodeList -> {
                        val entries = remember(missions, current.mode) {
                            missions?.let { missionGate.listEntries(it, current.mode) }
                        }
                        when {
                            entries == null -> Unit
                            entries.isEmpty() -> MessageBarScreen(
                                message = MissionCodes.emptyListMessage(current.mode),
                                onDismiss = { screen = DesktopShellScreen.GameModeSelect },
                            )
                            else -> PickListScreen(
                                title = MissionCodes.listTitle(current.mode),
                                entries = entries,
                                onSelect = { code ->
                                    screen = missionGate.submit(missions.orEmpty(), code, current.mode)
                                },
                                onCancel = { screen = DesktopShellScreen.GameModeSelect },
                            )
                        }
                    }

                    is DesktopShellScreen.Message -> MessageBarScreen(
                        message = current.text,
                        onDismiss = { screen = current.next },
                    )

                    is DesktopShellScreen.Briefing -> TextViewerScreen(
                        text = current.mission.sidecar.missionText.briefing,
                        onClose = {
                            controls.resetAll()
                            screen = DesktopShellScreen.Playing(current.mission, current.mode)
                        },
                    )

                    is DesktopShellScreen.Playing -> GameplayScreen(
                        mission = current.mission,
                        mode = current.mode,
                        controls = controls,
                        onMissionEnd = { outcome, score, kills, captures, time ->
                            controls.resetAll()
                            missionGate.missionEnded(current.mission, current.mode, outcome)
                            screen = DesktopShellScreen.Debrief(
                                current.mission, current.mode, outcome,
                                score = score, kills = kills, captures = captures, time = time,
                            )
                        },
                    )

                    is DesktopShellScreen.Debrief -> {
                        val nextMission = missions.orEmpty().findByCode(current.mission.sidecar.metadata.nextMissionCode)
                        DebriefScreen(
                            mission = current.mission,
                            outcome = current.outcome,
                            score = current.score,
                            kills = current.kills,
                            captures = current.captures,
                            time = current.time,
                            nextMission = nextMission,
                            onNextMission = { next ->
                                controls.resetAll()
                                screen = DesktopShellScreen.Playing(next, current.mode)
                            },
                            onRetry = {
                                controls.resetAll()
                                screen = DesktopShellScreen.Playing(current.mission, current.mode)
                            },
                            onBackToMenu = exitToMainMenu,
                        )
                    }

                    DesktopShellScreen.Editor -> MainMenuScreen(
                        onStartNewGame = { screen = DesktopShellScreen.GameModeSelect },
                        onExit = ::exitApplication,
                    )
                }
            }
        }
    }
}

private fun GameMode.toMatchMode(): MatchMode = when (this) {
    GameMode.SINGLE_PLAYER_VS_COMPUTER -> MatchMode.SINGLE
    GameMode.PLAYER_VS_PLAYER -> MatchMode.DUAL
    GameMode.DUAL_PLAYER_VS_COMPUTER -> MatchMode.DUAL_VS_COMPUTER
}

@Composable
private fun GameplayScreen(
    mission: MissionEntry,
    mode: GameMode,
    controls: DesktopControls,
    onMissionEnd: (MissionOutcome, score: Int, kills: Int, captures: Int, time: Long) -> Unit,
) {
    val host = remember(mission.baseName, mode) {
        controls.mode = mode
        LocalMatchClient.fromSceneJson(mission.sceneJson, mission.sidecar, mode.toMatchMode(), recordAs = mission.baseName)
    }
    DisposableEffect(host) { onDispose(host::dispose) }
    val audio = remember { AudioManager(DesktopAudioBackend()) }
    DisposableEffect(audio) { onDispose(audio::dispose) }
    val eventQueues = remember(host) { List(2) { ViewportEventQueue() } }
    var serverFrame by remember(host) { mutableStateOf<ServerFrame>(host.currentServerFrame()) }
    val playerIds = if (mode == GameMode.SINGLE_PLAYER_VS_COMPUTER) listOf(0) else listOf(0, 1)

    var missionEndBanner by remember(host) { mutableStateOf<MissionOutcome?>(null) }

    LaunchedEffect(host) {
        var inputSequence = 0L
        var pendingEnd: (() -> Unit)? = null
        var bannerTicksLeft = 0L
        while (true) {
            for (playerId in playerIds) {
                host.submitInput(controls.toInputFrame(playerId = playerId, inputSequence = inputSequence))
            }
            inputSequence++
            val frame = host.tick()
            serverFrame = frame
            eventQueues.forEach { it.push(frame.world.events) }
            audio.processEvents(frame.world.events, frame.playerViews)
            val end = pendingEnd
            if (end != null) {
                // Legacy game.end_round = tinit(300): the game runs on under the banner for 3 s.
                if (--bannerTicksLeft <= 0) {
                    host.recorder?.let { RecordingStore.save(it.recording()) }
                    end()
                    break
                }
            } else {
                val outcome = frame.world.events.firstNotNullOfOrNull { event ->
                    when (event) {
                        is GameEvent.MissionWon -> MissionOutcome.WON
                        is GameEvent.MissionLost -> MissionOutcome.LOST
                        else -> null
                    }
                }
                val result = frame.world.events.filterIsInstance<GameEvent.MissionResult>().lastOrNull()
                if (outcome != null) {
                    missionEndBanner = outcome
                    bannerTicksLeft = MISSION_END_BANNER_MILLIS / FixedStepClock.MILLIS_PER_TICK
                    pendingEnd = {
                        onMissionEnd(
                            outcome,
                            result?.score ?: 0,
                            result?.kills ?: 0,
                            result?.captures ?: 0,
                            result?.ticks ?: 0,
                        )
                    }
                }
            }
            delay(FixedStepClock.MILLIS_PER_TICK)
        }
    }

    val worldWidth = mission.sidecar.metadata.widthTiles * LEGACY_TILE_SIZE
    val worldHeight = mission.sidecar.metadata.heightTiles * LEGACY_TILE_SIZE
    val views = playerIds.mapNotNull { id -> serverFrame.playerViews.firstOrNull { it.playerId == id } }

    TiledPanelBackground(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(32.dp)
                .fillMaxHeight()
                .aspectRatio(LEGACY_PLAYFIELD_ASPECT_RATIO),
        ) {
            // Legacy draw_screen(): the losing player's view turns grey under the banner.
            Box(modifier = Modifier.fillMaxSize().grayscale(missionEndBanner == MissionOutcome.LOST)) {
                if (views.size > 1) {
                    // Legacy dual.c / dualvc.c: two side-by-side views, one per player.
                    Row(modifier = Modifier.fillMaxSize()) {
                        views.forEachIndexed { index, view ->
                            PlayerPane(
                                mission = mission,
                                serverFrame = serverFrame,
                                playerView = view,
                                events = eventQueues[index],
                                worldWidth = worldWidth,
                                worldHeight = worldHeight,
                                playfieldWidth = SPLIT_PLAYFIELD_WIDTH,
                                compactHud = true,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                            if (index == 0) Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(RetroColors.PanelBorderOuter))
                        }
                    }
                } else {
                    views.firstOrNull()?.let { view ->
                        PlayerPane(
                            mission = mission,
                            serverFrame = serverFrame,
                            playerView = view,
                            events = eventQueues[0],
                            worldWidth = worldWidth,
                            worldHeight = worldHeight,
                            playfieldWidth = null,
                            compactHud = false,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            // Legacy write_winner_1p; player vs player (write_winner_2p's "YOU WIN") has no winner id yet.
            val banner = missionEndBanner
            if (banner != null && mode != GameMode.PLAYER_VS_PLAYER) {
                MissionEndBanner(outcome = banner)
            }
        }
    }
}

@Composable
private fun PlayerPane(
    mission: MissionEntry,
    serverFrame: ServerFrame,
    playerView: PlayerView,
    events: ViewportEventQueue,
    worldWidth: Int,
    worldHeight: Int,
    playfieldWidth: Int?,
    compactHud: Boolean,
    modifier: Modifier,
) {
    var viewportRect by remember { mutableStateOf(Rect.Zero) }
    var viewportGeometry by remember { mutableStateOf<ViewportGeometry?>(null) }
    Box(modifier = modifier) {
        TankArenaViewport(
            sidecar = mission.sidecar,
            serverFrame = serverFrame,
            playerId = playerView.playerId,
            worldWidth = worldWidth,
            worldHeight = worldHeight,
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    viewportRect = coordinates.boundsInParent()
                },
            events = events,
            playfieldWidth = playfieldWidth ?: playerView.cameraWidth,
            onGeometryChanged = { viewportGeometry = it },
        )
        val geometry = viewportGeometry
        if (geometry != null && viewportRect != Rect.Zero) {
            val sceneWidth = (playfieldWidth ?: playerView.cameraWidth).toFloat()
            val scale = viewportRect.width / sceneWidth
            HudOverlay(
                hudState = playerView.hud,
                playerView = playerView,
                playfield = visiblePlayfield(geometry, viewportRect, sceneWidth, playerView.cameraHeight.toFloat()),
                scale = scale,
                compact = compactHud,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (showDebugOverlay) {
            ViewportDebugOverlay(
                playerView = playerView,
                geometry = viewportGeometry,
                viewportRect = viewportRect,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Draws the content without colour (legacy `b_convert(scr, gray, ...)` for a lost mission). */
private fun Modifier.grayscale(enabled: Boolean): Modifier = if (!enabled) this else drawWithContent {
    val paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
    drawIntoCanvas { it.saveLayer(Rect(Offset.Zero, size), paint) }
    drawContent()
    drawIntoCanvas { it.restore() }
}

/**
 * The part of the viewport that shows the map, in pane pixels. Legacy maps smaller than the
 * 640×400 playfield are shown in a window of the map's own size, and the status display sits in
 * that window's corners.
 */
private fun visiblePlayfield(geometry: ViewportGeometry, viewportRect: Rect, sceneWidth: Float, sceneHeight: Float): Rect {
    val scale = viewportRect.width / sceneWidth
    val sceneLeft = geometry.cameraCenterX - sceneWidth / 2f
    val sceneTop = geometry.cameraCenterY - sceneHeight / 2f
    val left = maxOf(geometry.renderOffsetX, sceneLeft)
    val top = maxOf(geometry.renderOffsetY, sceneTop)
    val right = minOf(geometry.renderOffsetX + geometry.worldWidth, sceneLeft + sceneWidth)
    val bottom = minOf(geometry.renderOffsetY + geometry.worldHeight, sceneTop + sceneHeight)
    return Rect(
        left = viewportRect.left + (left - sceneLeft) * scale,
        top = viewportRect.top + (top - sceneTop) * scale,
        right = viewportRect.left + (right - sceneLeft) * scale,
        bottom = viewportRect.top + (bottom - sceneTop) * scale,
    )
}

@Composable
private fun ViewportDebugOverlay(
    playerView: PlayerView,
    geometry: ViewportGeometry?,
    viewportRect: Rect,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                color = Color(0xFF00D7FF),
                style = Stroke(width = 1f),
            )
            if (viewportRect != Rect.Zero) {
                drawRect(
                    color = Color(0xFFFFD54F),
                    topLeft = viewportRect.topLeft,
                    size = viewportRect.size,
                    style = Stroke(width = 2f),
                )
            }
            if (geometry != null && viewportRect != Rect.Zero) {
                val viewportSceneWidth = playerView.cameraWidth.toFloat()
                val viewportSceneHeight = playerView.cameraHeight.toFloat()
                val sceneLeft = geometry.cameraCenterX - viewportSceneWidth / 2f
                val sceneTop = geometry.cameraCenterY - viewportSceneHeight / 2f
                val scaleX = viewportRect.width / viewportSceneWidth
                val scaleY = viewportRect.height / viewportSceneHeight
                val worldLeft = viewportRect.left + (geometry.renderOffsetX - sceneLeft) * scaleX
                val worldTop = viewportRect.top + (geometry.renderOffsetY - sceneTop) * scaleY
                val worldWidthPx = geometry.worldWidth * scaleX
                val worldHeightPx = geometry.worldHeight * scaleY
                drawRect(
                    color = Color(0xFF4CAF50),
                    topLeft = Offset(worldLeft, worldTop),
                    size = androidx.compose.ui.geometry.Size(worldWidthPx, worldHeightPx),
                    style = Stroke(width = 2f),
                )
                val cameraCenter = Offset(
                    x = viewportRect.left + viewportRect.width / 2f,
                    y = viewportRect.top + viewportRect.height / 2f,
                )
                drawLine(
                    color = Color(0xFFFF5252),
                    start = Offset(cameraCenter.x - 8f, cameraCenter.y),
                    end = Offset(cameraCenter.x + 8f, cameraCenter.y),
                    strokeWidth = 2f,
                )
                drawLine(
                    color = Color(0xFFFF5252),
                    start = Offset(cameraCenter.x, cameraCenter.y - 8f),
                    end = Offset(cameraCenter.x, cameraCenter.y + 8f),
                    strokeWidth = 2f,
                )
            }
        }
        if (geometry != null) {
            Text(
                text = buildString {
                    append("viewport=")
                    append(viewportRect.width.toInt())
                    append("x")
                    append(viewportRect.height.toInt())
                    append(" scene=")
                    append(geometry.sceneWidth)
                    append("x")
                    append(geometry.sceneHeight)
                    append(" world=")
                    append(geometry.worldWidth)
                    append("x")
                    append(geometry.worldHeight)
                    append(" offset=")
                    append(geometry.renderOffsetX.toInt())
                    append(",")
                    append(geometry.renderOffsetY.toInt())
                    append(" camera=")
                    append(geometry.cameraCenterX.toInt())
                    append(",")
                    append(geometry.cameraCenterY.toInt())
                },
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .background(Color(0xCC101010))
                    .padding(6.dp),
            )
        }
    }
}

private fun handleGlobalKeys(
    event: KeyEvent,
    screen: DesktopShellScreen,
    controls: DesktopControls,
    onExitToMenu: () -> Unit,
): Boolean {
    return when (screen) {
        is DesktopShellScreen.Playing -> {
            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                onExitToMenu()
                true
            } else {
                controls.handle(event)
            }
        }
        else -> false
    }
}

/** One player's keys, in the order of the legacy setup program's key list. */
private class KeyBinding(
    val forward: Key,
    val reverse: Key,
    val turnLeft: Key,
    val turnRight: Key,
    val aimLeft: Key,
    val aimRight: Key,
    val fire: Key,
    val shield: Key,
    val weaponLeft: Key,
    val weaponRight: Key,
) {
    companion object {
        /** DATA/KEYS.HLP "Player one": K , M . drive, Z fire, A shield, S/D weapon, X/C turret. */
        val PLAYER_ONE = KeyBinding(
            forward = Key.K, reverse = Key.Comma, turnLeft = Key.M, turnRight = Key.Period,
            aimLeft = Key.X, aimRight = Key.C, fire = Key.Z, shield = Key.A,
            weaponLeft = Key.S, weaponRight = Key.D,
        )

        /** DATA/KEYS.HLP "Player two (or player for single player game)": the grey cursor block. */
        val CURSOR_BLOCK = KeyBinding(
            forward = Key.DirectionUp, reverse = Key.DirectionDown, turnLeft = Key.DirectionLeft, turnRight = Key.DirectionRight,
            aimLeft = Key.MoveEnd, aimRight = Key.PageDown, fire = Key.Delete, shield = Key.Insert,
            weaponLeft = Key.MoveHome, weaponRight = Key.PageUp,
        )

        /** Modern single-player layout kept from earlier builds. */
        val WASD = KeyBinding(
            forward = Key.W, reverse = Key.S, turnLeft = Key.A, turnRight = Key.D,
            aimLeft = Key.Q, aimRight = Key.E, fire = Key.CtrlLeft, shield = Key.ShiftLeft,
            weaponLeft = Key.R, weaponRight = Key.F,
        )
    }
}

private class PlayerControls {
    var forward = false
    var reverse = false
    var turnLeft = false
    var turnRight = false
    var aimLeft = false
    var aimRight = false
    var fire = false
    var shield = false
    var weaponLeft = false
    var weaponRight = false
    /** One-shot F1–F10 request; cleared once sent. */
    var selectTankId = 0

    fun apply(binding: KeyBinding, key: Key, pressed: Boolean): Boolean {
        when (key) {
            binding.forward -> forward = pressed
            binding.reverse -> reverse = pressed
            binding.turnLeft -> turnLeft = pressed
            binding.turnRight -> turnRight = pressed
            binding.aimLeft -> aimLeft = pressed
            binding.aimRight -> aimRight = pressed
            binding.fire -> fire = pressed
            binding.shield -> shield = pressed
            binding.weaponLeft -> weaponLeft = pressed
            binding.weaponRight -> weaponRight = pressed
            else -> return false
        }
        return true
    }
}

private val TANK_SELECT_KEYS: List<Key> = listOf(
    Key.F1, Key.F2, Key.F3, Key.F4, Key.F5, Key.F6, Key.F7, Key.F8, Key.F9, Key.F10,
)

private class DesktopControls {
    var mode: GameMode = GameMode.SINGLE_PLAYER_VS_COMPUTER
    private val players = List(2) { PlayerControls() }

    /** Single player uses the legacy cursor block (or WASD); two players split the keyboard. */
    private fun bindingsFor(playerId: Int): List<KeyBinding> = when {
        mode == GameMode.SINGLE_PLAYER_VS_COMPUTER -> if (playerId == 0) listOf(KeyBinding.CURSOR_BLOCK, KeyBinding.WASD) else emptyList()
        playerId == 0 -> listOf(KeyBinding.PLAYER_ONE)
        else -> listOf(KeyBinding.CURSOR_BLOCK)
    }

    fun handle(event: KeyEvent): Boolean {
        val pressed = event.type == KeyEventType.KeyDown
        val tankId = TANK_SELECT_KEYS.indexOf(event.key) + 1
        if (tankId > 0) {
            // Legacy controls.c: F1–F10 switch player one's tank, Alt+F-key player two's in co-op.
            if (pressed && mode != GameMode.PLAYER_VS_PLAYER) {
                val player = if (event.isAltPressed && mode == GameMode.DUAL_PLAYER_VS_COMPUTER) 1 else 0
                players[player].selectTankId = tankId
            }
            return true
        }
        var handled = false
        for (playerId in players.indices) {
            for (binding in bindingsFor(playerId)) {
                if (players[playerId].apply(binding, event.key, pressed)) handled = true
            }
        }
        return handled
    }

    fun toInputFrame(playerId: Int, inputSequence: Long): InputFrame {
        val p = players[playerId]
        val selectTankId = p.selectTankId
        p.selectTankId = 0
        return InputFrame(
            playerId = playerId,
            inputSequence = inputSequence,
            forward = p.forward,
            reverse = p.reverse,
            turnLeft = p.turnLeft,
            turnRight = p.turnRight,
            aimLeft = p.aimLeft,
            aimRight = p.aimRight,
            firePrimary = p.fire,
            shield = p.shield,
            cycleWeaponLeft = p.weaponLeft,
            cycleWeaponRight = p.weaponRight,
            selectTankId = selectTankId,
        )
    }

    fun resetAll() {
        players.forEach { p ->
            p.forward = false
            p.reverse = false
            p.turnLeft = false
            p.turnRight = false
            p.aimLeft = false
            p.aimRight = false
            p.fire = false
            p.shield = false
            p.weaponLeft = false
            p.weaponRight = false
            p.selectTankId = 0
        }
    }
}

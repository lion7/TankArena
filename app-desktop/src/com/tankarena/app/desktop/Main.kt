package com.tankarena.app.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.tankarena.content.AuthoredObject
import com.tankarena.content.CanonicalMapDefinition
import com.tankarena.content.ObjectKinds
import com.tankarena.core.FixedStepClock
import com.tankarena.protocol.InputFrame
import com.tankarena.protocol.snapshot.GameEvent
import com.tankarena.protocol.snapshot.PlayerView
import com.tankarena.protocol.snapshot.ServerFrame
import com.tankarena.render.kubriko.LEGACY_PLAYFIELD_ASPECT_RATIO
import com.tankarena.render.kubriko.TankArenaViewport
import com.tankarena.render.kubriko.ViewportGeometry
import com.tankarena.sim.MissionMode
import com.tankarena.sim.runtime.LocalMatchHost
import com.tankarena.ui.compose.DesktopShellScreen
import com.tankarena.ui.compose.GameMode
import com.tankarena.ui.compose.MissionOutcome
import com.tankarena.ui.compose.hud.HudOverlay
import com.tankarena.ui.compose.hud.RadarOverlay
import com.tankarena.ui.compose.menu.DebriefScreen
import com.tankarena.ui.compose.menu.GameModeMenuScreen
import com.tankarena.ui.compose.menu.MainMenuScreen
import com.tankarena.ui.compose.menu.MissionCatalog
import com.tankarena.ui.compose.menu.MissionEntry
import com.tankarena.ui.compose.menu.MissionSelectScreen
import com.tankarena.ui.compose.menu.RetroColors
import com.tankarena.ui.compose.menu.TiledPanelBackground
import com.tankarena.ui.compose.menu.findByCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun main() = application {
    val controls = remember { DesktopControls() }
    var screen by remember { mutableStateOf<DesktopShellScreen>(DesktopShellScreen.MainMenu) }
    var selectedMode by remember { mutableStateOf(GameMode.SINGLE_PLAYER_VS_COMPUTER) }
    var missions by remember { mutableStateOf<List<MissionEntry>?>(null) }
    val coroutineScope = rememberCoroutineScope()
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
                    )

                    DesktopShellScreen.GameModeSelect -> {
                        LaunchedEffect(Unit) {
                            if (missions == null) {
                                coroutineScope.launch {
                                    missions = MissionCatalog.load(MissionCatalog.resolveDefaultMapsDir())
                                }
                            }
                        }
                        GameModeMenuScreen(
                            onSelectPlayerVsPlayer = {
                                selectedMode = GameMode.PLAYER_VS_PLAYER
                                screen = DesktopShellScreen.MissionSelect
                            },
                            onSelectSinglePlayer = {
                                selectedMode = GameMode.SINGLE_PLAYER_VS_COMPUTER
                                screen = DesktopShellScreen.MissionSelect
                            },
                            onSelectDualVsComputer = {
                                selectedMode = GameMode.DUAL_PLAYER_VS_COMPUTER
                                screen = DesktopShellScreen.MissionSelect
                            },
                            onCancel = { screen = DesktopShellScreen.MainMenu },
                        )
                    }

                    DesktopShellScreen.MissionSelect -> {
                        LaunchedEffect(Unit) {
                            if (missions == null) {
                                missions = MissionCatalog.load(MissionCatalog.resolveDefaultMapsDir())
                            }
                        }
                        MissionSelectScreen(
                            missions = missions,
                            onSelect = { mission ->
                                controls.resetAll()
                                screen = DesktopShellScreen.Playing(mission, selectedMode)
                            },
                            onCancel = { screen = DesktopShellScreen.GameModeSelect },
                        )
                    }

                    is DesktopShellScreen.Playing -> GameplayScreen(
                        mission = current.mission,
                        mode = current.mode,
                        controls = controls,
                        onMissionEnd = { outcome ->
                            controls.resetAll()
                            screen = DesktopShellScreen.Debrief(current.mission, current.mode, outcome)
                        },
                    )

                    is DesktopShellScreen.Debrief -> {
                        val nextMission = missions.orEmpty().findByCode(current.mission.canonical.metadata.nextMissionCode)
                        DebriefScreen(
                            mission = current.mission,
                            outcome = current.outcome,
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

@Composable
private fun GameplayScreen(
    mission: MissionEntry,
    mode: GameMode,
    controls: DesktopControls,
    onMissionEnd: (MissionOutcome) -> Unit,
) {
    val map = remember(mission.mapFile, mode) { materializePlayableMission(mission.canonical, mode) }
    val host = remember(map, mode) { LocalMatchHost(map = map, mode = mode.toMissionMode()) }
    var serverFrame by remember(host) { mutableStateOf<ServerFrame>(host.currentServerFrame()) }
    var viewportRect by remember { mutableStateOf(Rect.Zero) }
    var viewportGeometry by remember { mutableStateOf<ViewportGeometry?>(null) }
    val playerView = serverFrame.playerViews.firstOrNull()

    LaunchedEffect(host) {
        var inputSequence = 0L
        var consumed = false
        while (true) {
            host.submitInput(controls.toInputFrame(playerId = 0, inputSequence = inputSequence++))
            serverFrame = host.tick()
            if (!consumed) {
                val outcome = serverFrame.world.events.firstNotNullOfOrNull { event ->
                    when (event) {
                        is GameEvent.MissionWon -> MissionOutcome.WON
                        is GameEvent.MissionLost -> MissionOutcome.LOST
                        else -> null
                    }
                }
                if (outcome != null) {
                    consumed = true
                    onMissionEnd(outcome)
                }
            }
            delay(FixedStepClock.MILLIS_PER_TICK)
        }
    }

    TiledPanelBackground(modifier = Modifier.fillMaxSize()) {
        val worldWidth = map.metadata.widthTiles * 33
        val worldHeight = map.metadata.heightTiles * 33
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(32.dp)
                .fillMaxHeight()
                .aspectRatio(LEGACY_PLAYFIELD_ASPECT_RATIO)
                .border(2.dp, RetroColors.PanelBorderOuter, RectangleShape)
                .padding(2.dp)
                .background(Color.Black),
        ) {
            if (playerView != null) {
                TankArenaViewport(
                    map = map,
                    serverFrame = serverFrame,
                    playerId = playerView.playerId,
                    worldWidth = worldWidth,
                    worldHeight = worldHeight,
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coordinates ->
                            viewportRect = coordinates.boundsInParent()
                        },
                    onGeometryChanged = { viewportGeometry = it },
                )
                HudOverlay(
                    hudState = playerView.hud,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 6.dp),
                )
                RadarOverlay(
                    playerView = playerView,
                    worldWidth = worldWidth,
                    worldHeight = worldHeight,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                )
                ViewportDebugOverlay(
                    playerView = playerView,
                    geometry = viewportGeometry,
                    viewportRect = viewportRect,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
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

private fun GameMode.toMissionMode(): MissionMode = when (this) {
    GameMode.PLAYER_VS_PLAYER -> MissionMode.PLAYER_VS_PLAYER
    GameMode.SINGLE_PLAYER_VS_COMPUTER -> MissionMode.SINGLE_PLAYER_VS_COMPUTER
    GameMode.DUAL_PLAYER_VS_COMPUTER -> MissionMode.DUAL_VS_COMPUTER
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

private class DesktopControls {
    private var forward: Boolean = false
    private var reverse: Boolean = false
    private var turnLeft: Boolean = false
    private var turnRight: Boolean = false
    private var aimLeft: Boolean = false
    private var aimRight: Boolean = false
    private var firePrimary: Boolean = false
    private var fireSecondary: Boolean = false

    fun handle(event: KeyEvent): Boolean {
        val pressed = event.type == KeyEventType.KeyDown
        when (event.key) {
            Key.W -> forward = pressed
            Key.S -> reverse = pressed
            Key.A -> turnLeft = pressed
            Key.D -> turnRight = pressed
            Key.Q -> aimLeft = pressed
            Key.E -> aimRight = pressed
            Key.CtrlLeft -> firePrimary = pressed
            Key.ShiftLeft -> fireSecondary = pressed
            else -> return false
        }
        return true
    }

    fun toInputFrame(playerId: Int, inputSequence: Long): InputFrame = InputFrame(
        playerId = playerId,
        inputSequence = inputSequence,
        forward = forward,
        reverse = reverse,
        turnLeft = turnLeft,
        turnRight = turnRight,
        aimLeft = aimLeft,
        aimRight = aimRight,
        firePrimary = firePrimary,
        fireSecondary = fireSecondary,
    )

    fun resetAll() {
        forward = false
        reverse = false
        turnLeft = false
        turnRight = false
        aimLeft = false
        aimRight = false
        firePrimary = false
        fireSecondary = false
    }
}

private fun materializePlayableMission(map: CanonicalMapDefinition, mode: GameMode): CanonicalMapDefinition {
    if (!map.metadata.missionCode.equals("BEGIN1", ignoreCase = true)) return map
    val objects = buildList {
        add(
            AuthoredObject(
                id = "begin1-player",
                kind = ObjectKinds.PLAYER_START,
                x = 2 * 33 + 16,
                y = 9 * 33 + 16,
                properties = mapOf(
                    "direction" to "0",
                    "lives" to if (mode == GameMode.SINGLE_PLAYER_VS_COMPUTER) "3" else "1",
                ),
            ),
        )
        add(
            AuthoredObject(
                id = "begin1-enforcer",
                kind = ObjectKinds.ENFORCER,
                x = 9 * 33 + 16,
                y = 2 * 33 + 16,
                properties = mapOf(
                    "direction" to "8",
                    "tankType" to "0",
                    "armor" to "100",
                    "lives" to "1",
                ),
            ),
        )
        if (mode == GameMode.PLAYER_VS_PLAYER || mode == GameMode.DUAL_PLAYER_VS_COMPUTER) {
            add(
                AuthoredObject(
                    id = "begin1-player-2",
                    kind = ObjectKinds.PLAYER_START,
                    x = 3 * 33 + 16,
                    y = 9 * 33 + 16,
                    properties = mapOf("direction" to "0", "lives" to "3"),
                ),
            )
        }
    }
    return map.copy(objects = objects)
}

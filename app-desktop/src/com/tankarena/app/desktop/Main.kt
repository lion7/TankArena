package com.tankarena.app.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.tankarena.ui.compose.hud.HudOverlay
import kotlin.math.sign
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.tankarena.content.CanonicalMapDefinition
import com.tankarena.core.FixedStepClock
import com.tankarena.input.PlayerIntentFrame
import com.tankarena.render.kubriko.TankArenaViewport
import com.tankarena.sim.MissionMode
import com.tankarena.sim.SimulationEvent
import com.tankarena.sim.SimulationFactory
import com.tankarena.sim.WorldState
import com.tankarena.ui.compose.DesktopShellScreen
import com.tankarena.ui.compose.GameMode
import com.tankarena.ui.compose.MissionOutcome
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
            handleGlobalKeys(
                event = event,
                screen = screen,
                controls = controls,
                onExitToMenu = exitToMainMenu,
            )
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
                            onSelectPlayerVsPlayer = { screen = DesktopShellScreen.MissionSelect },
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
                                screen = DesktopShellScreen.Playing(mission, GameMode.SINGLE_PLAYER_VS_COMPUTER)
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
                        val nextMission = missions
                            .orEmpty()
                            .findByCode(current.mission.canonical.metadata.nextMissionCode)
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
    val map: CanonicalMapDefinition = mission.canonical
    val simulation = remember(mission.mapFile, mode) {
        SimulationFactory.fromCanonicalMap(map, mode = mode.toMissionMode())
    }
    var worldState by remember(simulation) { mutableStateOf<WorldState>(simulation.currentState()) }

    LaunchedEffect(simulation) {
        var consumed = false
        while (true) {
            val result = simulation.tick(mapOf(0 to controls.toIntentFrame()))
            worldState = result.current
            if (!consumed) {
                val outcome = result.events.firstNotNullOfOrNull { event ->
                    when (event) {
                        SimulationEvent.MissionWon -> MissionOutcome.WON
                        SimulationEvent.MissionLost -> MissionOutcome.LOST
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
        val aspectRatio = map.metadata.widthTiles.toFloat() /
            map.metadata.heightTiles.toFloat().coerceAtLeast(1f)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(32.dp)
                .fillMaxHeight()
                .aspectRatio(aspectRatio)
                .border(width = 2.dp, color = RetroColors.PanelBorderOuter, shape = RectangleShape)
                .padding(2.dp)
                .background(Color.Black)
                .onSizeChanged { size -> controls.updateViewportSize(size.width, size.height) }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Move ||
                                event.type == PointerEventType.Enter ||
                                event.type == PointerEventType.Press
                            ) {
                                val pos = event.changes.firstOrNull()?.position
                                if (pos != null) {
                                    controls.updateMousePosition(pos.x, pos.y)
                                }
                            }
                        }
                    }
                },
        ) {
            TankArenaViewport(
                map = map,
                worldState = worldState,
                modifier = Modifier.fillMaxSize(),
            )
            HudOverlay(
                world = worldState,
                missionCode = map.metadata.missionCode,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp),
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
    var throttle: Int = 0
    var steer: Int = 0
    var firePrimary: Boolean = false

    private var aimX: Int = 0
    private var aimY: Int = -1
    private var viewportWidthPx: Int = 0
    private var viewportHeightPx: Int = 0
    private var mouseX: Float = 0f
    private var mouseY: Float = 0f

    fun handle(event: KeyEvent): Boolean {
        val pressed = event.type == KeyEventType.KeyDown
        when (event.key) {
            Key.DirectionUp, Key.W -> throttle = if (pressed) -1 else if (throttle == -1) 0 else throttle
            Key.DirectionDown, Key.S -> throttle = if (pressed) 1 else if (throttle == 1) 0 else throttle
            Key.DirectionLeft, Key.A -> steer = if (pressed) -1 else if (steer == -1) 0 else steer
            Key.DirectionRight, Key.D -> steer = if (pressed) 1 else if (steer == 1) 0 else steer
            Key.Spacebar -> firePrimary = pressed
            else -> return false
        }
        return true
    }

    fun updateViewportSize(width: Int, height: Int) {
        viewportWidthPx = width
        viewportHeightPx = height
        recomputeAim()
    }

    fun updateMousePosition(x: Float, y: Float) {
        mouseX = x
        mouseY = y
        recomputeAim()
    }

    private fun recomputeAim() {
        if (viewportWidthPx <= 0 || viewportHeightPx <= 0) return
        // Camera follows tank 0, so the player tank renders at viewport center.
        val dx = mouseX - viewportWidthPx / 2f
        val dy = mouseY - viewportHeightPx / 2f
        // Apply a small dead zone so the turret does not jitter when the cursor sits near the tank.
        val deadZone = 12f
        val newAimX = if (kotlin.math.abs(dx) < deadZone) 0 else dx.sign.toInt()
        val newAimY = if (kotlin.math.abs(dy) < deadZone) 0 else dy.sign.toInt()
        // Avoid (0, 0) which would tell the sim "no aim"; preserve previous direction in that case.
        if (newAimX != 0 || newAimY != 0) {
            aimX = newAimX
            aimY = newAimY
        }
    }

    fun toIntentFrame(): PlayerIntentFrame {
        val fireNow = firePrimary
        firePrimary = false
        return PlayerIntentFrame(
            throttle = throttle,
            steer = steer,
            firePrimary = fireNow,
            aimX = aimX,
            aimY = aimY,
        )
    }

    fun resetAll() {
        throttle = 0
        steer = 0
        firePrimary = false
        aimX = 0
        aimY = -1
        mouseX = 0f
        mouseY = 0f
    }
}

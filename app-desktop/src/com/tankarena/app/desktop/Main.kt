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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.tankarena.content.CanonicalMapDefinition
import com.tankarena.core.FixedStepClock
import com.tankarena.input.PlayerIntentFrame
import com.tankarena.render.kubriko.TankArenaViewport
import com.tankarena.sim.SimulationFactory
import com.tankarena.sim.WorldState
import com.tankarena.ui.compose.DesktopShellScreen
import com.tankarena.ui.compose.GameMode
import com.tankarena.ui.compose.menu.GameModeMenuScreen
import com.tankarena.ui.compose.menu.MainMenuScreen
import com.tankarena.ui.compose.menu.MissionCatalog
import com.tankarena.ui.compose.menu.MissionEntry
import com.tankarena.ui.compose.menu.MissionSelectScreen
import com.tankarena.ui.compose.menu.RetroColors
import com.tankarena.ui.compose.menu.TiledPanelBackground
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
                                screen = DesktopShellScreen.Playing(mission, GameMode.PLAYER_VS_PLAYER)
                            },
                            onCancel = { screen = DesktopShellScreen.GameModeSelect },
                        )
                    }

                    is DesktopShellScreen.Playing -> GameplayScreen(
                        mission = current.mission,
                        controls = controls,
                    )

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
    controls: DesktopControls,
) {
    val map: CanonicalMapDefinition = mission.canonical
    val simulation = remember(mission.mapFile) { SimulationFactory.fromCanonicalMap(map) }
    var worldState by remember(simulation) { mutableStateOf<WorldState>(simulation.currentState()) }

    LaunchedEffect(simulation) {
        while (true) {
            worldState = simulation.tick(mapOf(0 to controls.toIntentFrame())).current
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
                .background(Color.Black),
        ) {
            TankArenaViewport(
                map = map,
                worldState = worldState,
                modifier = Modifier.fillMaxSize(),
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

private class DesktopControls {
    var throttle: Int = 0
    var steer: Int = 0
    var firePrimary: Boolean = false

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

    fun toIntentFrame(): PlayerIntentFrame {
        val fireNow = firePrimary
        firePrimary = false
        return PlayerIntentFrame(
            throttle = throttle,
            steer = steer,
            firePrimary = fireNow,
            aimY = if (throttle != 0) throttle else 0,
            aimX = if (steer != 0) steer else 0,
        )
    }

    fun resetAll() {
        throttle = 0
        steer = 0
        firePrimary = false
    }
}

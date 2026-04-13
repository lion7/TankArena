package com.tankarena.app.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.tankarena.content.CanonicalMapDefinition
import com.tankarena.core.FixedStepClock
import com.tankarena.input.PlayerIntentFrame
import com.tankarena.legacy.LegacyMapParser
import com.tankarena.render.kubriko.TankArenaViewport
import com.tankarena.sim.SimulationFactory
import com.tankarena.sim.TankArenaSimulation
import com.tankarena.sim.WorldState
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

fun main(args: Array<String>) = application {
    val loadedMap = loadMap(arrayOf("MAPS/BEGIN1.MAP"))
    val simulation = remember(loadedMap) {
        loadedMap?.let(SimulationFactory::fromCanonicalMap)
            ?: TankArenaSimulation.firstMilestonePrototype(
                widthPixels = 640 * 2,
                heightPixels = 400 * 2,
            )
    }
    val controls = remember { DesktopControls() }
    var worldState by remember(simulation) { mutableStateOf(simulation.currentState()) }

    LaunchedEffect(simulation, controls) {
        while (true) {
            worldState = simulation.tick(mapOf(0 to controls.toIntentFrame())).current
            delay(FixedStepClock.MILLIS_PER_TICK)
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Tank Arena Rewrite",
        onPreviewKeyEvent = { event -> controls.handle(event) },
    ) {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                DesktopShell(
                    map = loadedMap,
                    worldState = worldState,
                    controls = controls,
                )
            }
        }
    }
}

@Composable
private fun DesktopShell(
    map: CanonicalMapDefinition?,
    worldState: WorldState,
    controls: DesktopControls,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101521))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.28f)
                .background(Color(0xFF192132))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Tank Arena Rewrite", color = Color.White, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = buildString {
                    append("Map: ")
                    append(map?.metadata?.name ?: "Prototype")
                    append("\n")
                    append("Mission: ")
                    append(map?.metadata?.missionCode ?: "N/A")
                    append("\n")
                    append("Tick: ")
                    append(worldState.tick)
                    append("\n")
                    append("Tanks: ")
                    append(worldState.tanks.size)
                    append("\n")
                    append("Turrets: ")
                    append(worldState.turrets.size)
                    append("\n")
                    append("Projectiles: ")
                    append(worldState.projectiles.size)
                },
                color = Color(0xFFBED0E8),
            )
            Text(
                text = "Controls: arrows or WASD to move, space to fire.",
                color = Color(0xFF9FB2CA),
            )
            Button(
                onClick = {
                    controls.firePrimary = true
                },
            ) {
                Text("Fire Once")
            }
            Button(
                onClick = {
                    controls.resetMovement()
                },
            ) {
                Text("Stop")
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .background(Color(0xFF0B1020))
                .padding(8.dp),
        ) {
            TankArenaViewport(
                map = map,
                worldState = worldState,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun loadMap(args: Array<String>): CanonicalMapDefinition? {
    if (args.isEmpty()) return null
    val input = File(args[0]).absoluteFile
    return when {
        args[0].endsWith(".map", ignoreCase = true) -> {
            val parser = LegacyMapParser()
            parser.toCanonical(input.nameWithoutExtension, parser.parse(input.readBytes(), input.nameWithoutExtension))
        }

        args[0].endsWith(".json", ignoreCase = true) -> {
            json.decodeFromString<CanonicalMapDefinition>(input.readText())
        }

        else -> null
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

    fun resetMovement() {
        throttle = 0
        steer = 0
    }
}

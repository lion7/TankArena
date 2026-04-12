package com.tankarena.app.desktop

import com.tankarena.content.CanonicalMapDefinition
import com.tankarena.legacy.LegacyMapParser
import com.tankarena.render.kubriko.TankArenaRuntimeProjector
import com.tankarena.sim.SimulationFactory
import com.tankarena.sim.TankArenaSimulation
import java.io.File
import kotlinx.serialization.json.Json

fun main(args: Array<String>) {
    val simulation = when {
        args.isNotEmpty() && args[0].endsWith(".map", ignoreCase = true) -> {
            val input = File(args[0])
            val parser = LegacyMapParser()
            val canonical = parser.toCanonical(input.nameWithoutExtension, parser.parse(input.readBytes(), input.nameWithoutExtension))
            SimulationFactory.fromCanonicalMap(canonical)
        }

        args.isNotEmpty() && args[0].endsWith(".json", ignoreCase = true) -> {
            val input = File(args[0])
            val canonical = Json { ignoreUnknownKeys = true }.decodeFromString<CanonicalMapDefinition>(input.readText())
            SimulationFactory.fromCanonicalMap(canonical)
        }

        else -> TankArenaSimulation.firstMilestonePrototype(
            widthPixels = 640 * 2,
            heightPixels = 400 * 2,
        )
    }
    val projector = TankArenaRuntimeProjector()
    val projection = projector.project(simulation.currentState())
    println(
        "Tank Arena desktop scaffold started at tick=${projection.tick} " +
            "with ${projection.world.tanks.size} tank(s) and ${projection.world.turrets.size} turret(s)."
    )
}

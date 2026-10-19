package com.tankarena.sim.kubriko.server

import com.tankarena.content.MapSceneSidecar
import com.tankarena.content.ShippedScenes
import com.tankarena.sim.kubriko.server.legacy.CanonicalMapDefinition
import kotlinx.serialization.json.Json

private val sidecarJson = Json {
    prettyPrint = true
    encodeDefaults = true
}

object LegacyToSceneJson {

    data class Output(val sceneJson: String, val sidecarJson: String)

    fun convert(map: CanonicalMapDefinition): Output {
        val sceneJson = CanonicalSceneBuilder.buildSceneJson(map)
        val sidecar = MapSceneSidecar(
            metadata = map.metadata,
            missionText = map.missionText,
            tileLayers = map.layers,
            importNotes = map.importNotes,
        )
        return Output(
            sceneJson = sceneJson,
            sidecarJson = sidecarJson.encodeToString(sidecar),
        )
    }

    fun parseSidecar(json: String): MapSceneSidecar = ShippedScenes.parseSidecar(json)
}

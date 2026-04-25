package com.tankarena.sim.kubriko.server

import com.pandulapeter.kubriko.serialization.SerializableMetadata
import com.tankarena.content.CanonicalMapDefinition
import com.tankarena.content.MapSceneSidecar
import kotlinx.serialization.json.Json

private val sidecarJson = Json {
    prettyPrint = true
    encodeDefaults = true
}

object LegacyToSceneJson {

    data class Output(val sceneJson: String, val sidecarJson: String)

    fun convert(map: CanonicalMapDefinition): Output {
        val serializationManager = SerializableMetadata.newSerializationManagerInstance(
            *tankArenaSerializableMetadata,
        )
        val sceneJson = CanonicalSceneBuilder.buildSceneJson(map, serializationManager)
        val sidecar = MapSceneSidecar(
            metadata = map.metadata,
            missionText = map.missionText,
            importNotes = map.importNotes,
        )
        return Output(
            sceneJson = sceneJson,
            sidecarJson = sidecarJson.encodeToString(sidecar),
        )
    }

    fun parseSidecar(json: String): MapSceneSidecar =
        Json { ignoreUnknownKeys = true }.decodeFromString(MapSceneSidecar.serializer(), json)
}

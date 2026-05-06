package com.tankarena.sim.kubriko.server

import com.pandulapeter.kubriko.serialization.SerializableMetadata
import com.tankarena.content.MapSceneSidecar
import com.tankarena.content.SCENE_SCHEMA_VERSION
import com.tankarena.sim.kubriko.server.legacy.CanonicalMapDefinition
import kotlinx.serialization.json.Json

private val sidecarJson = Json {
    prettyPrint = true
    encodeDefaults = true
}

private val sidecarParseJson = Json { ignoreUnknownKeys = true }

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
            tileLayers = map.layers,
            importNotes = map.importNotes,
        )
        return Output(
            sceneJson = sceneJson,
            sidecarJson = sidecarJson.encodeToString(sidecar),
        )
    }

    fun parseSidecar(json: String): MapSceneSidecar {
        val sidecar = sidecarParseJson.decodeFromString(MapSceneSidecar.serializer(), json)
        require(sidecar.schemaVersion <= SCENE_SCHEMA_VERSION) {
            "Scene sidecar schemaVersion=${sidecar.schemaVersion} is newer than the runtime " +
                "schemaVersion=$SCENE_SCHEMA_VERSION; refusing to load."
        }
        return sidecar
    }
}

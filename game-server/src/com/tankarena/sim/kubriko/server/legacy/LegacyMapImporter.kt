package com.tankarena.sim.kubriko.server.legacy

import com.pandulapeter.kubriko.serialization.SerializableMetadata
import com.tankarena.content.MapSceneSidecar
import com.tankarena.legacy.LegacyMapData
import com.tankarena.legacy.LegacyMapParser
import com.tankarena.sim.kubriko.server.CanonicalSceneBuilder
import com.tankarena.sim.kubriko.server.tankArenaSerializableMetadata

/**
 * Public entry point for ingesting legacy `.MAP` bytes. Returns the artifacts that the runtime
 * client and the in-process server actually consume: a [MapSceneSidecar] (metadata, mission
 * text, tile layers) and the Kubriko scene JSON containing the placed actors.
 */
data class ImportedMission(
    val sidecar: MapSceneSidecar,
    val sceneJson: String,
)

object LegacyMapImporter {

    fun import(bytes: ByteArray, mapName: String): ImportedMission {
        val parsed: LegacyMapData = LegacyMapParser().parse(bytes, mapName)
        val canonical = LegacyCanonicalConverter.convert(mapName, parsed)
        val serializationManager = SerializableMetadata.newSerializationManagerInstance(
            *tankArenaSerializableMetadata,
        )
        val sceneJson = CanonicalSceneBuilder.buildSceneJson(canonical, serializationManager)
        return ImportedMission(
            sidecar = MapSceneSidecar(
                metadata = canonical.metadata,
                missionText = canonical.missionText,
                tileLayers = canonical.layers,
                importNotes = canonical.importNotes,
            ),
            sceneJson = sceneJson,
        )
    }
}

package com.tankarena.content

import kotlinx.serialization.Serializable

/**
 * Schema version for [MapSceneSidecar]. Bump on breaking changes to the sidecar layout
 * (tile-layer encoding, metadata keys, etc.). Loaders must reject sidecars whose
 * [MapSceneSidecar.schemaVersion] is newer than this constant.
 */
const val SCENE_SCHEMA_VERSION: Int = 1

@Serializable
data class MapSceneSidecar(
    val metadata: MapMetadata,
    val missionText: MissionText = MissionText(),
    val tileLayers: TileLayers? = null,
    val importNotes: List<String> = emptyList(),
    val schemaVersion: Int = SCENE_SCHEMA_VERSION,
)

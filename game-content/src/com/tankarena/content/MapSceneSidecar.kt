package com.tankarena.content

import kotlinx.serialization.Serializable

@Serializable
data class MapSceneSidecar(
    val metadata: MapMetadata,
    val missionText: MissionText = MissionText(),
    val tileLayers: TileLayers? = null,
    val importNotes: List<String> = emptyList(),
)

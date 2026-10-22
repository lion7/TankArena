package nl.tankarena.content

import kotlinx.serialization.Serializable

/** A map's header, mission texts and tile layers: everything but its objects. */
@Serializable
data class MapSceneSidecar(
    val metadata: MapMetadata,
    val missionText: MissionText = MissionText(),
    val tileLayers: TileLayers? = null,
)

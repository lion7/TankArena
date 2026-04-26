package com.tankarena.sim.kubriko.server.legacy

import com.tankarena.content.GameModeCompatibility
import com.tankarena.content.MapMetadata
import com.tankarena.content.MissionText
import com.tankarena.content.TankArenaWorld
import com.tankarena.content.TileLayers
import com.tankarena.legacy.LegacyMapData

object LegacyCanonicalConverter {

    fun convert(mapName: String, legacy: LegacyMapData): CanonicalMapDefinition {
        val parsedObjects = LegacyObjectParser.parseObjects(
            blob = legacy.rawObjectBlobSize.takeIf { it > 0 }?.let { legacy.rawObjectBlob } ?: ByteArray(0),
            objectSize = legacy.header.objectSize,
        )
        val notes = buildList {
            add("Imported from legacy .MAP file.")
            if (legacy.rawObjectBlobSize > 0) {
                add("Legacy object blob decoded for a supported subset of object types.")
                add("Remaining raw object bytes: ${legacy.rawObjectBlobSize}.")
            }
            addAll(parsedObjects.notes)
        }
        return CanonicalMapDefinition(
            metadata = MapMetadata(
                name = mapName,
                widthTiles = legacy.header.widthTiles,
                heightTiles = legacy.header.heightTiles,
                missionCode = legacy.header.missionCode,
                nextMissionCode = legacy.header.nextMissionCode.ifBlank { null },
                randomBonus = legacy.header.randomBonus,
                world = legacy.header.background.toWorld(),
                night = legacy.header.night,
                publicPassword = legacy.header.publicPassword,
                modeCompatibility = legacy.header.mapType.toModeCompatibility(),
                legacyMapVersion = legacy.header.mapVersion,
            ),
            layers = TileLayers(
                base = legacy.terrain0,
                solid = legacy.terrain1,
                top = legacy.terrain2,
                goalLayer = legacy.goalLayer,
                bonusLayer = legacy.bonusLayer,
                manTypeLayer = legacy.manTypeLayer,
                manAmountLayer = legacy.manAmountLayer,
            ),
            missionText = MissionText(
                briefing = legacy.briefingText,
                success = legacy.successText,
                failure = legacy.failureText,
            ),
            objects = parsedObjects.objects,
            importNotes = notes,
        )
    }

    private fun Int.toWorld(): TankArenaWorld = when (this) {
        1 -> TankArenaWorld.TEMPERATE
        2 -> TankArenaWorld.CITY
        3 -> TankArenaWorld.NIGHT
        else -> TankArenaWorld.DESERT
    }

    private fun Int.toModeCompatibility(): GameModeCompatibility = when (this) {
        1 -> GameModeCompatibility.DUAL
        2 -> GameModeCompatibility.SINGLE
        3 -> GameModeCompatibility.DUAL_VS_COMPUTER
        4 -> GameModeCompatibility.SINGLE_OR_DUAL
        else -> GameModeCompatibility.DONT_CARE
    }
}

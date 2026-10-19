package com.tankarena.sim.map

import com.tankarena.content.MapSceneSidecar
import com.tankarena.legacy.LegacyMapParser
import com.tankarena.sim.CanonicalSceneBuilder
import com.tankarena.sim.legacy.LegacyCanonicalConverter

/** Reads an original `.MAP` file into the native format (ADR 0003, ADR 0022). */
object LegacyMapImport {
    /** The imported map, and anything in the `.MAP` the importer could not represent. */
    class Result(val document: MapDocument, val notes: List<String>)

    fun import(bytes: ByteArray, mapName: String): Result {
        val canonical = LegacyCanonicalConverter.convert(mapName, LegacyMapParser().parse(bytes, mapName))
        val objects = CanonicalSceneBuilder.buildActors(canonical)
        val sidecar = MapSceneSidecar(metadata = canonical.metadata, missionText = canonical.missionText, tileLayers = canonical.layers)
        return Result(MapConversion.toDocument(LoadedMap(sidecar, objects)), canonical.importNotes)
    }

    /** The file name a map gets in the game: lower case, other characters than a-z, 0-9, - and _ replaced. */
    fun baseName(mapName: String): String = mapName.lowercase().replace(Regex("[^a-z0-9_-]+"), "_").trim('_')
}

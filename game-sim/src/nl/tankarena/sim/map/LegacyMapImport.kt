package nl.tankarena.sim.map

import nl.tankarena.content.MapSceneSidecar
import nl.tankarena.legacy.LegacyMapParser
import nl.tankarena.sim.CanonicalSceneBuilder
import nl.tankarena.sim.legacy.LegacyCanonicalConverter

/** Reads an original `.MAP` file into the native format (ADR 0003, ADR 0022). */
object LegacyMapImport {
    /** The imported map, and anything in the `.MAP` the importer could not represent. */
    class Result(val document: MapDocument, val notes: List<String>)

    fun import(bytes: ByteArray, mapName: String): Result {
        val canonical = LegacyCanonicalConverter.convert(mapName, LegacyMapParser().parse(bytes, mapName))
        val objects = CanonicalSceneBuilder.buildActors(canonical)
        val sidecar = MapSceneSidecar(metadata = canonical.metadata, missionText = canonical.missionText, tileLayers = canonical.layers)
        return Result(withPatch(MapConversion.toDocument(LoadedMap(sidecar, objects)), bytes), canonical.importNotes)
    }

    /** Object types the import reads (the others are runtime objects, men and bonuses, and the zeppelin). */
    private val IMPORTED_TYPES = setOf(0, 1, 3, 5, 8, 9, 10, 11, 13, 14, 15, 16, 17, 19)

    /**
     * ADR 0022, option C: the bytes of [original] that export does not make from [document] itself,
     * so that the map exports to its original file again (unlocked).
     */
    internal fun withPatch(document: MapDocument, original: ByteArray): MapDocument {
        val file = LegacyMapExport.unlocked(original)
        fun int(at: Int) = (0..3).fold(0) { v, k -> v or ((file[at + k].toInt() and 0xFF) shl (8 * k)) }
        val offsets = (0..3).map { int(52 + it * 4) }
        if (offsets.zipWithNext().any { (a, b) -> b < a } || offsets[3] > file.size) return document
        val raw = (0..2).map { String(file, offsets[it], offsets[it + 1] - offsets[it], Charsets.ISO_8859_1) }
        val made = listOf(document.texts.briefing, document.texts.success, document.texts.failure).map { if (it.isEmpty()) "" else it + "\n" }
        val texts = raw.takeIf { it != made }
        val parts = LegacyMapExport.exportUnpatched(document.copy(legacy = LegacyPatch(texts = texts)))
        if (parts.head.size != offsets[3]) return document
        val head = LegacyMapExport.diffRuns(parts.head, file.copyOfRange(0, offsets[3]))
        val size = (file[78].toInt() and 0xFF) or ((file[79].toInt() and 0xFF) shl 8)
        val records = (offsets[3] until file.size - size + 1 step size.coerceAtLeast(1))
            .map { file.copyOfRange(it, it + size) }
            .filter { record -> (0..3).fold(0) { v, k -> v or ((record[48 + k].toInt() and 0xFF) shl (8 * k)) } in IMPORTED_TYPES }
        val exported = parts.records.filterNotNull()
        val objects = if (size == LegacyMapExport.OBJECT_SIZE && records.size == exported.size && exported.size == parts.records.size) {
            exported.indices
                .associate { it.toString() to LegacyMapExport.diffRuns(exported[it], records[it]) }
                .filterValues { it.isNotEmpty() }
        } else {
            emptyMap()
        }
        val patch = LegacyPatch(head.ifEmpty { null }, texts, objects.ifEmpty { null })
        return if (patch == LegacyPatch()) document else document.copy(legacy = patch)
    }

    /** The file name a map gets in the game: lower case, other characters than a-z, 0-9, - and _ replaced. */
    fun baseName(mapName: String): String = mapName.lowercase().replace(Regex("[^a-z0-9_-]+"), "_").trim('_')
}

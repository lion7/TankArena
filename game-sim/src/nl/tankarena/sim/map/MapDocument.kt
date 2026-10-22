package nl.tankarena.sim.map

import nl.tankarena.content.GameModeCompatibility
import nl.tankarena.content.MissionText
import nl.tankarena.content.TankArenaWorld
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

/** Version of the `.tamap` format; readers refuse newer files. */
const val MAP_FORMAT_VERSION: Int = 1

/** File extension of native maps. */
const val MAP_FILE_EXTENSION: String = "tamap"

/**
 * A map in the native format (ADR 0022): one readable JSON file holding the header, the mission
 * texts, the three picture layers as text grids through a [legend], the sparse goal, bonus and men
 * layers, and the map's objects.
 */
@Serializable
data class MapDocument(
    val format: Int = MAP_FORMAT_VERSION,
    val header: MapHeader,
    val texts: MissionText = MissionText(),
    /** Two-character code to picture: its index in the world's picture table and its name. */
    val legend: Map<String, LegendEntry> = emptyMap(),
    /** Legacy `mapm.m[0..2]`: one string per row, two characters per cell, `..` for none. */
    val floor: List<String>,
    val solid: List<String>,
    val top: List<String>,
    /** Legacy `mapm.g`: the goal-counter share of the building on a cell, where it is not 0. */
    val goals: List<GoalCell> = emptyList(),
    /** Legacy `mapm.b`: the bonus left on a cell, where there is one. */
    val bonuses: List<BonusCell> = emptyList(),
    /** Legacy `mapm.man[0..1]`: paratroopers in a building (their weapon type and number). */
    val men: List<MenCell> = emptyList(),
    /** The map's objects: `type`, `at` (and `size`), then the object's own fields. */
    val objects: List<JsonObject> = emptyList(),
    /** For a map imported from a `.MAP`: the bytes export does not make itself (ADR 0022, option C). */
    val legacy: LegacyPatch? = null,
)

@Serializable
data class MapHeader(
    val name: String,
    /** Width and height in tiles. */
    val size: List<Int>,
    /** Legacy `this_mission_code` and `next_mission_code`. */
    val code: String,
    val next: String? = null,
    /** Legacy `background`. */
    val world: TankArenaWorld = TankArenaWorld.DESERT,
    val night: Boolean = false,
    /** Legacy `map_type`: which modes may use the map. */
    val type: GameModeCompatibility = GameModeCompatibility.DONT_CARE,
    /** Legacy `map_contents`: MAPC_* bits for the player-vs-player vehicle choice. */
    val contents: Int = 0,
    /** Legacy `public_pwd`. */
    val publicCode: Boolean = false,
    val randomBonus: Boolean = true,
    /** Legacy `map_version` of the `.MAP` the map came from. */
    val legacyVersion: Int = 0,
)

/** A picture of the map's world: [index] in the world's picture table, [name] for the reader. */
@Serializable(with = LegendEntrySerializer::class)
data class LegendEntry(val index: Int, val name: String)

@Serializable
data class GoalCell(val at: List<Int>, val share: Int)

@Serializable
data class BonusCell(val at: List<Int>, val bonus: Int)

@Serializable
data class MenCell(val at: List<Int>, val type: Int, val count: Int)

/**
 * The bytes of an imported `.MAP` that [LegacyMapExport] does not make from the map itself: timers
 * and pointers saved by the original editor, text left behind in the code fields, fields the rewrite
 * does not use. [head] patches the header, layers and texts, [objects] each object's record (by its
 * place in [MapDocument.objects]); [texts] are the original's three texts as they were in the file,
 * used while the map's text is still the same. The editor drops the runs over bytes an edit changes;
 * a new object has none.
 */
@Serializable
data class LegacyPatch(
    val head: List<PatchRun>? = null,
    val texts: List<String>? = null,
    val objects: Map<String, List<PatchRun>>? = null,
)

/** Bytes at an offset, written as `[offset, "hex"]`. */
@Serializable(with = PatchRunSerializer::class)
data class PatchRun(val offset: Int, val data: String)

internal object PatchRunSerializer : KSerializer<PatchRun> {
    override val descriptor: SerialDescriptor = JsonArray.serializer().descriptor

    override fun serialize(encoder: Encoder, value: PatchRun) =
        encoder.encodeSerializableValue(JsonArray.serializer(), JsonArray(listOf(JsonPrimitive(value.offset), JsonPrimitive(value.data))))

    override fun deserialize(decoder: Decoder): PatchRun {
        val array = decoder.decodeSerializableValue(JsonArray.serializer())
        require(array.size == 2) { "a patch run is [offset, hex], got $array" }
        return PatchRun(array[0].jsonPrimitive.int, array[1].jsonPrimitive.content)
    }
}

/** A legend entry is written as `[index, "NAME"]`. */
internal object LegendEntrySerializer : KSerializer<LegendEntry> {
    override val descriptor: SerialDescriptor = JsonArray.serializer().descriptor

    override fun serialize(encoder: Encoder, value: LegendEntry) =
        encoder.encodeSerializableValue(JsonArray.serializer(), JsonArray(listOf(JsonPrimitive(value.index), JsonPrimitive(value.name))))

    override fun deserialize(decoder: Decoder): LegendEntry {
        val array = decoder.decodeSerializableValue(JsonArray.serializer())
        require(array.size == 2) { "a legend entry is [index, name], got $array" }
        return LegendEntry(array[0].jsonPrimitive.int, array[1].jsonPrimitive.content)
    }
}

/** Reads and writes `.tamap` text. */
object MapFile {
    private val json = Json { ignoreUnknownKeys = true }

    fun read(text: String): MapDocument {
        val document = json.decodeFromString(MapDocument.serializer(), text)
        require(document.format <= MAP_FORMAT_VERSION) {
            "Map format ${document.format} is newer than this game's ($MAP_FORMAT_VERSION); refusing to load."
        }
        return document
    }

    /** The document as readable JSON: one map row per line, short records on one line. */
    fun write(document: MapDocument): String =
        ReadableJson.format(json.encodeToJsonElement(MapDocument.serializer(), document)) + "\n"
}

/**
 * Pretty-prints JSON for people: arrays of numbers stay on one line, lists of strings (map rows)
 * get one line each, and an object fits on one line when it is short enough.
 */
internal object ReadableJson {
    private const val LINE_LIMIT = 110
    private const val INDENT = "  "

    fun format(element: JsonElement): String = buildString { write(element, "", this) }

    private fun write(element: JsonElement, indent: String, out: StringBuilder) {
        val inline = inline(element)
        if (inline != null && indent.length + inline.length <= LINE_LIMIT) {
            out.append(inline)
            return
        }
        val inner = indent + INDENT
        when (element) {
            is JsonObject -> {
                out.append("{\n")
                element.entries.forEachIndexed { i, (key, value) ->
                    out.append(inner).append(JsonPrimitive(key).toString()).append(": ")
                    write(value, inner, out)
                    out.append(if (i < element.size - 1) ",\n" else "\n")
                }
                out.append(indent).append('}')
            }
            is JsonArray -> {
                out.append("[\n")
                element.forEachIndexed { i, value ->
                    out.append(inner)
                    write(value, inner, out)
                    out.append(if (i < element.size - 1) ",\n" else "\n")
                }
                out.append(indent).append(']')
            }
            else -> out.append(element.toString())
        }
    }

    /** The element on one line, or null when it must be broken up (lists of strings always are). */
    private fun inline(element: JsonElement): String? = when (element) {
        is JsonPrimitive -> element.toString()
        is JsonArray -> {
            if (element.size > 1 && element.all { it is JsonPrimitive && it.isString }) {
                null
            } else {
                val parts = element.map { inline(it) ?: return null }
                parts.joinToString(", ", "[", "]")
            }
        }
        is JsonObject -> {
            val parts = element.entries.map { (key, value) -> JsonPrimitive(key).toString() + ": " + (inline(value) ?: return null) }
            parts.joinToString(", ", "{", "}")
        }
    }
}

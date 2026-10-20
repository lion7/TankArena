package com.tankarena.sim.map

import com.tankarena.content.MapMetadata
import com.tankarena.content.MapSceneSidecar
import com.tankarena.content.PictureCatalog
import com.tankarena.content.TankArenaWorld
import com.tankarena.content.TileLayers
import com.tankarena.sim.SceneFile
import com.tankarena.sim.SceneObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** A map as the game holds it in memory: its header, texts and layers, and its objects. */
class LoadedMap(val sidecar: MapSceneSidecar, val objects: List<SceneObject<*>>)

/** Converts between [MapDocument] (the file) and [LoadedMap] (what a match is built from). */
object MapConversion {
    /** Legacy `mapm.b` read unsigned: 255 is "no bonus". */
    internal const val NO_BONUS = 255
    private const val TYPE_PREFIX = "tankArena"
    private val json = Json { ignoreUnknownKeys = true }

    fun toDocument(map: LoadedMap): MapDocument = documentOf(map.sidecar, map.objects.mapNotNull(::objectRecord))

    /** The document for a map's header, texts and layers ([sidecar]) and its object [records]. */
    fun documentOf(sidecar: MapSceneSidecar, records: List<JsonObject>): MapDocument {
        val metadata = sidecar.metadata
        val layers = requireNotNull(sidecar.tileLayers) { "${metadata.name} has no tile layers" }
        val width = metadata.widthTiles
        val legend = Legend(metadata.world)
        val floor = legend.encode(layers.base, width)
        val solid = legend.encode(layers.solid, width)
        val top = legend.encode(layers.top, width)
        fun at(index: Int) = listOf(index % width, index / width)
        return MapDocument(
            header = MapHeader(
                name = metadata.name,
                size = listOf(metadata.widthTiles, metadata.heightTiles),
                code = metadata.missionCode,
                next = metadata.nextMissionCode,
                world = metadata.world,
                night = metadata.night,
                type = metadata.modeCompatibility,
                contents = metadata.mapContents,
                publicCode = metadata.publicPassword,
                randomBonus = metadata.randomBonus,
                legacyVersion = metadata.legacyMapVersion,
            ),
            texts = sidecar.missionText,
            legend = legend.entries,
            floor = floor,
            solid = solid,
            top = top,
            goals = layers.goalLayer.indices.filter { layers.goalLayer[it] != 0 }.map { GoalCell(at(it), layers.goalLayer[it]) },
            bonuses = layers.bonusLayer.indices.filter { layers.bonusLayer[it] != NO_BONUS }.map { BonusCell(at(it), layers.bonusLayer[it]) },
            men = layers.manTypeLayer.indices
                .filter { layers.manTypeLayer[it] != 0 || layers.manAmountLayer[it] != 0 }
                .map { MenCell(at(it), layers.manTypeLayer[it], layers.manAmountLayer[it]) },
            objects = records,
        )
    }

    fun fromDocument(document: MapDocument): LoadedMap = LoadedMap(sidecarOf(document), objectsOf(document))

    /** Fresh objects of the map; a match changes them, so every match gets its own. */
    fun objectsOf(document: MapDocument): List<SceneObject<*>> = document.objects.map(::restoreObject)

    /** The map's header, texts and layers. */
    fun sidecarOf(document: MapDocument): MapSceneSidecar {
        val header = document.header
        val (width, height) = header.size
        val cells = width * height
        fun index(at: List<Int>) = at[0] + at[1] * width
        val goal = IntArray(cells).also { layer -> document.goals.forEach { layer[index(it.at)] = it.share } }
        val bonus = IntArray(cells) { NO_BONUS }.also { layer -> document.bonuses.forEach { layer[index(it.at)] = it.bonus } }
        val menType = IntArray(cells).also { layer -> document.men.forEach { layer[index(it.at)] = it.type } }
        val menCount = IntArray(cells).also { layer -> document.men.forEach { layer[index(it.at)] = it.count } }
        val legend = Legend.decoder(header.world, document.legend)
        return MapSceneSidecar(
            metadata = MapMetadata(
                name = header.name,
                widthTiles = width,
                heightTiles = height,
                missionCode = header.code,
                nextMissionCode = header.next,
                randomBonus = header.randomBonus,
                world = header.world,
                night = header.night,
                publicPassword = header.publicCode,
                modeCompatibility = header.type,
                mapContents = header.contents,
                legacyMapVersion = header.legacyVersion,
            ),
            missionText = document.texts,
            tileLayers = TileLayers(
                base = legend(document.floor, width, height, "floor"),
                solid = legend(document.solid, width, height, "solid"),
                top = legend(document.top, width, height, "top"),
                goalLayer = goal.toList(),
                bonusLayer = bonus.toList(),
                manTypeLayer = menType.toList(),
                manAmountLayer = menCount.toList(),
            ),
        )
    }

    /** `{"type": "tank", "at": [x, y], "size": [w, h], ...the object's other fields}`. */
    private fun objectRecord(obj: SceneObject<*>): JsonObject? {
        val typeId = SceneFile.typeIdOf(obj) ?: return null
        val state = json.parseToJsonElement(obj.save().serialize()).jsonObject
        val body = state["body"]?.jsonObject
        return buildJsonObject {
            put("type", typeId.removePrefix(TYPE_PREFIX).replaceFirstChar { it.lowercaseChar() })
            if (body != null && compactBody(body) != null) {
                compactBody(body)!!.forEach { (key, value) -> put(key, value) }
            } else if (body != null) {
                put("body", body)
            }
            state.forEach { (key, value) -> if (key != "body") put(key, value) }
        }
    }

    private fun restoreObject(record: JsonObject): SceneObject<*> {
        val type = requireNotNull(record["type"]?.jsonPrimitive?.content) { "object without a type: $record" }
        val typeId = TYPE_PREFIX + type.replaceFirstChar { it.uppercaseChar() }
        val state = buildJsonObject {
            val at = record["at"]?.jsonArray
            if (at != null) put("body", expandBody(at, record["size"]?.jsonArray))
            record.forEach { (key, value) -> if (key != "type" && key != "at" && key != "size") put(key, value) }
        }
        return requireNotNull(SceneFile.restore(typeId, state.toString())) { "unknown object type '$type'" }
    }

    /** A body whose pivot, scale and rotation are the defaults becomes `at` (and `size`); otherwise null. */
    private fun compactBody(body: JsonObject): Map<String, JsonElement>? {
        val position = body["position"]?.jsonObject ?: return null
        val at = number(position["x"]) to number(position["y"])
        val size = body["size"]?.jsonObject ?: return mapOf("at" to pair(at))
        val extent = number(size["width"]) to number(size["height"])
        val pivot = body["pivot"]?.jsonObject
        val scale = body["scale"]?.jsonObject
        val defaults = (pivot == null || (number(pivot["x"]) == extent.first / 2 && number(pivot["y"]) == extent.second / 2)) &&
            (scale == null || (number(scale["horizontal"]) == 1.0 && number(scale["vertical"]) == 1.0)) &&
            (body["rotation"] == null || number(body["rotation"]) == 0.0)
        return if (defaults) mapOf("at" to pair(at), "size" to pair(extent)) else null
    }

    private fun expandBody(at: JsonArray, size: JsonArray?): JsonObject = buildJsonObject {
        put("position", buildJsonObject {
            put("x", at[0].jsonPrimitive.double.toFloat())
            put("y", at[1].jsonPrimitive.double.toFloat())
        })
        if (size != null) {
            val width = size[0].jsonPrimitive.double.toFloat()
            val height = size[1].jsonPrimitive.double.toFloat()
            put("size", buildJsonObject { put("width", width); put("height", height) })
            put("pivot", buildJsonObject { put("x", width / 2f); put("y", height / 2f) })
            put("scale", buildJsonObject { put("horizontal", 1f); put("vertical", 1f) })
            put("rotation", 0f)
        }
    }

    private fun number(element: JsonElement?): Double = element?.jsonPrimitive?.double ?: 0.0

    /** Whole numbers are written without a fraction. */
    private fun pair(values: Pair<Double, Double>): JsonArray = JsonArray(
        listOf(values.first, values.second).map { if (it == Math.rint(it)) JsonPrimitive(it.toLong()) else JsonPrimitive(it) },
    )
}

/**
 * The legend of a map's picture layers: each picture used gets a two-character code whose first
 * character says what kind of tile it is (`w` water, `r` road, `h` building, …), so the rows read
 * like a sketch of the map. Codes are handed out in the order the pictures first appear.
 */
internal class Legend(private val world: TankArenaWorld) {
    private val codes = LinkedHashMap<Int, String>()
    val entries = LinkedHashMap<String, LegendEntry>()

    fun encode(layer: List<Int>, width: Int): List<String> = layer.chunked(width).map { row ->
        row.joinToString("") { picture -> if (picture < 0) EMPTY_CELL else code(picture) }
    }

    private fun code(picture: Int): String = codes.getOrPut(picture) {
        val code = freeCode(kindLetter(picture))
        entries[code] = LegendEntry(picture, PictureCatalog.pictureNames(world, picture)?.firstOrNull() ?: "?")
        code
    }

    private fun freeCode(letter: Char): String {
        for (first in listOf(letter, letter.uppercaseChar()) + FALLBACK_LETTERS) {
            for (second in SECOND_CHARACTERS) {
                val code = "$first$second"
                if (code != EMPTY_CELL && code !in entries) return code
            }
        }
        error("more than ${FALLBACK_LETTERS.size * SECOND_CHARACTERS.length} pictures in one map")
    }

    private fun kindLetter(picture: Int): Char {
        val row = PictureCatalog.legacyRow(world, picture) ?: return 'z'
        val type = row.type
        val strength = row.strength.firstOrNull() ?: 0
        return when {
            type == "NOTHING" || type == "STATION" -> when {
                strength >= 1000 -> '#'
                strength > 0 -> 'h'
                else -> 'g'
            }
            type.startsWith("ROAD") || type == "RUNWAY" || type == "NORM_KRAT" -> 'r'
            type.startsWith("RAILS") -> '='
            type.startsWith("WATER") -> 'w'
            type.startsWith("BRIDGE") -> 'b'
            type.startsWith("LAVA") -> 'l'
            type == "OIL" -> 'o'
            type.startsWith("BUSH") -> 't'
            type.contains("PIT") -> 'p'
            type.contains("RAMP") -> 'j'
            type.startsWith("T_") || type.startsWith("ROTATE") -> 'c'
            type.startsWith("FIELD") -> 'f'
            type.startsWith("WARP") -> 'x'
            type.startsWith("CHECK") || type == "START" -> 'k'
            type == "SHELTER" -> 's'
            type == "FUEL_DUMP" -> 'd'
            type == "ABOX" -> 'a'
            type.startsWith("BARRIER") -> 'n'
            type == "LIGHT_SRC" -> 'i'
            type == "HELISITE" -> 'e'
            else -> 'z'
        }
    }

    companion object {
        const val EMPTY_CELL = ".."
        private const val SECOND_CHARACTERS = "123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0"
        private val FALLBACK_LETTERS = ('a'..'z') + ('A'..'Z') + listOf('#', '=', '+', '*', '%', '&', '$', '@', '~', '^')

        /** Turns rows of codes back into picture indices, checking every code against the legend. */
        fun decoder(world: TankArenaWorld, legend: Map<String, LegendEntry>): (List<String>, Int, Int, String) -> List<Int> =
            { rows, width, height, layer ->
                require(rows.size == height) { "$layer has ${rows.size} rows, the map is $height high" }
                rows.flatMapIndexed { y, row ->
                    require(row.length == width * 2) { "$layer row $y has ${row.length / 2} cells, the map is $width wide" }
                    row.chunked(2).map { code ->
                        if (code == EMPTY_CELL) {
                            -1
                        } else {
                            val entry = requireNotNull(legend[code]) { "$layer row $y: code '$code' is not in the legend" }
                            val name = PictureCatalog.pictureNames(world, entry.index)?.firstOrNull()
                            require(name == null || name == entry.name) {
                                "legend '$code': picture ${entry.index} of $world is $name, not ${entry.name}"
                            }
                            entry.index
                        }
                    }
                }
            }
    }
}

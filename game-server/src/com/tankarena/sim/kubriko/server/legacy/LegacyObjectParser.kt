package com.tankarena.sim.kubriko.server.legacy

internal object LegacyObjectParser {
    private const val OBJECT_MAGIC = 0x4A424F54

    private const val OFFSET_TYPE = 48
    private const val OFFSET_X = 52
    private const val OFFSET_Y = 56
    private const val OFFSET_GC = 68
    private const val OFFSET_UNION = 84

    // Offsets into the packed `tankstruct` (src/include/types.h) relative to OFFSET_UNION.
    private const val TANK_NUM = 4
    private const val TANK_DIR = 32
    private const val TANK_ATTACK_TANKS = 112
    private const val TANK_ACTIVATOR = 132
    private const val TANK_ACTIVE = 136
    private const val TANK_ACTIVATION_RAD = 140
    private const val TANK_STAY_ACTIVE = 144
    private const val TANK_WAY_POINTS = 188
    private const val TANK_CONTR_ID = 268
    private const val TANK_CONTR_COMPUTER = 276
    private const val TANK_CONTR_GOOD = 280
    private const val TANK_WAYPOINT_COUNT = 10
    private const val TANK_STRUCT_END = TANK_CONTR_GOOD + 4

    private const val TYPE_TANK = 0
    private const val TYPE_B52 = 1
    private const val TYPE_MAN = 2
    private const val TYPE_TURRET = 3
    private const val TYPE_ROCKET = 4
    private const val TYPE_MINE = 5
    private const val TYPE_ABOMB = 6
    private const val TYPE_BONUS = 7
    private const val TYPE_TRAIN = 8
    private const val TYPE_WAGON = 9
    private const val TYPE_PLAYER = 10
    private const val TYPE_WARP = 11
    private const val TYPE_MORTAR = 12
    private const val TYPE_FLAG = 13
    private const val TYPE_LOCK = 14
    private const val TYPE_GOAL = 15
    private const val TYPE_DESTROYER = 16
    private const val TYPE_ENFORCER = 17
    private const val TYPE_ZEPPELIN = 18
    private const val TYPE_PRODUCT = 19

    /**
     * Types the legacy engine spawns at runtime (rocket projectiles, mortar bursts,
     * atomic-bomb detonations). load_map() in the original C source never calls a
     * prepare_*() handler for these, so any occurrence in a .MAP blob is benign and
     * silently dropped on import.
     */
    private val runtimeSpawnedOnly: Set<Int> = setOf(TYPE_ROCKET, TYPE_ABOMB, TYPE_MORTAR)

    private const val TILE_CENTRE_OFFSET = 16
    private val topLeftAnchored: Set<Int> = setOf(TYPE_TANK, TYPE_TURRET, TYPE_PLAYER, TYPE_WARP)

    fun parseObjects(blob: ByteArray, objectSize: Int): LegacyObjectParseResult {
        if (objectSize <= 0 || blob.isEmpty()) {
            return LegacyObjectParseResult(emptyList(), emptyList())
        }

        val objects = mutableListOf<AuthoredObject>()
        val notes = mutableListOf<String>()
        val unsupportedCounts = mutableMapOf<Int, Int>()
        val total = blob.size / objectSize

        for (index in 0 until total) {
            val start = index * objectSize
            val end = start + objectSize
            val entry = blob.copyOfRange(start, end)
            if (entry.size < OFFSET_UNION) {
                notes += "Skipped truncated legacy object entry at index $index."
                continue
            }
            val reader = LegacyObjectReader(entry)
            val magic = reader.readInt(0)
            if (magic != OBJECT_MAGIC) {
                notes += "Skipped legacy object entry at index $index because the object magic did not match."
                continue
            }

            val type = reader.readInt(OFFSET_TYPE)
            if (type in runtimeSpawnedOnly) continue
            when (val authored = parseObject(index, reader)) {
                null -> unsupportedCounts[type] = unsupportedCounts.getOrDefault(type, 0) + 1
                else -> objects += authored
            }
        }

        unsupportedCounts.toSortedMap().forEach { (type, count) ->
            notes += "Unsupported legacy object type $type encountered $count time(s) during import."
        }

        return LegacyObjectParseResult(objects = objects, notes = notes)
    }

    private fun parseObject(index: Int, reader: LegacyObjectReader): AuthoredObject? {
        val type = reader.readInt(OFFSET_TYPE)
        // Tile-sized objects are stored by their top-left corner (the engine adds 16 for the
        // centre, e.g. `(o->x+16)/b_size` in src/objects/player.c); the scene uses centres.
        val centreOffset = if (type in topLeftAnchored) TILE_CENTRE_OFFSET else 0
        val x = reader.readInt(OFFSET_X) + centreOffset
        val y = reader.readInt(OFFSET_Y) + centreOffset
        val goalContribution = reader.readInt(OFFSET_GC)

        return when (type) {
            TYPE_TANK -> AuthoredObject(
                id = "legacy-ai-tank-$index",
                kind = ObjectKinds.AI_TANK,
                x = x,
                y = y,
                properties = parseTankProperties(reader) + ("goalContribution" to goalContribution.toString()),
            )

            TYPE_B52 -> AuthoredObject(
                id = "legacy-b52-$index",
                kind = ObjectKinds.B52,
                x = x,
                y = y,
                properties = mapOf(
                    "goalContribution" to goalContribution.toString(),
                ),
            )

            TYPE_MAN -> AuthoredObject(
                id = "legacy-man-$index",
                kind = ObjectKinds.MAN,
                x = x,
                y = y,
                properties = mapOf(
                    "goalContribution" to goalContribution.toString(),
                ),
            )

            TYPE_MINE -> AuthoredObject(
                id = "legacy-mine-$index",
                kind = ObjectKinds.MINE,
                x = x,
                y = y,
                properties = mapOf(
                    "mineType" to reader.readInt(OFFSET_UNION).toString(),
                    "goalContribution" to goalContribution.toString(),
                ),
            )

            TYPE_BONUS -> AuthoredObject(
                id = "legacy-bonus-$index",
                kind = ObjectKinds.BONUS,
                x = x,
                y = y,
                properties = mapOf(
                    "bonusType" to reader.readInt(OFFSET_UNION).toString(),
                    "goalContribution" to goalContribution.toString(),
                ),
            )

            TYPE_TRAIN -> AuthoredObject(
                id = "legacy-train-$index",
                kind = ObjectKinds.TRAIN,
                x = x,
                y = y,
                properties = mapOf(
                    "goalContribution" to goalContribution.toString(),
                ),
            )

            TYPE_WAGON -> AuthoredObject(
                id = "legacy-wagon-$index",
                kind = ObjectKinds.WAGON,
                x = x,
                y = y,
                properties = mapOf(
                    "goalContribution" to goalContribution.toString(),
                ),
            )

            TYPE_ZEPPELIN -> AuthoredObject(
                id = "legacy-zeppelin-$index",
                kind = ObjectKinds.ZEPPELIN,
                x = x,
                y = y,
                properties = mapOf(
                    "goalContribution" to goalContribution.toString(),
                ),
            )

            TYPE_PLAYER -> AuthoredObject(
                id = "legacy-player-$index",
                kind = ObjectKinds.PLAYER_START,
                x = x,
                y = y,
                properties = mapOf(
                    "startType" to reader.readInt(OFFSET_UNION).toString(),
                    "direction" to reader.readInt(OFFSET_UNION + 4).toString(),
                    "lives" to reader.readInt(OFFSET_UNION + 8).toString(),
                    "laps" to reader.readInt(OFFSET_UNION + 12).toString(),
                    "goalContribution" to goalContribution.toString(),
                ),
            )

            TYPE_TURRET -> AuthoredObject(
                id = "legacy-turret-$index",
                kind = ObjectKinds.TURRET,
                x = x,
                y = y,
                properties = mapOf(
                    "turretType" to reader.readInt(OFFSET_UNION).toString(),
                    "direction" to reader.readInt(OFFSET_UNION + 4).toString(),
                    "delay" to reader.readInt(OFFSET_UNION + 12).toString(),
                    "power" to reader.readInt(OFFSET_UNION + 16).toString(),
                    "radius" to reader.readInt(OFFSET_UNION + 20).toString(),
                    "goalContribution" to goalContribution.toString(),
                ),
            )

            TYPE_FLAG -> AuthoredObject(
                id = "legacy-flag-$index",
                kind = ObjectKinds.FLAG,
                x = x,
                y = y,
                properties = mapOf(
                    "flagType" to reader.readInt(OFFSET_UNION).toString(),
                    "number" to reader.readInt(OFFSET_UNION + 4).toString(),
                    "goalContribution" to goalContribution.toString(),
                ),
            )

            TYPE_GOAL -> AuthoredObject(
                id = "legacy-goal-$index",
                kind = ObjectKinds.GOAL,
                x = x,
                y = y,
                properties = mapOf(
                    "radius" to reader.readInt(OFFSET_UNION).toString(),
                    "who" to reader.readInt(OFFSET_UNION + 4).toString(),
                    "goalContribution" to goalContribution.toString(),
                ),
            )

            TYPE_LOCK -> AuthoredObject(
                id = "legacy-lock-$index",
                kind = ObjectKinds.LOCK,
                x = x,
                y = y,
                properties = mapOf(
                    "lockX" to reader.readInt(OFFSET_UNION).toString(),
                    "lockY" to reader.readInt(OFFSET_UNION + 4).toString(),
                    "activationRange" to reader.readInt(OFFSET_UNION + 16).toString(),
                    "goalContribution" to goalContribution.toString(),
                ),
            )

            TYPE_WARP -> AuthoredObject(
                id = "legacy-warp-$index",
                kind = ObjectKinds.WARP,
                x = x,
                y = y,
                properties = mapOf(
                    "targetX" to (reader.readInt(OFFSET_UNION) + TILE_CENTRE_OFFSET).toString(),
                    "targetY" to (reader.readInt(OFFSET_UNION + 4) + TILE_CENTRE_OFFSET).toString(),
                ),
            )

            TYPE_PRODUCT -> AuthoredObject(
                id = "legacy-product-$index",
                kind = ObjectKinds.PRODUCT,
                x = x,
                y = y,
                properties = mapOf(
                    "productType" to reader.readInt(OFFSET_UNION).toString(),
                    "price" to reader.readInt(OFFSET_UNION + 4).toString(),
                ),
            )

            TYPE_DESTROYER -> AuthoredObject(
                id = "legacy-destroyer-$index",
                kind = ObjectKinds.DESTROYER,
                x = x,
                y = y,
                properties = mapOf(
                    "radius" to reader.readInt(OFFSET_UNION).toString(),
                    "immediate" to reader.readInt(OFFSET_UNION + 4).toString(),
                    "what" to reader.readInt(OFFSET_UNION + 8).toString(),
                ),
            )

            TYPE_ENFORCER -> AuthoredObject(
                id = "legacy-enforcer-$index",
                kind = ObjectKinds.ENFORCER,
                x = x,
                y = y,
                properties = mapOf(
                    "radius" to reader.readInt(OFFSET_UNION).toString(),
                    "good" to reader.readInt(OFFSET_UNION + 4).toString(),
                    "evil" to reader.readInt(OFFSET_UNION + 8).toString(),
                    "weapon" to reader.readInt(OFFSET_UNION + 12).toString(),
                    "delay" to reader.readInt(OFFSET_UNION + 24).toString(),
                ),
            )

            else -> null
        }
    }

    /**
     * Reads the authored controller fields of a legacy tank: vehicle index into
     * `src/data/tanks.c`, facing, `contr.id` (the F1–F10 id), whether the computer drives it,
     * which side it is on, and its authored waypoints.
     */
    private fun parseTankProperties(reader: LegacyObjectReader): Map<String, String> {
        if (!reader.has(OFFSET_UNION + TANK_STRUCT_END)) return emptyMap()
        val waypoints = (0 until TANK_WAYPOINT_COUNT)
            .map { i ->
                reader.readInt(OFFSET_UNION + TANK_WAY_POINTS + i * 8) to
                    reader.readInt(OFFSET_UNION + TANK_WAY_POINTS + i * 8 + 4)
            }
            .filter { (wx, _) -> wx >= 0 }
            .joinToString(";") { (wx, wy) -> "$wx,$wy" }
        return buildMap {
            put("tankType", reader.readInt(OFFSET_UNION + TANK_NUM).toString())
            put("direction", reader.readInt(OFFSET_UNION + TANK_DIR).toString())
            put("controllerId", reader.readInt(OFFSET_UNION + TANK_CONTR_ID).toString())
            put("computer", if (reader.readInt(OFFSET_UNION + TANK_CONTR_COMPUTER) != 0) "1" else "0")
            put("good", if (reader.readInt(OFFSET_UNION + TANK_CONTR_GOOD) != 0) "1" else "0")
            put("attackTanks", reader.readInt(OFFSET_UNION + TANK_ATTACK_TANKS).toString())
            put("activator", reader.readInt(OFFSET_UNION + TANK_ACTIVATOR).toString())
            put("active", reader.readInt(OFFSET_UNION + TANK_ACTIVE).toString())
            put("activationRadius", reader.readInt(OFFSET_UNION + TANK_ACTIVATION_RAD).toString())
            put("stayActive", reader.readInt(OFFSET_UNION + TANK_STAY_ACTIVE).toString())
            if (waypoints.isNotEmpty()) put("waypoints", waypoints)
        }
    }
}

internal data class LegacyObjectParseResult(
    val objects: List<AuthoredObject>,
    val notes: List<String>,
)

private class LegacyObjectReader(
    private val bytes: ByteArray,
) {
    fun has(endOffset: Int): Boolean = bytes.size >= endOffset

    fun readInt(offset: Int): Int {
        val b0 = bytes[offset].toInt() and 0xFF
        val b1 = bytes[offset + 1].toInt() and 0xFF
        val b2 = bytes[offset + 2].toInt() and 0xFF
        val b3 = bytes[offset + 3].toInt() and 0xFF
        return (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
    }
}

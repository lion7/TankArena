package nl.tankarena.sim.legacy

internal object LegacyObjectParser {
    private const val OBJECT_MAGIC = 0x4A424F54

    private const val OFFSET_TYPE = 48
    private const val OFFSET_X = 52
    private const val OFFSET_Y = 56
    private const val OFFSET_GC = 68
    private const val OFFSET_UNION = 84

    // Offsets into the packed `tankstruct` (original/src/include/types.h) relative to OFFSET_UNION.
    private const val TANK_NUM = 4
    private const val TANK_DIR = 32
    private const val TANK_ATTACK_AT = 108
    private const val TANK_ATTACK_TANKS = 112
    private const val TANK_ATTACK_GOALS = 116
    private const val TANK_FIRE_DELAY = 120
    private const val TANK_WAVE_DELAY = 124
    private const val TANK_ACTIVATOR = 132
    private const val TANK_ACTIVE = 136
    private const val TANK_ACTIVATION_RAD = 140
    private const val TANK_STAY_ACTIVE = 144
    private const val TANK_WAY_TYPE = 180
    private const val TANK_WAY_CURRENT = 184
    private const val TANK_WAY_POINTS = 188
    private const val TANK_CONTR_ID = 268
    private const val TANK_CONTR_COMPUTER = 276
    private const val TANK_CONTR_GOOD = 280
    private const val TANK_ARMOR = 328
    private const val TANK_WEAPON_AMMO = 368
    private const val TANK_WEAPON_POWER = 432
    private const val TANK_WEAPON_COUNT = 12
    private const val TANK_WAYPOINT_COUNT = 10
    private const val TANK_STRUCT_END = TANK_WEAPON_POWER + TANK_WEAPON_COUNT * 4

    // Offsets into the packed `playerstruct`: six longs and chk[25] of four shorts, then `weap`.
    private const val PLAYER_WEAPONS_ACTIVE = 224
    private const val PLAYER_WEAPON_AMMO = 228

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
    /** Legacy `o->this_structure` and `o->owner`: saved pointers that load_map() relocates. */
    private const val OFFSET_THIS = 4
    /** Legacy `o->target`: a saved pointer to another object (a goal's linked object). */
    private const val OFFSET_TARGET = 8
    private const val OFFSET_OWNER = 12
    /** Legacy `o->funct.control`: non-zero for objects that were running when the map was saved. */
    private const val OFFSET_CONTROL = 24

    fun parseObjects(blob: ByteArray, objectSize: Int): LegacyObjectParseResult {
        if (objectSize <= 0 || blob.isEmpty()) {
            return LegacyObjectParseResult(emptyList(), emptyList())
        }

        val objects = mutableListOf<AuthoredObject>()
        /** Each object's saved `this_structure`, to resolve the pointers in other objects. */
        val savedAddresses = mutableListOf<Int>()
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
                else -> {
                    objects += authored
                    savedAddresses += reader.readInt(OFFSET_THIS)
                }
            }
        }
        linkGoalTargets(objects, savedAddresses, notes)

        unsupportedCounts.toSortedMap().forEach { (type, count) ->
            notes += if (type == TYPE_MAN || type == TYPE_BONUS) {
                // Legacy load_map() has no case for them and stops with "invalid object type":
                // soldiers and bonuses belong in the men and bonus layers.
                "Skipped $count placed ${if (type == TYPE_MAN) "soldier" else "bonus"} object(s); the original refuses maps with them."
            } else {
                "Unsupported legacy object type $type encountered $count time(s) during import."
            }
        }

        return LegacyObjectParseResult(objects = objects, notes = notes)
    }

    /**
     * Legacy load_map(): `relocate(&o->target)` turns a goal's saved pointer back into the object
     * it pointed at. Goals with `who` 3 are reached by that object; it gets a `ref` the goal names
     * as its `targetRef`. Only vehicles can be such targets here.
     */
    private fun linkGoalTargets(objects: MutableList<AuthoredObject>, addresses: List<Int>, notes: MutableList<String>) {
        val byAddress = addresses.withIndex().associate { (i, address) -> address to i }
        for (i in objects.indices) {
            val goal = objects[i]
            if (goal.kind != ObjectKinds.GOAL) continue
            val address = goal.properties["targetAddress"]?.toInt() ?: continue
            val properties = goal.properties - "targetAddress"
            val targetIndex = byAddress[address]
            if (goal.properties["who"] != "3") {
                objects[i] = goal.copy(properties = properties)
                continue
            }
            val target = targetIndex?.let { objects[it] }
            if (target == null || target.kind != ObjectKinds.AI_TANK) {
                notes += "Goal ${goal.id} is linked to an object the importer cannot link (${target?.kind ?: "missing"})."
                objects[i] = goal.copy(properties = properties)
                continue
            }
            val ref = address.toString()
            objects[targetIndex] = target.copy(properties = target.properties + ("ref" to ref))
            objects[i] = goal.copy(properties = properties + ("targetRef" to ref))
        }
    }

    private fun parseObject(index: Int, reader: LegacyObjectReader): AuthoredObject? {
        val type = reader.readInt(OFFSET_TYPE)
        // Every object is stored by the top-left corner of its 33×33 picture (the engine adds 16
        // for the centre, e.g. `(o->x+16)/b_size` in original/src/objects/player.c); the scene uses centres.
        val x = reader.readInt(OFFSET_X) + TILE_CENTRE_OFFSET
        val y = reader.readInt(OFFSET_Y) + TILE_CENTRE_OFFSET
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
                // Packed `b52struct`: v, count, dir, armor, ...
                properties = mapOf(
                    "heading" to reader.readInt(OFFSET_UNION + 8).toString(),
                    "armor" to reader.readInt(OFFSET_UNION + 12).toString(),
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
                    "minePower" to reader.readInt(OFFSET_UNION + 8).toString(),
                    "goalContribution" to goalContribution.toString(),
                ),
            )


            TYPE_TRAIN, TYPE_WAGON -> AuthoredObject(
                id = if (type == TYPE_TRAIN) "legacy-train-$index" else "legacy-wagon-$index",
                kind = if (type == TYPE_TRAIN) ObjectKinds.TRAIN else ObjectKinds.WAGON,
                x = x,
                y = y,
                properties = parseTrainProperties(reader) + ("goalContribution" to goalContribution.toString()),
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
                properties = buildMap {
                    put("startType", reader.readInt(OFFSET_UNION).toString())
                    put("direction", reader.readInt(OFFSET_UNION + 4).toString())
                    put("lives", reader.readInt(OFFSET_UNION + 8).toString())
                    put("laps", reader.readInt(OFFSET_UNION + 12).toString())
                    put("goalContribution", goalContribution.toString())
                    // Legacy `pl.weap`: when active, every vehicle from this pad gets this ammo.
                    if (reader.has(OFFSET_UNION + PLAYER_WEAPON_AMMO + TANK_WEAPON_COUNT * 4) &&
                        reader.readInt(OFFSET_UNION + PLAYER_WEAPONS_ACTIVE) != 0
                    ) {
                        put("ammo", (0 until TANK_WEAPON_COUNT).joinToString(",") { reader.readInt(OFFSET_UNION + PLAYER_WEAPON_AMMO + it * 4).toString() })
                    }
                },
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
                    "shootAt" to reader.readInt(OFFSET_UNION + 24).toString(),
                    "fixed" to (reader.readInt(OFFSET_UNION + 28) != 0).toString(),
                    // Legacy `tur.forbid_dir`, a ushort: bit d set means the turret holds fire facing d.
                    "forbidDirections" to (reader.readInt(OFFSET_UNION + 40) and 0xFFFF).toString(),
                    "goalContribution" to goalContribution.toString(),
                    // A turret riding a wagon (make_turret(..., owner)) follows it instead of a building.
                    "ownerRef" to reader.readInt(OFFSET_OWNER).toString(),
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
                properties = buildMap {
                    put("radius", reader.readInt(OFFSET_UNION).toString())
                    put("who", reader.readInt(OFFSET_UNION + 4).toString())
                    put("goalContribution", goalContribution.toString())
                    reader.readInt(OFFSET_TARGET).takeIf { it != 0 }?.let { put("targetAddress", it.toString()) }
                },
            )

            TYPE_LOCK -> AuthoredObject(
                id = "legacy-lock-$index",
                kind = ObjectKinds.LOCK,
                x = x,
                y = y,
                properties = mapOf(
                    "lockX" to (reader.readInt(OFFSET_UNION) + TILE_CENTRE_OFFSET).toString(),
                    "lockY" to (reader.readInt(OFFSET_UNION + 4) + TILE_CENTRE_OFFSET).toString(),
                    "activation" to reader.readInt(OFFSET_UNION + 8).toString(),
                    "target" to reader.readInt(OFFSET_UNION + 12).toString(),
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
                    "goalContribution" to goalContribution.toString(),
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
                    "fire" to reader.readInt(OFFSET_UNION + 16).toString(),
                    "change" to reader.readInt(OFFSET_UNION + 20).toString(),
                    "delay" to reader.readInt(OFFSET_UNION + 24).toString(),
                ),
            )

            else -> null
        }
    }

    /**
     * The packed `trainstruct` (original/src/include/types.h): speed and direction, armor, whether it
     * smokes, its neighbours in the train (saved pointers, matched against `this_structure`),
     * its picture, and the rail segment it is on (`rails`) and the one it heads for (`end`).
     */
    private fun parseTrainProperties(reader: LegacyObjectReader): Map<String, String> {
        val u = OFFSET_UNION
        val noSmoke = reader.readInt(u + 16) == Int.MAX_VALUE
        return mapOf(
            "ref" to reader.readInt(OFFSET_THIS).toString(),
            // Legacy prepare_train(): only cars that had a control function at save time drive.
            "driving" to (if (reader.readInt(OFFSET_CONTROL) != 0) "1" else "0"),
            "speed" to reader.readInt(u).toString(),
            "direction" to reader.readInt(u + 8).toString(),
            "armor" to reader.readInt(u + 12).toString(),
            "smokes" to (if (noSmoke) "0" else "1"),
            "frontRef" to reader.readInt(u + 24).toString(),
            "rearRef" to reader.readInt(u + 28).toString(),
            "picture" to reader.readInt(u + 44).toString(),
            "railsStart" to java.lang.Float.intBitsToFloat(reader.readInt(u + 48)).toString(),
            "railsType" to reader.readInt(u + 52).toString(),
            "railsDir" to reader.readInt(u + 56).toString(),
            "railsX" to reader.readInt(u + 60).toString(),
            "railsY" to reader.readInt(u + 64).toString(),
            "endType" to reader.readInt(u + 68).toString(),
            "endDir" to reader.readInt(u + 72).toString(),
            "endX" to reader.readInt(u + 76).toString(),
            "endY" to reader.readInt(u + 80).toString(),
        )
    }

    /**
     * Reads the authored controller fields of a legacy tank: vehicle index into
     * `original/src/data/tanks.c`, facing, `contr.id` (the F1–F10 id), whether the computer drives it,
     * which side it is on, its `computer` driving preferences and its authored waypoints.
     */
    private fun parseTankProperties(reader: LegacyObjectReader): Map<String, String> {
        if (!reader.has(OFFSET_UNION + TANK_STRUCT_END)) return emptyMap()
        // All ten slots in order, as `way.current` indexes them; x = -1 marks an empty one.
        val waypoints = (0 until TANK_WAYPOINT_COUNT).joinToString(";") { i ->
            "${reader.readInt(OFFSET_UNION + TANK_WAY_POINTS + i * 8)},${reader.readInt(OFFSET_UNION + TANK_WAY_POINTS + i * 8 + 4)}"
        }
        return buildMap {
            put("tankType", reader.readInt(OFFSET_UNION + TANK_NUM).toString())
            put("direction", reader.readInt(OFFSET_UNION + TANK_DIR).toString())
            put("controllerId", reader.readInt(OFFSET_UNION + TANK_CONTR_ID).toString())
            put("computer", if (reader.readInt(OFFSET_UNION + TANK_CONTR_COMPUTER) != 0) "1" else "0")
            put("good", if (reader.readInt(OFFSET_UNION + TANK_CONTR_GOOD) != 0) "1" else "0")
            put("attackAt", reader.readInt(OFFSET_UNION + TANK_ATTACK_AT).toString())
            put("attackTanks", reader.readInt(OFFSET_UNION + TANK_ATTACK_TANKS).toString())
            put("attackGoals", reader.readInt(OFFSET_UNION + TANK_ATTACK_GOALS).toString())
            put("fireDelay", reader.readInt(OFFSET_UNION + TANK_FIRE_DELAY).toString())
            put("waveDelay", reader.readInt(OFFSET_UNION + TANK_WAVE_DELAY).toString())
            put("activator", reader.readInt(OFFSET_UNION + TANK_ACTIVATOR).toString())
            put("active", reader.readInt(OFFSET_UNION + TANK_ACTIVE).toString())
            put("activationRadius", reader.readInt(OFFSET_UNION + TANK_ACTIVATION_RAD).toString())
            put("stayActive", reader.readInt(OFFSET_UNION + TANK_STAY_ACTIVE).toString())
            // Legacy `shield.armor`, `weap.nw[]` and `weap.power[]`: maps give each tank its own armor and loadout.
            put("armor", reader.readInt(OFFSET_UNION + TANK_ARMOR).toString())
            put("ammo", (0 until TANK_WEAPON_COUNT).joinToString(",") { reader.readInt(OFFSET_UNION + TANK_WEAPON_AMMO + it * 4).toString() })
            put("power", (0 until TANK_WEAPON_COUNT).joinToString(",") { reader.readInt(OFFSET_UNION + TANK_WEAPON_POWER + it * 4).toString() })
            put("wayType", reader.readInt(OFFSET_UNION + TANK_WAY_TYPE).toString())
            put("wayCurrent", reader.readInt(OFFSET_UNION + TANK_WAY_CURRENT).toString())
            put("waypoints", waypoints)
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

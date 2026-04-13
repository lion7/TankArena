package com.tankarena.legacy

import com.tankarena.content.AuthoredObject
import com.tankarena.content.ObjectKinds

internal object LegacyObjectParser {
    private const val OBJECT_MAGIC = 0x4A424F54

    private const val OFFSET_TYPE = 48
    private const val OFFSET_X = 52
    private const val OFFSET_Y = 56
    private const val OFFSET_GC = 68
    private const val OFFSET_UNION = 84

    private const val TYPE_TURRET = 3
    private const val TYPE_PLAYER = 10
    private const val TYPE_WARP = 11
    private const val TYPE_FLAG = 13
    private const val TYPE_LOCK = 14
    private const val TYPE_GOAL = 15
    private const val TYPE_DESTROYER = 16
    private const val TYPE_ENFORCER = 17
    private const val TYPE_PRODUCT = 19

    fun parseObjects(blob: ByteArray, objectSize: Int): LegacyObjectParseResult {
        if (objectSize <= 0 || blob.isEmpty()) {
            return LegacyObjectParseResult(emptyList(), listOf("No legacy objects were present in the map blob."))
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

            when (val authored = parseObject(index, reader)) {
                null -> {
                    val type = reader.readInt(OFFSET_TYPE)
                    unsupportedCounts[type] = unsupportedCounts.getOrDefault(type, 0) + 1
                }
                else -> objects += authored
            }
        }

        unsupportedCounts.toSortedMap().forEach { (type, count) ->
            notes += "Unsupported legacy object type $type encountered $count time(s) during import."
        }

        return LegacyObjectParseResult(
            objects = objects,
            notes = notes,
        )
    }

    private fun parseObject(index: Int, reader: LegacyObjectReader): AuthoredObject? {
        val type = reader.readInt(OFFSET_TYPE)
        val x = reader.readInt(OFFSET_X)
        val y = reader.readInt(OFFSET_Y)
        val goalContribution = reader.readInt(OFFSET_GC)

        return when (type) {
            TYPE_PLAYER -> {
                val startType = reader.readInt(OFFSET_UNION)
                val dir = reader.readInt(OFFSET_UNION + 4)
                val lives = reader.readInt(OFFSET_UNION + 8)
                val laps = reader.readInt(OFFSET_UNION + 12)
                AuthoredObject(
                    id = "legacy-player-$index",
                    kind = ObjectKinds.PLAYER_START,
                    x = x,
                    y = y,
                    properties = mapOf(
                        "startType" to startType.toString(),
                        "direction" to dir.toString(),
                        "lives" to lives.toString(),
                        "laps" to laps.toString(),
                        "goalContribution" to goalContribution.toString(),
                    ),
                )
            }

            TYPE_TURRET -> {
                val turretType = reader.readInt(OFFSET_UNION)
                val dir = reader.readInt(OFFSET_UNION + 4)
                val delay = reader.readInt(OFFSET_UNION + 12)
                val power = reader.readInt(OFFSET_UNION + 16)
                val radius = reader.readInt(OFFSET_UNION + 20)
                AuthoredObject(
                    id = "legacy-turret-$index",
                    kind = ObjectKinds.TURRET,
                    x = x,
                    y = y,
                    properties = mapOf(
                        "turretType" to turretType.toString(),
                        "direction" to dir.toString(),
                        "delay" to delay.toString(),
                        "power" to power.toString(),
                        "radius" to radius.toString(),
                        "goalContribution" to goalContribution.toString(),
                    ),
                )
            }

            TYPE_FLAG -> {
                val flagType = reader.readInt(OFFSET_UNION)
                val number = reader.readInt(OFFSET_UNION + 4)
                AuthoredObject(
                    id = "legacy-flag-$index",
                    kind = ObjectKinds.FLAG,
                    x = x,
                    y = y,
                    properties = mapOf(
                        "flagType" to flagType.toString(),
                        "number" to number.toString(),
                        "goalContribution" to goalContribution.toString(),
                    ),
                )
            }

            TYPE_GOAL -> {
                val radius = reader.readInt(OFFSET_UNION)
                val who = reader.readInt(OFFSET_UNION + 4)
                AuthoredObject(
                    id = "legacy-goal-$index",
                    kind = ObjectKinds.GOAL,
                    x = x,
                    y = y,
                    properties = mapOf(
                        "radius" to radius.toString(),
                        "who" to who.toString(),
                        "goalContribution" to goalContribution.toString(),
                    ),
                )
            }

            TYPE_LOCK -> {
                val lockX = reader.readInt(OFFSET_UNION)
                val lockY = reader.readInt(OFFSET_UNION + 4)
                val activationRange = reader.readInt(OFFSET_UNION + 16)
                AuthoredObject(
                    id = "legacy-lock-$index",
                    kind = ObjectKinds.LOCK,
                    x = x,
                    y = y,
                    properties = mapOf(
                        "lockX" to lockX.toString(),
                        "lockY" to lockY.toString(),
                        "activationRange" to activationRange.toString(),
                        "goalContribution" to goalContribution.toString(),
                    ),
                )
            }

            TYPE_WARP -> {
                val targetX = reader.readInt(OFFSET_UNION)
                val targetY = reader.readInt(OFFSET_UNION + 4)
                AuthoredObject(
                    id = "legacy-warp-$index",
                    kind = ObjectKinds.WARP,
                    x = x,
                    y = y,
                    properties = mapOf(
                        "targetX" to targetX.toString(),
                        "targetY" to targetY.toString(),
                    ),
                )
            }

            TYPE_PRODUCT -> {
                val productType = reader.readInt(OFFSET_UNION)
                val price = reader.readInt(OFFSET_UNION + 4)
                AuthoredObject(
                    id = "legacy-product-$index",
                    kind = ObjectKinds.PRODUCT,
                    x = x,
                    y = y,
                    properties = mapOf(
                        "productType" to productType.toString(),
                        "price" to price.toString(),
                    ),
                )
            }

            TYPE_DESTROYER -> {
                val radius = reader.readInt(OFFSET_UNION)
                val immediate = reader.readInt(OFFSET_UNION + 4)
                val what = reader.readInt(OFFSET_UNION + 8)
                AuthoredObject(
                    id = "legacy-destroyer-$index",
                    kind = ObjectKinds.DESTROYER,
                    x = x,
                    y = y,
                    properties = mapOf(
                        "radius" to radius.toString(),
                        "immediate" to immediate.toString(),
                        "what" to what.toString(),
                    ),
                )
            }

            TYPE_ENFORCER -> {
                val radius = reader.readInt(OFFSET_UNION)
                val good = reader.readInt(OFFSET_UNION + 4)
                val evil = reader.readInt(OFFSET_UNION + 8)
                val weapon = reader.readInt(OFFSET_UNION + 12)
                AuthoredObject(
                    id = "legacy-enforcer-$index",
                    kind = ObjectKinds.ENFORCER,
                    x = x,
                    y = y,
                    properties = mapOf(
                        "radius" to radius.toString(),
                        "good" to good.toString(),
                        "evil" to evil.toString(),
                        "weapon" to weapon.toString(),
                    ),
                )
            }

            else -> null
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
    fun readInt(offset: Int): Int {
        val b0 = bytes[offset].toInt() and 0xFF
        val b1 = bytes[offset + 1].toInt() and 0xFF
        val b2 = bytes[offset + 2].toInt() and 0xFF
        val b3 = bytes[offset + 3].toInt() and 0xFF
        return (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
    }
}

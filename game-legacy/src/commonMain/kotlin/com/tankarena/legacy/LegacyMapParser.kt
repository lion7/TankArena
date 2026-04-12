package com.tankarena.legacy

import com.tankarena.content.AuthoredObject
import com.tankarena.content.CanonicalMapDefinition
import com.tankarena.content.GameModeCompatibility
import com.tankarena.content.MapMetadata
import com.tankarena.content.MissionText
import com.tankarena.content.TankArenaWorld
import com.tankarena.content.TileLayers

class LegacyMapParser {
    fun parse(bytes: ByteArray, mapName: String): LegacyMapData {
        require(bytes.size >= HEADER_SIZE) { "Map file is smaller than legacy header size." }
        val headerBytes = bytes.copyOfRange(0, HEADER_SIZE)
        val header = readHeader(selectHeaderBytes(headerBytes))
        val cursor = LittleEndianCursor(bytes, HEADER_SIZE)
        val cellCount = header.widthTiles * header.heightTiles

        val terrain0 = cursor.readShortArray(cellCount)
        val terrain1 = cursor.readShortArray(cellCount)
        val terrain2 = cursor.readShortArray(cellCount)
        val goalLayer = cursor.readUnsignedByteArray(cellCount)
        val bonusLayer = cursor.readUnsignedByteArray(cellCount)
        val manTypeLayer = cursor.readUnsignedByteArray(cellCount)
        val manAmountLayer = cursor.readUnsignedByteArray(cellCount)

        val missionOffsets = header.missionTextOffsets.sorted()
        val briefingText = bytes.readCStringRange(missionOffsets[0], missionOffsets[1])
        val successText = bytes.readCStringRange(missionOffsets[1], missionOffsets[2])
        val failureText = bytes.readCStringRange(missionOffsets[2], missionOffsets[3])
        val rawObjectBlobSize = (bytes.size - missionOffsets[3]).coerceAtLeast(0)
        val rawObjectBlob = bytes.copyOfRange(missionOffsets[3].coerceAtMost(bytes.size), bytes.size)

        return LegacyMapData(
            header = header,
            terrain0 = terrain0,
            terrain1 = terrain1,
            terrain2 = terrain2,
            goalLayer = goalLayer,
            bonusLayer = bonusLayer,
            manTypeLayer = manTypeLayer,
            manAmountLayer = manAmountLayer,
            briefingText = briefingText,
            successText = successText,
            failureText = failureText,
            rawObjectBlobSize = rawObjectBlobSize,
            rawObjectBlob = rawObjectBlob,
        )
    }

    fun toCanonical(mapName: String, legacy: LegacyMapData): CanonicalMapDefinition {
        val objectBlob = legacy.rawObjectBlobSize.takeIf { it > 0 }?.let {
            legacy.rawObjectBlob
        } ?: ByteArray(0)
        val parsedObjects = LegacyObjectParser.parseObjects(
            blob = objectBlob,
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

    private fun readHeader(bytes: ByteArray): LegacyMapHeader {
        val cursor = LittleEndianCursor(bytes)
        return LegacyMapHeader(
            missionCode = cursor.readFixedString(22),
            nextMissionCode = cursor.readFixedString(22),
            widthTiles = cursor.readInt(),
            heightTiles = cursor.readInt(),
            missionTextOffsets = List(4) { cursor.readInt() },
            randomBonus = cursor.readShort() != 0,
            background = cursor.readUnsignedByte(),
            publicPassword = cursor.readUnsignedByte() != 0,
            night = cursor.readShort() != 0,
            mapType = cursor.readUnsignedByte(),
            mapVersion = cursor.readUnsignedByte(),
            mapContents = cursor.readShort(),
            objectSize = cursor.readShort(),
            shareware = cursor.readUnsignedByte() != 0,
            lock = run {
                cursor.readUnsignedByte() // legacy extra char
                cursor.readShort() != 0
            },
        )
    }

    private fun selectHeaderBytes(headerBytes: ByteArray): ByteArray {
        val plain = readHeader(headerBytes)
        if (plain.isSane()) return headerBytes

        val unlockedBytes = headerBytes.xorProtected()
        val unlocked = readHeader(unlockedBytes)
        require(unlocked.isSane()) { "Unable to parse a sane legacy header from map bytes." }
        return unlockedBytes
    }

    private fun ByteArray.xorProtected(): ByteArray {
        val out = copyOf()
        var index = 0
        while (index + 3 < out.size) {
            val value = LittleEndianCursor(out, index).readInt()
            val xored = value xor PROTECT_MASK
            out[index] = (xored and 0xFF).toByte()
            out[index + 1] = ((xored ushr 8) and 0xFF).toByte()
            out[index + 2] = ((xored ushr 16) and 0xFF).toByte()
            out[index + 3] = ((xored ushr 24) and 0xFF).toByte()
            index += 4
        }
        return out
    }

    private fun ByteArray.readCStringRange(start: Int, end: Int): String {
        if (start >= size || start >= end) return ""
        val safeEnd = end.coerceAtMost(size)
        val segment = copyOfRange(start, safeEnd)
        val zero = segment.indexOf(0)
        val cut = if (zero == -1) segment.size else zero
        return segment.copyOf(cut).decodeToString().trim()
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

    companion object {
        private const val HEADER_SIZE = 84
        private const val PROTECT_MASK = 0x34312E33
    }
}

private fun LegacyMapHeader.isSane(): Boolean {
    return widthTiles in 1..200 &&
        heightTiles in 1..200 &&
        missionTextOffsets.size == 4 &&
        missionTextOffsets.zipWithNext().all { (a, b) -> a in 0..1_000_000 && b >= a } &&
        mapVersion in 0..64 &&
        objectSize in 0..4096
}

private class LittleEndianCursor(
    private val bytes: ByteArray,
    private var position: Int = 0,
) {
    fun readFixedString(length: Int): String {
        val end = (position + length).coerceAtMost(bytes.size)
        val raw = bytes.copyOfRange(position, end)
        position += length
        val zero = raw.indexOf(0)
        val cut = if (zero == -1) raw.size else zero
        return raw.copyOf(cut).decodeToString().trim()
    }

    fun readUnsignedByte(): Int = bytes[position++].toInt() and 0xFF

    fun readShort(): Int {
        val low = readUnsignedByte()
        val high = readUnsignedByte()
        return (high shl 8) or low
    }

    fun readInt(): Int {
        val b0 = readUnsignedByte()
        val b1 = readUnsignedByte()
        val b2 = readUnsignedByte()
        val b3 = readUnsignedByte()
        return (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
    }

    fun readShortArray(size: Int): List<Int> = List(size) { readShort().let { if (it >= 0x8000) it - 0x10000 else it } }

    fun readUnsignedByteArray(size: Int): List<Int> = List(size) { readUnsignedByte() }
}

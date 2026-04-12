package com.tankarena.legacy

import kotlin.test.Test
import kotlin.test.assertEquals

class LegacyObjectParserTest {
    @Test
    fun `parses supported player and turret objects`() {
        val objectSize = 128
        val blob = ByteArray(objectSize * 2)

        writeInt(blob, 0, 0x4A424F54)
        writeInt(blob, 48, 10)
        writeInt(blob, 52, 100)
        writeInt(blob, 56, 200)
        writeInt(blob, 84, 0)
        writeInt(blob, 88, 4)
        writeInt(blob, 92, 4)
        writeInt(blob, 96, 3)

        writeInt(blob, objectSize + 0, 0x4A424F54)
        writeInt(blob, objectSize + 48, 3)
        writeInt(blob, objectSize + 52, 300)
        writeInt(blob, objectSize + 56, 400)
        writeInt(blob, objectSize + 84, 2)
        writeInt(blob, objectSize + 88, 12)
        writeInt(blob, objectSize + 96, 15)
        writeInt(blob, objectSize + 100, 7)
        writeInt(blob, objectSize + 104, 250)

        val parsed = LegacyObjectParser.parseObjects(blob, objectSize)

        assertEquals(2, parsed.objects.size)
        assertEquals("player_start", parsed.objects[0].kind)
        assertEquals("turret", parsed.objects[1].kind)
        assertEquals("250", parsed.objects[1].properties["radius"])
    }

    private fun writeInt(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value and 0xFF).toByte()
        target[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        target[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        target[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }
}

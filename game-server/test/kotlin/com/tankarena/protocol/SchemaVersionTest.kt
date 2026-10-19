package com.tankarena.protocol

import com.tankarena.protocol.snapshot.PROTOCOL_VERSION
import com.tankarena.protocol.snapshot.WorldSnapshot
import com.tankarena.protocol.snapshot.decodeWorldSnapshot
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SchemaVersionTest {

    @Test
    fun `WorldSnapshot defaults to current PROTOCOL_VERSION`() {
        val snapshot = WorldSnapshot(tick = 0L)
        assertEquals(PROTOCOL_VERSION, snapshot.protocolVersion)
    }

    @Test
    fun `decodeWorldSnapshot rejects future protocolVersion`() {
        val futureJson = """{"tick":0,"actors":[],"events":[],"protocolVersion":${PROTOCOL_VERSION + 1}}"""
        val ex = assertFailsWith<IllegalArgumentException> { decodeWorldSnapshot(futureJson) }
        check(ex.message!!.contains("protocolVersion"))
    }

    @Test
    fun `decodeWorldSnapshot accepts current protocolVersion`() {
        val payload = Json.encodeToString(WorldSnapshot.serializer(), WorldSnapshot(tick = 7L))
        val decoded = decodeWorldSnapshot(payload)
        assertEquals(7L, decoded.tick)
        assertEquals(PROTOCOL_VERSION, decoded.protocolVersion)
    }

}

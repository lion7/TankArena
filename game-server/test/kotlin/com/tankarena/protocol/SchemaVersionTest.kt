package com.tankarena.protocol

import com.tankarena.content.MapSceneSidecar
import com.tankarena.content.MapMetadata
import com.tankarena.content.SCENE_SCHEMA_VERSION
import com.tankarena.protocol.snapshot.PROTOCOL_VERSION
import com.tankarena.protocol.snapshot.WorldSnapshot
import com.tankarena.protocol.snapshot.decodeWorldSnapshot
import com.tankarena.sim.kubriko.server.LegacyToSceneJson
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
    fun `MapSceneSidecar defaults to current SCENE_SCHEMA_VERSION`() {
        val sidecar = MapSceneSidecar(
            metadata = MapMetadata(name = "x", widthTiles = 1, heightTiles = 1, missionCode = "X"),
        )
        assertEquals(SCENE_SCHEMA_VERSION, sidecar.schemaVersion)
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

    @Test
    fun `parseSidecar rejects future schemaVersion`() {
        val futureJson = """
            {
              "metadata": {"name":"x","widthTiles":1,"heightTiles":1,"missionCode":"X"},
              "schemaVersion": ${SCENE_SCHEMA_VERSION + 1}
            }
        """.trimIndent()
        val ex = assertFailsWith<IllegalArgumentException> {
            LegacyToSceneJson.parseSidecar(futureJson)
        }
        check(ex.message!!.contains("schemaVersion"))
    }
}

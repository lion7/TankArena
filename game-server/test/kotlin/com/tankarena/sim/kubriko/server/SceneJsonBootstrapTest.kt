package com.tankarena.sim.kubriko.server

import com.tankarena.content.MapMetadata
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SceneJsonBootstrapTest {

    private val scenesDir: File = run {
        val workingDir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        sequenceOf(
            File(workingDir, "game-content/resources/scenes"),
            File(workingDir, "../game-content/resources/scenes"),
        ).firstOrNull { it.isDirectory } ?: error("Generated scenes directory not found near $workingDir")
    }

    private var prototype: ServerMatchPrototype? = null

    @AfterTest
    fun cleanup() {
        prototype?.dispose()
        prototype = null
    }

    @Test
    fun `fromSceneJson bootstraps a working server match from a generated scene file`() {
        val sceneFile = File(scenesDir, "scene_3_vs_3.json")
        val sidecarFile = File(scenesDir, "metadata_3_vs_3.json")
        assertTrue(sceneFile.isFile, "expected ${sceneFile.absolutePath}")
        assertTrue(sidecarFile.isFile, "expected ${sidecarFile.absolutePath}")

        val sidecar = LegacyToSceneJson.parseSidecar(sidecarFile.readText())
        val proto = ServerMatchPrototype.fromSceneJson(
            sceneJson = sceneFile.readText(),
            mapMetadata = sidecar.metadata,
        )
        prototype = proto
        proto.initialize()

        val initialSnapshot = proto.snapshot()
        assertTrue(initialSnapshot.actors.isNotEmpty(), "expected actors")
        assertEquals(sidecar.metadata.missionCode, proto.missionCode)

        val expectedWorldWidth = sidecar.metadata.widthTiles * 33
        val expectedWorldHeight = sidecar.metadata.heightTiles * 33
        assertEquals(expectedWorldWidth, proto.worldWidth)
        assertEquals(expectedWorldHeight, proto.worldHeight)

        repeat(60) { proto.tick() }
        val later = proto.snapshot()
        assertEquals(60L, later.tick)
    }

    @Test
    fun `every generated scene plus sidecar pair is well-formed and parsable`() {
        val sidecarFiles = scenesDir.listFiles { f -> f.isFile && f.name.startsWith("metadata_") && f.name.endsWith(".json") }
            ?: emptyArray()
        assertTrue(sidecarFiles.isNotEmpty(), "expected generated metadata files")
        for (sidecarFile in sidecarFiles) {
            val sceneFile = File(sidecarFile.parentFile, sidecarFile.name.replace("metadata_", "scene_"))
            assertTrue(sceneFile.isFile, "missing scene for ${sidecarFile.name}")
            val sidecar = runCatching { LegacyToSceneJson.parseSidecar(sidecarFile.readText()) }.getOrNull()
            assertNotNull(sidecar, "could not parse ${sidecarFile.name}")
            assertTrue(sidecar.metadata.missionCode.isNotBlank(), "blank missionCode in ${sidecarFile.name}")
            val sceneText = sceneFile.readText()
            assertTrue(sceneText.startsWith("["), "scene ${sceneFile.name} not a JSON array")
        }
    }

    @Test
    fun `sidecar emitted by LegacyToSceneJson round-trips through parseSidecar`() {
        val sidecarFile = File(scenesDir, "metadata_3_vs_3.json")
        val parsed = LegacyToSceneJson.parseSidecar(sidecarFile.readText())
        assertEquals("3 VS 3", parsed.metadata.missionCode)
        assertEquals(8, parsed.metadata.widthTiles)
        assertEquals(8, parsed.metadata.heightTiles)
    }

    @Suppress("unused")
    private fun MapMetadata.dimensions(): Pair<Int, Int> = widthTiles to heightTiles
}

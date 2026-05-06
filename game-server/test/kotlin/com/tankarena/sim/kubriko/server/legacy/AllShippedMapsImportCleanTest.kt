package com.tankarena.sim.kubriko.server.legacy

import com.tankarena.legacy.LegacyMapParser
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * T05 acceptance: every shipped legacy map imports cleanly — `MapSceneSidecar.importNotes`
 * is empty for all of them. Drift surfaces immediately in CI.
 */
class AllShippedMapsImportCleanTest {

    @Test
    fun `every shipped MAP file imports without notes`() {
        val mapsDir = locateMapsDir()
        val parser = LegacyMapParser()
        val violations = mutableListOf<String>()

        val files = mapsDir.listFiles { file -> file.isFile && file.name.endsWith(".MAP", ignoreCase = true) }
            ?: emptyArray()
        assertTrue(files.isNotEmpty(), "no .MAP files found under ${mapsDir.absolutePath}")

        for (file in files) {
            val name = file.nameWithoutExtension
            val legacy = runCatching { parser.parse(file.readBytes(), name) }.getOrNull()
            if (legacy == null) continue
            val canonical = LegacyCanonicalConverter.convert(name, legacy)
            if (canonical.importNotes.isNotEmpty()) {
                violations += "${file.name}: ${canonical.importNotes}"
            }
        }

        assertTrue(
            violations.isEmpty(),
            "shipped maps surfaced importNotes:\n" + violations.joinToString("\n"),
        )
    }

    private fun locateMapsDir(): File {
        val candidates = sequenceOf("../MAPS", "MAPS")
        for (candidate in candidates) {
            val dir = File(candidate).absoluteFile
            if (dir.isDirectory) return dir
        }
        fail("could not locate MAPS directory from ${File(".").absolutePath}")
    }
}

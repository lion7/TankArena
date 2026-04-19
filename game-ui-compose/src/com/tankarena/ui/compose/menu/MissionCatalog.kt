package com.tankarena.ui.compose.menu

import com.tankarena.content.CanonicalMapDefinition
import com.tankarena.legacy.LegacyMapParser
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MissionEntry(
    val code: String,
    val mapFile: File,
    val briefingPreview: String,
    val canonical: CanonicalMapDefinition,
)

object MissionCatalog {
    suspend fun load(mapsDir: File): List<MissionEntry> = withContext(Dispatchers.IO) {
        if (!mapsDir.isDirectory) return@withContext emptyList()
        val parser = LegacyMapParser()
        val files = mapsDir.listFiles { file ->
            file.isFile && file.name.endsWith(".MAP", ignoreCase = true)
        } ?: emptyArray()

        files
            .mapNotNull { file -> tryLoadEntry(parser, file) }
            .sortedBy { it.code.lowercase() }
    }

    fun resolveDefaultMapsDir(): File {
        val custom = System.getProperty("tankarena.maps.root")
        if (!custom.isNullOrBlank()) return File(custom)
        val workingDir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        return File(workingDir, "MAPS")
    }

    private fun tryLoadEntry(parser: LegacyMapParser, file: File): MissionEntry? {
        return runCatching {
            val bytes = file.readBytes()
            val name = file.nameWithoutExtension
            val legacy = parser.parse(bytes, name)
            val canonical = parser.toCanonical(name, legacy)
            val code = canonical.metadata.missionCode.ifBlank { name }
            MissionEntry(
                code = code,
                mapFile = file,
                briefingPreview = canonical.missionText.briefing.summarize(),
                canonical = canonical,
            )
        }.getOrNull()
    }
}

private fun String.summarize(maxChars: Int = 90): String {
    if (isEmpty()) return ""
    val firstLine = lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() }
        ?: return ""
    return if (firstLine.length <= maxChars) firstLine else firstLine.take(maxChars - 1).trimEnd() + "\u2026"
}

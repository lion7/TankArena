package com.tankarena.ui.compose.menu

import com.tankarena.content.MapSceneSidecar
import com.tankarena.sim.kubriko.server.legacy.LegacyMapImporter
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MissionEntry(
    val code: String,
    val mapFile: File,
    val briefingPreview: String,
    val sidecar: MapSceneSidecar,
    val sceneJson: String,
)

object MissionCatalog {
    suspend fun load(mapsDir: File): List<MissionEntry> = withContext(Dispatchers.IO) {
        if (!mapsDir.isDirectory) return@withContext emptyList()
        val files = mapsDir.listFiles { file ->
            file.isFile && file.name.endsWith(".MAP", ignoreCase = true)
        } ?: emptyArray()

        files
            .mapNotNull { file -> tryLoadEntry(file) }
            .sortedBy { it.code.lowercase() }
    }

    fun resolveDefaultMapsDir(): File {
        val custom = System.getProperty("tankarena.maps.root")
        if (!custom.isNullOrBlank()) return File(custom)
        val workingDir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        return File(workingDir, "MAPS")
    }

    private fun tryLoadEntry(file: File): MissionEntry? {
        return runCatching {
            val bytes = file.readBytes()
            val name = file.nameWithoutExtension
            val imported = LegacyMapImporter.import(bytes, name)
            val code = imported.sidecar.metadata.missionCode.ifBlank { name }
            MissionEntry(
                code = code,
                mapFile = file,
                briefingPreview = imported.sidecar.missionText.briefing.summarize(),
                sidecar = imported.sidecar,
                sceneJson = imported.sceneJson,
            )
        }.getOrNull()
    }
}

fun List<MissionEntry>.findByCode(code: String?): MissionEntry? =
    code?.takeIf { it.isNotBlank() }?.let { c -> firstOrNull { it.code.equals(c, ignoreCase = true) } }

private fun String.summarize(maxChars: Int = 90): String {
    if (isEmpty()) return ""
    val firstLine = lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() }
        ?: return ""
    return if (firstLine.length <= maxChars) firstLine else firstLine.take(maxChars - 1).trimEnd() + "…"
}

package com.tankarena.ui.compose.menu

import com.tankarena.content.GameModeCompatibility
import com.tankarena.content.MapSceneSidecar
import com.tankarena.sim.map.MapDocument
import com.tankarena.sim.map.ShippedMaps
import com.tankarena.ui.compose.GameMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MissionEntry(
    val code: String,
    val baseName: String,
    val briefingPreview: String,
    val sidecar: MapSceneSidecar,
    /** The map itself; matches are created from it (`LocalMatchClient.fromMap`). */
    val document: MapDocument,
)

/**
 * The shipped missions: the native `.tamap` maps in `:game-content`, loaded from the classpath
 * (ADR 0022). Legacy `.MAP` files are not read at runtime.
 */
object MissionCatalog {
    suspend fun load(): List<MissionEntry> = withContext(Dispatchers.IO) {
        ShippedMaps.loadAll()
            .map { scene ->
                MissionEntry(
                    code = scene.sidecar.metadata.missionCode.ifBlank { scene.baseName },
                    baseName = scene.baseName,
                    briefingPreview = scene.sidecar.missionText.briefing.summarize(),
                    sidecar = scene.sidecar,
                    document = scene.document,
                )
            }
            .sortedBy { it.code.lowercase() }
    }
}

/**
 * Legacy `src/game/mapfiles.c`: a map's type decides which game modes may load it
 * (player-vs-player lists only "don't care" and dual maps, and so on).
 */
fun List<MissionEntry>.playableIn(mode: GameMode): List<MissionEntry> = filter { entry ->
    when (entry.sidecar.metadata.modeCompatibility) {
        GameModeCompatibility.DONT_CARE -> true
        GameModeCompatibility.DUAL -> mode == GameMode.PLAYER_VS_PLAYER
        GameModeCompatibility.SINGLE -> mode == GameMode.SINGLE_PLAYER_VS_COMPUTER
        GameModeCompatibility.DUAL_VS_COMPUTER -> mode == GameMode.DUAL_PLAYER_VS_COMPUTER
        GameModeCompatibility.SINGLE_OR_DUAL -> mode != GameMode.PLAYER_VS_PLAYER
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

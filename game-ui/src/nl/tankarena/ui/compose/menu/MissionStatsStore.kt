package nl.tankarena.ui.compose.menu

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nl.tankarena.app.AppDirs
import nl.tankarena.ui.compose.GameMode
import nl.tankarena.ui.compose.MissionOutcome
import java.io.File

/** How often a mission was played, won and lost; a mission left with ESC counts as played only. */
@Serializable
data class MissionRecord(val played: Int = 0, val won: Int = 0, val lost: Int = 0) {
    /** The record as the screens show it, e.g. "Played 5× · won 2 · lost 2". */
    fun text(): String = "Played $played× · won $won · lost $lost"
}

@Serializable
private data class MissionStatsFile(val modes: Map<String, Map<String, MissionRecord>> = emptyMap())

/**
 * The record of every campaign mission, one player and together against the computer apart, kept as
 * JSON in the user's config directory next to the reached codes. Like [PasswordStore], IO failures
 * degrade to "nothing played" or "not saved".
 */
class MissionStatsStore(private val file: File = File(AppDirs.config, "mission-stats.json")) {
    private var cache: MissionStatsFile? = null

    private fun load(): MissionStatsFile = cache ?: try {
        if (file.isFile) json.decodeFromString(MissionStatsFile.serializer(), file.readText()) else MissionStatsFile()
    } catch (e: Exception) {
        System.err.println("Cannot read the mission records from $file: ${e.message}")
        MissionStatsFile()
    }.also { cache = it }

    /** The record of [baseName] in [mode], or null when it was never played. */
    fun of(mode: GameMode, baseName: String): MissionRecord? = load().modes[mode.name]?.get(baseName.uppercase())

    /** A mission of [mode] ended: won, lost, or left ([outcome] null). */
    fun record(mode: GameMode, baseName: String, outcome: MissionOutcome?) {
        val all = load()
        val missions = all.modes[mode.name].orEmpty()
        val key = baseName.uppercase()
        val before = missions[key] ?: MissionRecord()
        val after = before.copy(
            played = before.played + 1,
            won = before.won + if (outcome == MissionOutcome.WON) 1 else 0,
            lost = before.lost + if (outcome == MissionOutcome.LOST) 1 else 0,
        )
        val changed = all.copy(modes = all.modes + (mode.name to (missions + (key to after))))
        cache = changed
        try {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(MissionStatsFile.serializer(), changed))
        } catch (e: Exception) {
            System.err.println("Cannot save the mission records to $file: ${e.message}")
        }
    }

    private companion object {
        val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }
    }
}

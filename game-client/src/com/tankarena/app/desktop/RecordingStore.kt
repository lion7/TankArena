package com.tankarena.app.desktop

import com.tankarena.sim.kubriko.server.MatchRecording
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Saves every finished mission's [MatchRecording] under `$XDG_DATA_HOME/tankarena/recordings`
 * (default `~/.local/share/tankarena/recordings`). Copied into
 * `game-server/testResources/recordings`, a recording becomes a replay test.
 */
internal object RecordingStore {
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    fun directory(): File {
        val base = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
            ?: (System.getProperty("user.home") + "/.local/share")
        return File(base, "tankarena/recordings")
    }

    /** Writes [recording]; returns the file, or null when it could not be written. */
    fun save(recording: MatchRecording): File? = try {
        val outcome = when (recording.result?.won) {
            true -> "won"
            false -> "lost"
            null -> "unfinished"
        }
        val file = File(directory(), "${recording.mission}-${LocalDateTime.now().format(stamp)}-$outcome.json")
        file.parentFile.mkdirs()
        file.writeText(recording.toJson())
        file
    } catch (e: Exception) {
        System.err.println("Could not save the match recording: ${e.message}")
        null
    }
}

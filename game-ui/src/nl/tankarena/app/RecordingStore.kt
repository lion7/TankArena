package nl.tankarena.app

import nl.tankarena.sim.MatchRecording
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Saves every finished mission's [MatchRecording] under `recordings` in the data folder ([AppDirs];
 * on the desktop `~/.local/share/tankarena/recordings`). Copied into
 * `game-sim/testResources/recordings`, a recording becomes a replay test.
 */
internal object RecordingStore {
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    fun directory(): File = File(AppDirs.data, "recordings")

    /** Writes [recording]; returns the file, or null when it could not be written. */
    fun save(recording: MatchRecording): File? = try {
        val outcome = when (recording.result?.won) {
            true -> "won"
            false -> "lost"
            null -> "unfinished"
        }
        val file = File(directory(), "${recording.mission}-${LocalDateTime.now().format(stamp)}-$outcome.json")
        file.parentFile?.mkdirs()
        file.writeText(recording.toJson())
        file
    } catch (e: Exception) {
        System.err.println("Could not save the match recording: ${e.message}")
        null
    }
}

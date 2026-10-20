package nl.tankarena.ui.compose.menu

import nl.tankarena.app.AppDirs
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class PasswordFile(val codes: List<String> = emptyList())

/**
 * The mission codes the player has reached: the legacy `PASSWD.DAT` (src/game/passwd.c), kept
 * as JSON in the user's config directory. Every IO failure degrades to "nothing known" or "not
 * saved"; it never ends the game.
 */
class PasswordStore(private val file: File = defaultFile()) {

    /** Legacy `read_passwords`: the codes in the order they were reached. */
    fun load(): List<String> = try {
        if (file.isFile) {
            json.decodeFromString(PasswordFile.serializer(), file.readText()).codes
        } else {
            emptyList()
        }
    } catch (e: Exception) {
        System.err.println("Cannot read known passwords from $file: ${e.message}")
        emptyList()
    }

    /** Legacy `add_password_to_list`: appends [code] once and rewrites the file. Returns the new list. */
    fun add(code: String): List<String> {
        val known = load()
        val merged = known.withPassword(code)
        if (merged == known) return known
        try {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(PasswordFile.serializer(), PasswordFile(merged)))
        } catch (e: Exception) {
            System.err.println("Cannot save known passwords to $file: ${e.message}")
        }
        return merged
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }

        /** `passwd.json` in the config folder ([AppDirs]; on the desktop `~/.config/tankarena`). */
        fun defaultFile(): File = File(AppDirs.config, "passwd.json")
    }
}

/** Legacy `add_password_to_list`: an empty or already known code leaves the list unchanged. */
fun List<String>.withPassword(code: String): List<String> {
    val trimmed = code.trim()
    return if (trimmed.isEmpty() || trimmed in this) this else this + trimmed
}

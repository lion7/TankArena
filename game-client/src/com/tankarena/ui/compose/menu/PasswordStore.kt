package com.tankarena.ui.compose.menu

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class PasswordFile(val codes: List<String> = emptyList())

/**
 * The mission codes the player has reached: the legacy `PASSWD.DAT` (src/game/passwd.c), kept
 * as JSON in the user's config directory. Every IO failure degrades to "nothing known" or "not
 * saved"; it never ends the game.
 */
class PasswordStore(private val file: Path = defaultFile()) {

    /** Legacy `read_passwords`: the codes in the order they were reached. */
    fun load(): List<String> = try {
        if (Files.isRegularFile(file)) {
            json.decodeFromString(PasswordFile.serializer(), Files.readString(file)).codes
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
            file.parent?.let { Files.createDirectories(it) }
            Files.writeString(file, json.encodeToString(PasswordFile.serializer(), PasswordFile(merged)))
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

        /** `$XDG_CONFIG_HOME/tankarena/passwd.json`, falling back to `~/.config/tankarena/passwd.json`. */
        fun defaultFile(): Path {
            val configHome = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }
                ?.let { Paths.get(it) }
                ?: Paths.get(System.getProperty("user.home") ?: ".", ".config")
            return configHome.resolve("tankarena").resolve("passwd.json")
        }
    }
}

/** Legacy `add_password_to_list`: an empty or already known code leaves the list unchanged. */
fun List<String>.withPassword(code: String): List<String> {
    val trimmed = code.trim()
    return if (trimmed.isEmpty() || trimmed in this) this else this + trimmed
}

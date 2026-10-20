package com.tankarena.app.desktop

import com.tankarena.sim.map.LegacyMapExport
import com.tankarena.sim.map.MAP_FILE_EXTENSION
import com.tankarena.sim.map.MapDocument
import com.tankarena.sim.map.MapFile
import java.io.File

/**
 * Maps made or changed in the editor, under `$XDG_DATA_HOME/tankarena/maps` (default
 * `~/.local/share/tankarena/maps`), one `.tamap` per map. A changed shipped map is saved here under
 * its own name; the shipped file is never written.
 */
internal object UserMapStore {
    fun directory(): File {
        val base = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
            ?: (System.getProperty("user.home") + "/.local/share")
        return File(base, "tankarena/maps")
    }

    /** The saved maps' base names, sorted. */
    fun baseNames(): List<String> = directory()
        .listFiles { file -> file.extension == MAP_FILE_EXTENSION }
        .orEmpty()
        .map { it.nameWithoutExtension }
        .sorted()

    fun load(baseName: String): MapDocument = MapFile.read(file(baseName).readText())

    /** Every saved map that can be read; broken files are reported and left out. */
    fun loadAll(): Map<String, MapDocument> = baseNames().mapNotNull { baseName ->
        runCatching { baseName to load(baseName) }
            .onFailure { System.err.println("Cannot read the map ${file(baseName)}: ${it.message}") }
            .getOrNull()
    }.toMap()

    /** Writes the map; returns the file, or throws when it cannot be written. */
    fun save(baseName: String, document: MapDocument): File {
        val file = file(baseName)
        file.parentFile.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(MapFile.write(document))
        if (!temp.renameTo(file)) {
            file.delete()
            check(temp.renameTo(file)) { "cannot write $file" }
        }
        return file
    }

    /**
     * Writes the map as an original `.MAP` (ADR 0022, option C) to `export/` next to the player's maps,
     * named as DOS wants it: at most 8 characters, upper case. Returns the file.
     */
    fun exportLegacy(baseName: String, document: MapDocument): File {
        val file = File(File(directory(), "export"), baseName.uppercase().take(8) + ".MAP")
        file.parentFile.mkdirs()
        file.writeBytes(LegacyMapExport.export(document))
        return file
    }

    private fun file(baseName: String) = File(directory(), "$baseName.$MAP_FILE_EXTENSION")
}

package com.tankarena.tools.mapconv

import com.tankarena.legacy.LegacyMapParser
import com.tankarena.sim.legacy.LegacyCanonicalConverter
import com.tankarena.sim.map.LegacyMapExport
import com.tankarena.sim.map.LegacyMapImport
import com.tankarena.sim.map.MAP_FILE_EXTENSION
import com.tankarena.sim.map.MAP_INDEX_FILE_NAME
import com.tankarena.sim.map.MapFile
import java.io.File
import kotlinx.serialization.json.Json

private val json = Json {
    prettyPrint = true
    encodeDefaults = true
}

fun main(args: Array<String>) {
    require(args.isNotEmpty()) {
        """
        Usage:
          mapconv map <legacy-map-file> <output-json-file>
          mapconv import <legacy-map-file> <output-maps-dir>
          mapconv import-all <legacy-maps-dir> <output-maps-dir>
          mapconv export <tamap-file> <legacy-map-file>
          mapconv extract-pictures <src/data/pictures.c> <output-kotlin-file>
          mapconv extract-picture-terrain <src/data/pictures.c> <output-kotlin-file>
          mapconv extract-vehicles <src/data/tanks.c> <output-kotlin-file>
          mapconv extract-masks <drawable dir> <output mask file>
          mapconv extract-pictures-png <DATA dir> <output drawable dir> <output kotlin catalog> <src/data/pictures.c>
        """.trimIndent()
    }

    when (args[0]) {
        "map" -> {
            require(args.size >= 3) {
                "Usage: mapconv map <legacy-map-file> <output-json-file>"
            }
            val input = File(args[1])
            val output = File(args[2])
            val parser = LegacyMapParser()
            val legacy = parser.parse(input.readBytes(), input.nameWithoutExtension)
            val canonical = LegacyCanonicalConverter.convert(input.nameWithoutExtension, legacy)
            output.parentFile?.mkdirs()
            output.writeText(json.encodeToString(canonical))
            println("Converted ${input.name} -> ${output.absolutePath}")
        }

        "import" -> {
            require(args.size >= 3) {
                "Usage: mapconv import <legacy-map-file> <output-maps-dir>"
            }
            val outputDir = File(args[2])
            outputDir.mkdirs()
            importToNative(File(args[1]), outputDir)
        }

        "export" -> {
            require(args.size >= 3) {
                "Usage: mapconv export <tamap-file> <legacy-map-file>"
            }
            // ADR 0022, option C: a `.MAP` version 6, not locked, that the original plays and edits.
            val output = File(args[2])
            output.parentFile?.mkdirs()
            output.writeBytes(LegacyMapExport.export(MapFile.read(File(args[1]).readText())))
            println("Exported ${args[1]} -> ${output.absolutePath}")
        }

        "import-all" -> {
            require(args.size >= 3) {
                "Usage: mapconv import-all <legacy-maps-dir> <output-maps-dir>"
            }
            val inputDir = File(args[1])
            val outputDir = File(args[2])
            require(inputDir.isDirectory) { "Not a directory: ${inputDir.absolutePath}" }
            outputDir.mkdirs()
            val files = inputDir.listFiles { file -> file.isFile && file.name.endsWith(".MAP", ignoreCase = true) }
                ?: emptyArray()
            var failed = 0
            val imported = sortedSetOf<String>()
            for (file in files.sortedBy { it.name.lowercase() }) {
                runCatching { importToNative(file, outputDir) }
                    .onSuccess { baseName ->
                        if (!imported.add(baseName)) System.err.println("Warning: ${file.name} overwrote map '$baseName'")
                    }
                    .onFailure { ex ->
                        failed += 1
                        System.err.println("Failed ${file.name}: ${ex.message}")
                    }
            }
            // The game finds the shipped maps through this index (classpath resources cannot be listed).
            File(outputDir, MAP_INDEX_FILE_NAME).writeText(imported.joinToString(separator = "\n", postfix = "\n"))
            println("import-all: ok=${imported.size} failed=$failed -> ${outputDir.absolutePath}")
        }

        "extract-pictures-png" -> {
            require(args.size >= 5) {
                "Usage: mapconv extract-pictures-png <DATA dir> <output drawable dir> <output kotlin catalog> <src/data/pictures.c>"
            }
            LegacyPicturesPngExtractor.run(
                dataDir = File(args[1]),
                outputDrawableDir = File(args[2]),
                outputCatalogFile = File(args[3]),
                picturesSourceFile = File(args[4]),
            )
        }

        "extract-night" -> {
            require(args.size >= 3) {
                "Usage: mapconv extract-night <DATA dir> <output drawable dir>"
            }
            NightAssetsExtractor.run(File(args[1]), File(args[2]))
        }

        "extract-fonts" -> {
            require(args.size >= 4) {
                "Usage: mapconv extract-fonts <src/graph/5x5font.s> <DATA dir> <output-kotlin-file>"
            }
            LegacyFontExtractor.run(File(args[1]), File(args[2]), File(args[3]))
        }

        "extract-masks" -> {
            require(args.size >= 3) {
                "Usage: mapconv extract-masks <drawable dir> <output mask file>"
            }
            PictureMaskExtractor.run(File(args[1]), File(args[2]))
        }

        "extract-vehicles" -> {
            require(args.size >= 3) {
                "Usage: mapconv extract-vehicles <src/data/tanks.c> <output-kotlin-file>"
            }
            val output = File(args[2])
            LegacyVehicleExtractor.generateKotlin(sourceFile = File(args[1]), outputFile = output)
            println("Extracted legacy vehicles -> ${output.absolutePath}")
        }

        "extract-picture-terrain" -> {
            require(args.size >= 3) {
                "Usage: mapconv extract-picture-terrain <src/data/pictures.c> <output-kotlin-file>"
            }
            val output = File(args[2])
            LegacyPictureTerrainExtractor.generateKotlin(sourceFile = File(args[1]), outputFile = output)
            println("Extracted legacy picture terrain -> ${output.absolutePath}")
        }

        "extract-pictures" -> {
            require(args.size >= 3) {
                "Usage: mapconv extract-pictures <src/data/pictures.c> <output-kotlin-file>"
            }
            val input = File(args[1])
            val output = File(args[2])
            LegacyPictureCatalogExtractor.generateKotlin(
                sourceFile = input,
                outputFile = output,
            )
            println("Extracted legacy picture catalog -> ${output.absolutePath}")
        }

        else -> {
            require(args.size >= 2) {
                "Usage: mapconv <legacy-map-file> <output-json-file>"
            }
            val input = File(args[0])
            val output = File(args[1])
            val parser = LegacyMapParser()
            val legacy = parser.parse(input.readBytes(), input.nameWithoutExtension)
            val canonical = LegacyCanonicalConverter.convert(input.nameWithoutExtension, legacy)
            output.parentFile?.mkdirs()
            output.writeText(json.encodeToString(canonical))
            println("Converted ${input.name} -> ${output.absolutePath}")
        }
    }
}

/** `.MAP` → `<name>.tamap`; returns the base name. */
private fun importToNative(input: File, outputDir: File): String {
    val result = LegacyMapImport.import(input.readBytes(), input.nameWithoutExtension)
    val baseName = LegacyMapImport.baseName(input.nameWithoutExtension)
    val output = File(outputDir, "$baseName.$MAP_FILE_EXTENSION")
    output.writeText(MapFile.write(result.document))
    result.notes.forEach { System.err.println("${input.name}: $it") }
    println("Imported ${input.name} -> ${output.name}")
    return baseName
}

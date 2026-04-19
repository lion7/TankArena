package com.tankarena.tools.mapconv

import com.tankarena.legacy.LegacyMapParser
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
          mapconv extract-pictures <src/data/pictures.c> <output-kotlin-file>
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
            val canonical = parser.toCanonical(input.nameWithoutExtension, legacy)
            output.parentFile?.mkdirs()
            output.writeText(json.encodeToString(canonical))
            println("Converted ${input.name} -> ${output.absolutePath}")
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
            val canonical = parser.toCanonical(input.nameWithoutExtension, legacy)
            output.parentFile?.mkdirs()
            output.writeText(json.encodeToString(canonical))
            println("Converted ${input.name} -> ${output.absolutePath}")
        }
    }
}

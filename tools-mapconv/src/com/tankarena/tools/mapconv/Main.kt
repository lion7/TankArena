package com.tankarena.tools.mapconv

import com.tankarena.legacy.LegacyMapParser
import com.tankarena.sim.kubriko.server.LegacyToSceneJson
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
          mapconv scene <legacy-map-file> <output-scenes-dir>
          mapconv scene-all <legacy-maps-dir> <output-scenes-dir>
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

        "scene" -> {
            require(args.size >= 3) {
                "Usage: mapconv scene <legacy-map-file> <output-scenes-dir>"
            }
            val input = File(args[1])
            val outputDir = File(args[2])
            outputDir.mkdirs()
            convertSingleToScene(input, outputDir)
        }

        "scene-all" -> {
            require(args.size >= 3) {
                "Usage: mapconv scene-all <legacy-maps-dir> <output-scenes-dir>"
            }
            val inputDir = File(args[1])
            val outputDir = File(args[2])
            require(inputDir.isDirectory) { "Not a directory: ${inputDir.absolutePath}" }
            outputDir.mkdirs()
            val files = inputDir.listFiles { file -> file.isFile && file.name.endsWith(".MAP", ignoreCase = true) }
                ?: emptyArray()
            var ok = 0
            var failed = 0
            for (file in files.sortedBy { it.name.lowercase() }) {
                runCatching { convertSingleToScene(file, outputDir) }
                    .onSuccess { ok += 1 }
                    .onFailure { ex ->
                        failed += 1
                        System.err.println("Failed ${file.name}: ${ex.message}")
                    }
            }
            println("scene-all: ok=$ok failed=$failed -> ${outputDir.absolutePath}")
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

private fun convertSingleToScene(input: File, outputDir: File) {
    val parser = LegacyMapParser()
    val name = input.nameWithoutExtension
    val legacy = parser.parse(input.readBytes(), name)
    val canonical = parser.toCanonical(name, legacy)
    val converted = LegacyToSceneJson.convert(canonical)
    val baseName = sanitizeFileName(name)
    val sceneFile = File(outputDir, "scene_${baseName}.json")
    val sidecarFile = File(outputDir, "metadata_${baseName}.json")
    sceneFile.writeText(converted.sceneJson)
    sidecarFile.writeText(converted.sidecarJson)
    println("Converted ${input.name} -> ${sceneFile.name} + ${sidecarFile.name}")
}

private fun sanitizeFileName(raw: String): String =
    raw.lowercase().replace(Regex("[^a-z0-9_-]+"), "_").trim('_')

package com.tankarena.tools.mapconv

import com.tankarena.legacy.LegacyMapParser
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

fun main(args: Array<String>) {
    require(args.size >= 2) {
        "Usage: mapconv <legacy-map-file> <output-json-file>"
    }

    val input = File(args[0])
    val output = File(args[1])
    val parser = LegacyMapParser()
    val legacy = parser.parse(input.readBytes(), input.nameWithoutExtension)
    val canonical = parser.toCanonical(input.nameWithoutExtension, legacy)
    output.parentFile?.mkdirs()
    output.writeText(
        Json {
            prettyPrint = true
            encodeDefaults = true
        }.encodeToString(canonical)
    )
    println("Converted ${input.name} -> ${output.absolutePath}")
}


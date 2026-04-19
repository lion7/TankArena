package com.tankarena.tools.mapconv

import com.tankarena.content.LegacyResourceNaming
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Decodes the legacy palette-indexed sprite container
 * (`DATA/PICTURES.IDX` + `DATA/PICTURES.DAT`) using `DATA/PALETTE.DAT`
 * and emits one transparent PNG per registered sprite, plus a regenerated
 * Kotlin catalog with all variant names sourced from `src/data/pictures.c`.
 */
internal object LegacyPicturesPngExtractor {

    private const val TILE_SIZE = 33
    private const val TILE_BYTES = TILE_SIZE * TILE_SIZE
    private const val INDEX_RECORD_SIZE = 16
    private const val INDEX_NAME_SIZE = 12
    private const val TRANSPARENT_INDEX = 0xFF

    private val WORLDS = listOf(
        WorldEntry("DESERT", "pc0"),
        WorldEntry("TEMPERATE", "pc1"),
        WorldEntry("CITY", "pc2"),
        WorldEntry("NIGHT", "pc3"),
    )

    fun run(
        dataDir: File,
        outputDrawableDir: File,
        outputCatalogFile: File,
        picturesSourceFile: File,
    ) {
        val palette = readPalette(File(dataDir, "PALETTE.DAT"))
        val indexEntries = readIndex(File(dataDir, "PICTURES.IDX"))
        val pictureBytes = File(dataDir, "PICTURES.DAT").readBytes()
        val pictureSource = picturesSourceFile.readText()
        val catalogs = WORLDS.associate { entry ->
            entry.label to parsePicturesArray(pictureSource, entry.variable)
        }

        outputDrawableDir.mkdirs()
        var written = 0
        for (entry in indexEntries) {
            val image = decodePicture(palette, pictureBytes, entry.address)
            val target = File(outputDrawableDir, "${LegacyResourceNaming.safeKey(entry.name)}.png")
            ImageIO.write(image, "PNG", target)
            written += 1
        }

        outputCatalogFile.parentFile?.mkdirs()
        outputCatalogFile.writeText(renderCatalog(catalogs, indexEntries))
        println(
            "Extracted $written legacy PNG sprites to ${outputDrawableDir.absolutePath} and " +
                "regenerated ${outputCatalogFile.absolutePath}.",
        )
    }

    private fun readPalette(paletteFile: File): IntArray {
        val raw = paletteFile.readBytes()
        require(raw.size >= 256 * 3) {
            "PALETTE.DAT must contain at least 256 RGB triplets, got ${raw.size} bytes."
        }
        val argb = IntArray(256)
        // Allegro palettes use 6-bit channels. Promote to 8-bit by left-shifting,
        // then OR with the high two bits to keep the legacy gradient (matches
        // the (value << 2) | (value >> 4) trick used by mode13 renderers).
        for (index in 0 until 256) {
            val r = raw[index * 3].toInt() and 0x3F
            val g = raw[index * 3 + 1].toInt() and 0x3F
            val b = raw[index * 3 + 2].toInt() and 0x3F
            val r8 = (r shl 2) or (r ushr 4)
            val g8 = (g shl 2) or (g ushr 4)
            val b8 = (b shl 2) or (b ushr 4)
            argb[index] = (0xFF shl 24) or (r8 shl 16) or (g8 shl 8) or b8
        }
        return argb
    }

    private fun readIndex(indexFile: File): List<IndexEntry> {
        val bytes = indexFile.readBytes()
        require(bytes.size % INDEX_RECORD_SIZE == 0) {
            "PICTURES.IDX size ${bytes.size} is not a multiple of $INDEX_RECORD_SIZE."
        }
        val records = bytes.size / INDEX_RECORD_SIZE
        val out = ArrayList<IndexEntry>(records)
        val nameBytes = ByteArray(INDEX_NAME_SIZE)
        for (i in 0 until records) {
            val base = i * INDEX_RECORD_SIZE
            System.arraycopy(bytes, base, nameBytes, 0, INDEX_NAME_SIZE)
            val zero = nameBytes.indexOf(0)
            val end = if (zero == -1) INDEX_NAME_SIZE else zero
            val name = String(nameBytes, 0, end, Charsets.US_ASCII).trim()
            if (name.isEmpty()) continue
            val a0 = bytes[base + 12].toLong() and 0xFF
            val a1 = bytes[base + 13].toLong() and 0xFF
            val a2 = bytes[base + 14].toLong() and 0xFF
            val a3 = bytes[base + 15].toLong() and 0xFF
            val address = (a3 shl 24) or (a2 shl 16) or (a1 shl 8) or a0
            out += IndexEntry(name, address.toInt())
        }
        return out
    }

    private fun decodePicture(
        palette: IntArray,
        pictureBytes: ByteArray,
        address: Int,
    ): BufferedImage {
        require(address >= 0 && address + TILE_BYTES <= pictureBytes.size) {
            "Picture address $address out of range for PICTURES.DAT (${pictureBytes.size} bytes)."
        }
        val image = BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_ARGB)
        var offset = address
        for (y in 0 until TILE_SIZE) {
            for (x in 0 until TILE_SIZE) {
                val index = pictureBytes[offset].toInt() and 0xFF
                val argb = if (index == TRANSPARENT_INDEX) 0 else palette[index]
                image.setRGB(x, y, argb)
                offset += 1
            }
        }
        return image
    }

    private fun parsePicturesArray(source: String, variable: String): List<PictureRecord> {
        val marker = "$variable[]={"
        val start = source.indexOf(marker)
        require(start >= 0) { "Could not find $variable in pictures.c source." }
        val blockStart = start + marker.length
        var depth = 1
        var position = blockStart
        while (position < source.length && depth > 0) {
            when (source[position]) {
                '{' -> depth += 1
                '}' -> depth -= 1
            }
            if (depth == 0) break
            position += 1
        }
        require(depth == 0) { "Could not find end of $variable initializer." }
        val block = source.substring(blockStart, position)

        val records = ArrayList<PictureRecord>()
        var index = 0
        while (index < block.length) {
            val open = block.indexOf("{{", index)
            if (open < 0) break
            val innerStart = open + 1
            var braceCount = 1
            var cursor = innerStart + 1
            while (cursor < block.length && braceCount > 0) {
                when (block[cursor]) {
                    '{' -> braceCount += 1
                    '}' -> braceCount -= 1
                }
                if (braceCount == 0) break
                cursor += 1
            }
            require(braceCount == 0) { "Unterminated names block in $variable." }
            val innerBlock = block.substring(innerStart + 1, cursor)
            val variants = mutableListOf<String>()
            for (match in Regex("\"([^\"]*)\"").findAll(innerBlock)) {
                val raw = match.groupValues[1]
                if (raw.isNotEmpty()) variants += raw
            }
            if (variants.isEmpty()) break
            records += PictureRecord(variants)
            index = cursor + 1
        }
        return records
    }

    private fun renderCatalog(
        catalogs: Map<String, List<PictureRecord>>,
        indexEntries: List<IndexEntry>,
    ): String {
        val resourceKeys = sortedMapOf<String, String>()
        for (entry in indexEntries) {
            resourceKeys[entry.name] = LegacyResourceNaming.safeKey(entry.name)
        }
        val builder = StringBuilder()
        builder.appendLine("package com.tankarena.content")
        builder.appendLine()
        builder.appendLine("// Generated by :tools-mapconv extract-pictures-png from src/data/pictures.c +")
        builder.appendLine("// DATA/PICTURES.IDX. Do not edit by hand.")
        builder.appendLine("internal object GeneratedLegacyPictureCatalog {")
        // The number of entries blows past JVM's per-method 64KB bytecode
        // limit if we materialise the catalogs in a single static initializer,
        // so each list/map is built lazily by chunked helper functions.
        catalogs.forEach { (label, records) ->
            val propertyName = label.lowercase() + "Records"
            builder.appendLine("    val $propertyName: List<LegacyPictureRecord> by lazy { build${label}Records() }")
            builder.appendLine()
        }
        builder.appendLine("    val resourceKeys: Map<String, String> by lazy { buildResourceKeys() }")
        builder.appendLine()

        catalogs.forEach { (label, records) ->
            renderRecordChunks(builder, label, records)
        }
        renderResourceKeyChunks(builder, resourceKeys)
        builder.appendLine("}")
        return builder.toString()
    }

    private fun renderRecordChunks(
        builder: StringBuilder,
        label: String,
        records: List<PictureRecord>,
    ) {
        val chunks = records.chunked(CHUNK_SIZE)
        builder.appendLine("    private fun build${label}Records(): List<LegacyPictureRecord> {")
        builder.appendLine("        val list = ArrayList<LegacyPictureRecord>(${records.size})")
        chunks.forEachIndexed { index, _ ->
            builder.appendLine("        chunk${label}Records$index(list)")
        }
        builder.appendLine("        return list")
        builder.appendLine("    }")
        builder.appendLine()
        chunks.forEachIndexed { chunkIndex, chunk ->
            builder.appendLine("    private fun chunk${label}Records$chunkIndex(list: MutableList<LegacyPictureRecord>) {")
            chunk.forEach { record ->
                val variantsLiteral = record.nameVariants.joinToString(
                    prefix = "listOf(",
                    postfix = ")",
                ) { it.asKotlinStringLiteral() }
                builder.append("        list.add(LegacyPictureRecord(")
                builder.append(variantsLiteral)
                builder.appendLine("))")
            }
            builder.appendLine("    }")
            builder.appendLine()
        }
    }

    private fun renderResourceKeyChunks(
        builder: StringBuilder,
        resourceKeys: Map<String, String>,
    ) {
        val entries = resourceKeys.entries.toList()
        val chunks = entries.chunked(CHUNK_SIZE)
        builder.appendLine("    private fun buildResourceKeys(): Map<String, String> {")
        builder.appendLine("        val map = LinkedHashMap<String, String>(${entries.size})")
        chunks.forEachIndexed { index, _ ->
            builder.appendLine("        chunkResourceKeys$index(map)")
        }
        builder.appendLine("        return map")
        builder.appendLine("    }")
        builder.appendLine()
        chunks.forEachIndexed { chunkIndex, chunk ->
            builder.appendLine("    private fun chunkResourceKeys$chunkIndex(map: MutableMap<String, String>) {")
            chunk.forEach { (legacy, key) ->
                builder.append("        map.put(")
                builder.append(legacy.asKotlinStringLiteral())
                builder.append(", ")
                builder.append(key.asKotlinStringLiteral())
                builder.appendLine(")")
            }
            builder.appendLine("    }")
            builder.appendLine()
        }
    }

    private const val CHUNK_SIZE = 200

    private fun String.asKotlinStringLiteral(): String {
        val builder = StringBuilder(length + 2)
        builder.append('"')
        for (character in this) {
            when (character) {
                '\\' -> builder.append("\\\\")
                '"' -> builder.append("\\\"")
                else -> builder.append(character)
            }
        }
        builder.append('"')
        return builder.toString()
    }

    private data class WorldEntry(val label: String, val variable: String)

    private data class IndexEntry(val name: String, val address: Int)

    private data class PictureRecord(val nameVariants: List<String>)
}

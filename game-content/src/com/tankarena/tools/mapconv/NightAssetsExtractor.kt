package com.tankarena.tools.mapconv

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import javax.imageio.ImageIO

/**
 * Builds what the client needs to draw the night missions like the original (src/game/night.c):
 *
 * - `night_lut.png`, a lookup from every 6-bit palette colour to its night colour. The original
 *   draws a night map through NIGHT.LNK into the dark half (0..127) of NIGHT.PAL, and a light sets
 *   bit 7 of the pixels it covers, which picks the lit half. Some colours link straight into the
 *   lit half and glow without a light. The table holds 64 slices of 64×64 (blue picks the slice,
 *   8 slices per row): the dark colours in the left 512×512, the lit ones in the right.
 * - `light_<name>.png`, the 15 light cones of DATA/LIGHT.DAT (`LLIGHT1..5`, `MLIGHT1..5`,
 *   `SLIGHT1..5`), white where they light. As in write_pattern(), each row lights one run of
 *   pixels after its leading transparent (0xFF) ones.
 */
object NightAssetsExtractor {

    private const val SLICE = 64

    fun run(dataDir: File, outputDrawableDir: File) {
        outputDrawableDir.mkdirs()
        writeLookup(dataDir, File(outputDrawableDir, "night_lut.png"))
        writeLights(File(dataDir, "LIGHT.DAT"), outputDrawableDir)
    }

    private fun readPalette(file: File): List<Triple<Int, Int, Int>> {
        val bytes = file.readBytes()
        return List(256) { Triple(bytes[it * 3].toInt() and 0x3F, bytes[it * 3 + 1].toInt() and 0x3F, bytes[it * 3 + 2].toInt() and 0x3F) }
    }

    /** The extracted PNGs widen 6-bit colours as `v << 2 | v >> 4` (LegacyPicturesPngExtractor). */
    private fun widen(v: Int): Int = (v shl 2) or (v ushr 4)

    private fun rgb(c: Triple<Int, Int, Int>): Int = (0xFF shl 24) or (widen(c.first) shl 16) or (widen(c.second) shl 8) or widen(c.third)

    private fun writeLookup(dataDir: File, output: File) {
        val day = readPalette(File(dataDir, "PALETTE.DAT"))
        val night = readPalette(File(dataDir, "NIGHT.PAL"))
        val link = File(dataDir, "NIGHT.LNK").readBytes().map { it.toInt() and 0xFF }
        // The first palette entry of each colour wins, as the pictures were drawn with it.
        val indexOf = HashMap<Triple<Int, Int, Int>, Int>()
        day.forEachIndexed { index, colour -> indexOf.putIfAbsent(colour, index) }
        val image = BufferedImage(2 * 8 * SLICE, 8 * SLICE, BufferedImage.TYPE_INT_ARGB)
        for (b in 0 until SLICE) for (g in 0 until SLICE) for (r in 0 until SLICE) {
            val colour = Triple(r, g, b)
            val index = indexOf[colour] ?: nearest(day, colour)
            val x = (b % 8) * SLICE + r
            val y = (b / 8) * SLICE + g
            image.setRGB(x, y, rgb(night[link[index]]))
            image.setRGB(8 * SLICE + x, y, rgb(night[link[index] or 0x80]))
        }
        ImageIO.write(image, "png", output)
        println("Wrote night colour table -> ${output.absolutePath}")
    }

    private fun nearest(palette: List<Triple<Int, Int, Int>>, colour: Triple<Int, Int, Int>): Int =
        palette.indices.minBy { i ->
            val (r, g, b) = palette[i]
            (r - colour.first) * (r - colour.first) + (g - colour.second) * (g - colour.second) + (b - colour.third) * (b - colour.third)
        }

    /** Legacy init_night_mission() reads these 15 BIN objects, each `sx × sy` bytes. */
    private val lightSizes = listOf(
        "LLIGHT1" to (75 to 150), "LLIGHT2" to (100 to 150), "LLIGHT3" to (150 to 150), "LLIGHT4" to (150 to 100), "LLIGHT5" to (150 to 75),
        "MLIGHT1" to (29 to 76), "MLIGHT2" to (39 to 71), "MLIGHT3" to (58 to 58), "MLIGHT4" to (71 to 39), "MLIGHT5" to (76 to 29),
        "SLIGHT1" to (13 to 61), "SLIGHT2" to (28 to 56), "SLIGHT3" to (46 to 46), "SLIGHT4" to (56 to 28), "SLIGHT5" to (61 to 13),
    )

    private fun writeLights(datafile: File, outputDir: File) {
        val objects = readDatafile(datafile)
        for ((name, size) in lightSizes) {
            val (sx, sy) = size
            val bytes = objects[name] ?: error("$name missing from ${datafile.name}")
            val image = BufferedImage(sx, sy, BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until sy) {
                var x = 0
                while (x < sx && bytes[y * sx + x] == 0xFF.toByte()) x++
                while (x < sx && bytes[y * sx + x] != 0xFF.toByte()) {
                    image.setRGB(x, y, -1)
                    x++
                }
            }
            val output = File(outputDir, "light_${name.lowercase()}.png")
            ImageIO.write(image, "png", output)
        }
        println("Wrote ${lightSizes.size} light cones -> ${outputDir.absolutePath}")
    }

    /** An Allegro 3 datafile (`slh!` LZSS-packed, then `ALL.`): object name → raw bytes. */
    private fun readDatafile(file: File): Map<String, ByteArray> {
        val packed = file.readBytes()
        require(String(packed, 0, 4, Charsets.ISO_8859_1) == "slh!") { "${file.name} is not a packed Allegro file" }
        val data = DataInputStream(ByteArrayInputStream(unpackLzss(packed, 4)))
        val magic = ByteArray(4).also(data::readFully)
        require(String(magic, Charsets.ISO_8859_1) == "ALL.") { "${file.name} is not an Allegro datafile" }
        val count = data.readInt()
        val objects = LinkedHashMap<String, ByteArray>()
        repeat(count) {
            var name = ""
            while (true) {
                val tag = String(ByteArray(4).also(data::readFully), Charsets.ISO_8859_1)
                if (tag != "prop") {
                    val compressedSize = data.readInt()
                    data.readInt()
                    val bytes = ByteArray(compressedSize).also(data::readFully)
                    objects[name] = bytes
                    break
                }
                val type = String(ByteArray(4).also(data::readFully), Charsets.ISO_8859_1)
                val value = String(ByteArray(data.readInt()).also(data::readFully), Charsets.ISO_8859_1)
                if (type == "NAME") name = value
            }
        }
        return objects
    }

    /** Allegro's LZSS (Okumura): 4096-byte ring, matches of 3..18 bytes, flag bit 1 = literal. */
    private fun unpackLzss(src: ByteArray, start: Int): ByteArray {
        val n = 4096
        val f = 18
        val ring = ByteArray(n)
        var r = n - f
        val out = java.io.ByteArrayOutputStream()
        var i = start
        var flags = 0
        while (true) {
            flags = flags ushr 1
            if (flags and 0x100 == 0) {
                if (i >= src.size) break
                flags = (src[i++].toInt() and 0xFF) or 0xFF00
            }
            if (flags and 1 != 0) {
                if (i >= src.size) break
                val c = src[i++]
                out.write(c.toInt())
                ring[r] = c
                r = (r + 1) and (n - 1)
            } else {
                if (i + 1 >= src.size) break
                var position = src[i++].toInt() and 0xFF
                var length = src[i++].toInt() and 0xFF
                position = position or ((length and 0xF0) shl 4)
                length = (length and 0x0F) + 2
                for (k in 0..length) {
                    val c = ring[(position + k) and (n - 1)]
                    out.write(c.toInt())
                    ring[r] = c
                    r = (r + 1) and (n - 1)
                }
            }
        }
        return out.toByteArray()
    }
}

package nl.tankarena.tools.mapconv

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File

/** An Allegro 3 datafile (`slh!` LZSS-packed, then `ALL.`): object name → raw bytes. */
internal object AllegroDatafile {
    fun read(file: File): Map<String, ByteArray> {
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

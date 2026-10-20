package nl.tankarena.ui.compose.legacy


/**
 * An Autodesk Animator FLI file as Allegro's play_fli() reads it (the intro's LOGO.FLI and
 * TITLE.FLI, and VICTORY.FLI / THUNDER.FLI when they exist): 320×200 frames of palette indices,
 * each frame a set of chunks that change the palette and the pixels of the previous frame.
 *
 * Call [next] once per frame; afterwards [pixels] and [palette] (0xRRGGBB) hold that frame.
 */
class FliAnimation(private val bytes: ByteArray) {
    val width: Int = short(8)
    val height: Int = short(10)

    /** Frames to show, without the ring frame that leads back to the first. */
    val frameCount: Int = short(6)

    /** FLI speed is in 1/70 s (FLC files, magic 0xAF12, use milliseconds). */
    val frameMillis: Long = short(16).let { speed -> if (short(4) == FLC_MAGIC) speed.toLong() else speed * 1000L / 70 }

    val pixels = ByteArray(width * height)
    val palette = IntArray(256)

    private var offset = HEADER_SIZE
    private var shown = 0

    init {
        val magic = short(4)
        require(magic == FLI_MAGIC || magic == FLC_MAGIC) { "not an FLI file (magic 0x${magic.toString(16)})" }
    }

    /** Decode the next frame; false once all [frameCount] frames have been shown. */
    fun next(): Boolean {
        if (shown >= frameCount) return false
        // Skip any prefix chunk (0xF100) before the frame.
        while (offset + FRAME_HEADER_SIZE <= bytes.size && short(offset + 4) != FRAME_MAGIC) {
            offset += int(offset).coerceAtLeast(FRAME_HEADER_SIZE)
        }
        if (offset + FRAME_HEADER_SIZE > bytes.size) return false
        val frameSize = int(offset)
        val chunks = short(offset + 6)
        var chunk = offset + FRAME_HEADER_SIZE
        repeat(chunks) {
            val size = int(chunk)
            when (short(chunk + 4)) {
                COLOR_256 -> color(chunk + 6, shift = 0)
                COLOR_64 -> color(chunk + 6, shift = 2)
                DELTA_FLI -> deltaFli(chunk + 6)
                DELTA_FLC -> deltaFlc(chunk + 6)
                BLACK -> pixels.fill(0)
                BYTE_RUN -> byteRun(chunk + 6)
                COPY -> System.arraycopy(bytes, chunk + 6, pixels, 0, minOf(pixels.size, bytes.size - chunk - 6))
            }
            chunk += size
        }
        offset += frameSize
        shown++
        return true
    }

    /** FLI_COLOR (64 levels) and FLC_COLOR_256: packets of (skip, count) colours. */
    private fun color(at: Int, shift: Int) {
        var p = at
        val packets = short(p).also { p += 2 }
        var index = 0
        repeat(packets) {
            index += ubyte(p++)
            val count = ubyte(p++).let { if (it == 0) 256 else it }
            repeat(count) {
                if (index < 256) {
                    fun level(v: Int) = if (shift == 0) v else (v shl 2) or (v ushr 4)
                    palette[index] = (level(ubyte(p)) shl 16) or (level(ubyte(p + 1)) shl 8) or level(ubyte(p + 2))
                }
                index++
                p += 3
            }
        }
    }

    /** FLI_LC: from a first line, per line packets of (skip x, run) with run > 0 copied and < 0 repeated. */
    private fun deltaFli(at: Int) {
        var p = at
        var y = short(p)
        val lines = short(p + 2)
        p += 4
        repeat(lines) {
            var x = 0
            val packets = ubyte(p++)
            repeat(packets) {
                x += ubyte(p++)
                val run = bytes[p++].toInt()
                if (run > 0) {
                    copyRow(p, x, y, run)
                    p += run
                    x += run
                } else if (run < 0) {
                    fillRow(ubyte(p++), x, y, -run)
                    x += -run
                }
            }
            y++
        }
    }

    /** FLC_DELTA (word runs), for FLC files. */
    private fun deltaFlc(at: Int) {
        var p = at
        var lines = short(p).also { p += 2 }
        var y = 0
        while (lines > 0) {
            val word = short(p).also { p += 2 }
            when {
                word and 0xC000 == 0xC000 -> y += 0x10000 - word
                word and 0xC000 == 0x8000 -> set(width - 1, y, word and 0xFF)
                else -> {
                    var x = 0
                    repeat(word) {
                        x += ubyte(p++)
                        val run = bytes[p++].toInt()
                        if (run > 0) {
                            for (i in 0 until run) {
                                set(x, y, ubyte(p + 2 * i))
                                set(x + 1, y, ubyte(p + 2 * i + 1))
                                x += 2
                            }
                            p += run * 2
                        } else if (run < 0) {
                            val a = ubyte(p)
                            val b = ubyte(p + 1)
                            p += 2
                            repeat(-run) {
                                set(x, y, a)
                                set(x + 1, y, b)
                                x += 2
                            }
                        }
                    }
                    y++
                    lines--
                }
            }
        }
    }

    /** FLI_BRUN: every line run-length encoded, run > 0 repeated and < 0 copied. */
    private fun byteRun(at: Int) {
        var p = at
        for (y in 0 until height) {
            p++ // the old packet count, unused
            var x = 0
            while (x < width) {
                val run = bytes[p++].toInt()
                if (run > 0) {
                    fillRow(ubyte(p++), x, y, run)
                    x += run
                } else if (run < 0) {
                    copyRow(p, x, y, -run)
                    p += -run
                    x += -run
                } else {
                    break
                }
            }
        }
    }

    private fun copyRow(from: Int, x: Int, y: Int, count: Int) {
        if (y !in 0 until height) return
        val n = minOf(count, width - x, bytes.size - from)
        if (n > 0) System.arraycopy(bytes, from, pixels, x + y * width, n)
    }

    private fun fillRow(color: Int, x: Int, y: Int, count: Int) {
        if (y !in 0 until height) return
        val start = x + y * width
        pixels.fill(color.toByte(), start, start + minOf(count, width - x).coerceAtLeast(0))
    }

    private fun set(x: Int, y: Int, color: Int) {
        if (x in 0 until width && y in 0 until height) pixels[x + y * width] = color.toByte()
    }

    private fun ubyte(at: Int): Int = bytes[at].toInt() and 0xFF
    private fun short(at: Int): Int = ubyte(at) or (ubyte(at + 1) shl 8)
    private fun int(at: Int): Int = short(at) or (short(at + 2) shl 16)

    companion object {
        private const val HEADER_SIZE = 128
        private const val FRAME_HEADER_SIZE = 16
        private const val FLI_MAGIC = 0xAF11
        private const val FLC_MAGIC = 0xAF12
        private const val FRAME_MAGIC = 0xF1FA
        private const val COLOR_256 = 4
        private const val DELTA_FLC = 7
        private const val COLOR_64 = 11
        private const val DELTA_FLI = 12
        private const val BLACK = 13
        private const val BYTE_RUN = 15
        private const val COPY = 16

        /** An animation from the game's `FLI/` directory (bundled as `fli/`), or null when it is not there. */
        fun load(fileName: String): FliAnimation? {
            val bytes = FliAnimation::class.java.classLoader?.getResourceAsStream("fli/$fileName")?.use { it.readBytes() }
                ?: return null
            return runCatching { FliAnimation(bytes) }.getOrNull()
        }
    }
}

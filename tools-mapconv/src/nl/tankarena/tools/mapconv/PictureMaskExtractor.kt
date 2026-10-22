package nl.tankarena.tools.mapconv

import nl.tankarena.content.GeneratedLegacyPictureCatalog
import nl.tankarena.content.PictureMasks
import java.io.DataOutputStream
import java.io.File
import javax.imageio.ImageIO

/**
 * Writes the opaque-pixel masks of every legacy picture to [PictureMasks]' resource file. The
 * original game collides by pixels (`overlap()` in original/src/check/overlap.c counts pixel pairs that
 * are both not 0xFF); the extracted PNGs keep exactly those pixels opaque.
 */
object PictureMaskExtractor {

    /** [drawableDir] holds the extracted `pic_<key>.png` files. */
    fun run(drawableDir: File, outputFile: File) {
        val masks = GeneratedLegacyPictureCatalog.resourceKeys.entries.sortedBy { it.key }.mapNotNull { (name, key) ->
            val file = File(drawableDir, "$key.png").takeIf { it.isFile } ?: return@mapNotNull null
            val image = ImageIO.read(file)
            val bits = PictureMasks.emptyBits()
            for (y in 0 until minOf(image.height, PictureMasks.SIZE)) {
                for (x in 0 until minOf(image.width, PictureMasks.SIZE)) {
                    if ((image.getRGB(x, y) ushr 24) != 0) PictureMasks.setBit(bits, x, y)
                }
            }
            name to bits
        }
        outputFile.parentFile?.mkdirs()
        DataOutputStream(outputFile.outputStream().buffered()).use { out ->
            out.writeInt(masks.size)
            for ((name, bits) in masks) {
                out.writeUTF(name)
                out.write(bits)
            }
        }
        println("Wrote ${masks.size} picture masks -> ${outputFile.absolutePath}")
    }
}

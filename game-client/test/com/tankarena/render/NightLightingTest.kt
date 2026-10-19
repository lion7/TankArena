package com.tankarena.render

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.Surface

/** The night shader against the original palettes: DATA/PALETTE.DAT, NIGHT.PAL and NIGHT.LNK. */
class NightLightingTest {

    private fun dataFile(name: String): File =
        listOf(File("../DATA/$name"), File("DATA/$name")).first { it.isFile }

    private fun palette(name: String): List<Triple<Int, Int, Int>> {
        val bytes = dataFile(name).readBytes()
        return List(256) { Triple(bytes[it * 3].toInt(), bytes[it * 3 + 1].toInt(), bytes[it * 3 + 2].toInt()) }
    }

    private fun widen(v: Int) = (v shl 2) or (v ushr 4)

    private fun argb(c: Triple<Int, Int, Int>) = Color.makeARGB(255, widen(c.first), widen(c.second), widen(c.third))

    @Test
    fun `dark pixels take the dark half of the night palette and lit pixels the lit half`() {
        val day = palette("PALETTE.DAT")
        val night = palette("NIGHT.PAL")
        val link = dataFile("NIGHT.LNK").readBytes().map { it.toInt() and 0xFF }
        val index = 31 // a light blue that NIGHT.LNK sends to the dark half
        val lut = Image.makeFromEncoded(listOf(File("composeResources/drawable/night_lut.png"), File("game-client/composeResources/drawable/night_lut.png")).first { it.isFile }.readBytes())

        // Content: 4×1 pixels of palette colour 31. Mask: only the right half lit.
        val content = Surface.makeRasterN32Premul(4, 1)
        content.canvas.clear(argb(day[index]))
        val mask = Surface.makeRasterN32Premul(4, 1)
        mask.canvas.drawRect(Rect.makeXYWH(2f, 0f, 2f, 1f), Paint().apply { color = Color.WHITE })
        val filter = NightLighting.nightFilter(RuntimeEffect.makeForShader(NightLighting.SKSL), mask.makeImageSnapshot(), lut, 1f, 1f)

        val out = Surface.makeRasterN32Premul(4, 1)
        out.canvas.drawImage(content.makeImageSnapshot(), 0f, 0f, Paint().apply { imageFilter = filter })
        val bitmap = Bitmap().apply { allocPixels(ImageInfo.makeN32Premul(4, 1)) }
        out.readPixels(bitmap, 0, 0)

        assertEquals(argb(night[link[index]]), bitmap.getColor(0, 0), "dark")
        assertEquals(argb(night[link[index] or 0x80]), bitmap.getColor(3, 0), "lit")
    }
}

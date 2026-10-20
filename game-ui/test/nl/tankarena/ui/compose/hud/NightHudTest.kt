package nl.tankarena.ui.compose.hud

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.use
import nl.tankarena.protocol.snapshot.HudState
import nl.tankarena.protocol.snapshot.PlayerView
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/**
 * The status display of a night mission against the original palettes: text in
 * `cmap[c] = night_link[c] + 0x80` (the lit half of NIGHT.PAL), the small weapon icons (not
 * `night_vis`) in the dark half.
 */
class NightHudTest {

    private fun dataFile(name: String): File = listOf(File("../DATA/$name"), File("DATA/$name")).first { it.isFile }

    private fun palette(name: String): List<Int> {
        val bytes = dataFile(name).readBytes()
        fun widen(v: Int) = (v shl 2) or (v ushr 4)
        return List(256) { (widen(bytes[it * 3].toInt()) shl 16) or (widen(bytes[it * 3 + 1].toInt()) shl 8) or widen(bytes[it * 3 + 2].toInt()) }
    }

    private val day = palette("PALETTE.DAT")
    private val night = palette("NIGHT.PAL")
    private val link = dataFile("NIGHT.LNK").readBytes().map { it.toInt() and 0xFF }

    private val hud = HudState(
        armor = 100, maxArmor = 100, fuel = 100, maxFuel = 100, currentWeapon = 0,
        ammo = listOf(50, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
        messageLine1 = "HHHH", messageSteps = 29,
    )

    private fun render(night: Boolean): Bitmap {
        val view = PlayerView(playerId = 0, controlledActorId = null, cameraCenterX = 0, cameraCenterY = 0, hud = hud)
        return ImageComposeScene(WIDTH, HEIGHT) {
            HudOverlay(hud, view, Rect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat()), scale = 1f, night = night, modifier = Modifier.fillMaxSize())
        }.use { scene ->
            val start = System.nanoTime()
            repeat(30) {
                scene.render(System.nanoTime() - start)
                Thread.sleep(10)
            }
            val image = scene.render(System.nanoTime() - start)
            System.getenv("DUMP_DIR")?.let { File(it, "hud-${if (night) "night" else "day"}.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes) }
            Bitmap.makeFromImage(image)
        }
    }

    private fun Bitmap.rgb(x: Int, y: Int): Int = getColor(x, y) and 0xFFFFFF
    private fun Bitmap.opaque(x: Int, y: Int): Boolean = (getColor(x, y) ushr 24) == 0xFF

    @Test
    fun `at night the text takes the lit half and the small weapon icons the dark half of the night palette`() {
        val byDay = render(night = false)
        val byNight = render(night = true)

        // write_message(): the first line at (10 + b_size, buf_y - 11 - b_size), in cmap[15].
        val textY = HEIGHT - 11 - 33
        val text = (43 until 43 + 24).flatMap { x -> (textY until textY + 5).map { x to it } }.filter { (x, y) -> byDay.opaque(x, y) }
        assertTrue(text.isNotEmpty(), "the message is drawn")
        for ((x, y) in text) {
            assertEquals(day[15], byDay.rgb(x, y), "day text at $x,$y")
            assertEquals(night[link[15] or 0x80], byNight.rgb(x, y), "night text at $x,$y")
        }

        // write_weapons(): ICON1 at (0, buf_y - 11), its pixels through NIGHT.LNK only.
        var checked = 0
        for (y in HEIGHT - 11 until HEIGHT) for (x in 0 until 8) {
            if (!byDay.opaque(x, y)) continue
            val index = day.indexOf(byDay.rgb(x, y)).takeIf { it >= 0 } ?: continue
            // The SELECT frame over the current weapon is night_vis; leave its pixels out.
            if (byNight.rgb(x, y) == night[link[index] or 0x80]) continue
            assertEquals(night[link[index]], byNight.rgb(x, y), "night icon at $x,$y (colour $index)")
            checked++
        }
        assertTrue(checked > 10, "icon pixels checked: $checked")
    }

    private companion object {
        const val WIDTH = 320
        const val HEIGHT = 200
    }
}

package com.tankarena.app.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import com.tankarena.sim.map.MapEditor
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/** The editor draws a shipped map offscreen; the picture is kept in `build/editor-begin1.png` to look at. */
class EditorRenderTest {
    @Test
    fun `the editor draws a shipped map`() {
        val choice = EditorMapChoice("begin1", userMap = false)
        ImageComposeScene(1280, 800) {
            MaterialTheme { EditorScreen(choice, MapEditor(choice.load()), onPlayTest = {}, onClose = {}) }
        }.use { scene ->
            var time = 0L
            repeat(5) { scene.render(time); time += 16_000_000L; Thread.sleep(50) }
            val png = scene.render(time).encodeToData(EncodedImageFormat.PNG)!!.bytes
            File("build").mkdirs()
            File("build/editor-begin1.png").writeBytes(png)
            val image = ImageIO.read(png.inputStream())
            // The map area (left of the palette) shows pictures, not just the background colour.
            val colours = (0 until 400 step 7).flatMap { x -> (60 until 400 step 7).map { y -> image.getRGB(x, y) } }.toSet()
            assertTrue(colours.size > 20, "the map area looks empty (${colours.size} colours)")
        }
    }
}

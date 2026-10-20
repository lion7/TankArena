package com.tankarena.app.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import com.tankarena.content.StructureLibrary
import com.tankarena.content.TankArenaWorld
import com.tankarena.sim.map.MapEditor
import com.tankarena.sim.map.MenWeapon
import com.tankarena.sim.map.NewMapStart
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/** The editor draws maps offscreen; the pictures are kept in `build/editor-*.png` to look at. */
class EditorRenderTest {
    @Test
    fun `a new map shows its structures and the goal layer`() {
        val editor = MapEditor(MapEditor.newDocument("fresh", TankArenaWorld.TEMPERATE, 24, 16, NewMapStart.BORDER), unsaved = true)
        val structures = StructureLibrary.forWorld(editor.world)
        editor.fillRegion(structures.first { it.cells.size == 1 && it.cells[0].pictures[0] >= 0 }, 1, 1, 22, 14)
        val building = structures.first { it.cells.any { c -> c.pictures[1] >= 0 } }
        editor.placeStructure(building, 5, 5)
        editor.changeGoal(5, 5, 50)
        editor.setBonus(5, 5, 2)
        editor.addMan(5, 5, MenWeapon.CHAIN_GUN)
        for (tool in listOf(EditorTool.GOALS, EditorTool.STRUCTURES)) {
            ImageComposeScene(1280, 800) {
                EditorScreen(EditorMapChoice("fresh", userMap = true), editor, onPlayTest = {}, onClose = {}, initialTool = tool)
            }.use { scene ->
                var time = 0L
                repeat(5) { scene.render(time); time += 16_000_000L; Thread.sleep(50) }
                File("build").mkdirs()
                File("build/editor-new-map-${tool.label}.png").writeBytes(scene.render(time).encodeToData(EncodedImageFormat.PNG)!!.bytes)
            }
        }
    }

    @Test
    fun `the objects tool shows a selected vehicle with its waypoints and settings`() {
        val choice = EditorMapChoice("air", userMap = false)
        ImageComposeScene(1280, 800) {
            EditorScreen(choice, MapEditor(choice.load()), onPlayTest = {}, onClose = {}, initialTool = EditorTool.OBJECTS, initialSelection = 4)
        }.use { scene ->
            var time = 0L
            repeat(5) { scene.render(time); time += 16_000_000L; Thread.sleep(50) }
            File("build").mkdirs()
            File("build/editor-objects.png").writeBytes(scene.render(time).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
    }

    @Test
    fun `the map page shows the settings and texts`() {
        val choice = EditorMapChoice("begin3", userMap = false)
        ImageComposeScene(1280, 800) {
            EditorScreen(choice, MapEditor(choice.load()), onPlayTest = {}, onClose = {}, initialTool = EditorTool.MAP)
        }.use { scene ->
            var time = 0L
            repeat(5) { scene.render(time); time += 16_000_000L; Thread.sleep(50) }
            File("build").mkdirs()
            File("build/editor-map-page.png").writeBytes(scene.render(time).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
    }

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

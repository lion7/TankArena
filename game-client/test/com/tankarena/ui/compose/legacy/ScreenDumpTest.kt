package com.tankarena.ui.compose.legacy

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import com.tankarena.ui.compose.DualMatch
import com.tankarena.ui.compose.menu.MissionCatalog
import com.tankarena.ui.compose.menu.TankSelectScreen
import java.io.File
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat

/** Not a test: `DUMP_DIR=... ./kotlin test -m game-client` renders screens offscreen to PNG. */
class ScreenDumpTest {
    @Test
    fun dump() {
        val out = System.getenv("DUMP_DIR") ?: return
        val missions = runBlocking { MissionCatalog.load() }
        val flagIsle = missions.first { it.baseName == "flagisle" }
        val select = missions.first { it.baseName == "select" }
        ImageComposeScene(1280, 960) {
            TankSelectScreen(DualMatch(flagIsle), select, listOf(0, 5), { null }, {}, {})
        }.use { scene ->
            val start = System.nanoTime()
            for (shot in 1..3) {
                repeat(60) {
                    scene.render(System.nanoTime() - start)
                    Thread.sleep(16)
                }
                val image = scene.render(System.nanoTime() - start)
                File(out, "select$shot.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            }
        }
    }
}

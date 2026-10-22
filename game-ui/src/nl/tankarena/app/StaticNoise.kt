package nl.tankarena.app

import nl.tankarena.render.imageFromArgb
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.geometry.Rect
import nl.tankarena.content.LEGACY_TILE_SIZE
import nl.tankarena.protocol.snapshot.PlayerView
import nl.tankarena.ui.compose.hud.HudOverlay
import nl.tankarena.ui.compose.legacy.LegacyFonts
import kotlin.random.Random

/**
 * Legacy write_screen() with no vehicle to follow (original/src/game/mainloop.c:81-91): every frame four
 * 33×33 tiles (STATIC1..4) are filled with random colours 0..15 of the palette and the window is
 * tiled with them at random. The game's own random numbers stay untouched.
 */
@Composable
internal fun StaticNoise(playfieldWidth: Int, modifier: Modifier = Modifier) {
    val random = remember { Random(System.nanoTime()) }
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) withFrameNanos { frame = it }
    }
    Canvas(modifier = modifier) {
        frame.let { }
        val tiles = List(4) { noiseTile(random) }
        val scale = size.width / playfieldWidth
        val side = LEGACY_TILE_SIZE * scale
        var y = 0f
        while (y < size.height) {
            var x = 0f
            while (x < size.width) {
                drawImage(
                    tiles[random.nextInt(tiles.size)],
                    IntOffset.Zero,
                    IntSize(LEGACY_TILE_SIZE, LEGACY_TILE_SIZE),
                    IntOffset(x.toInt(), y.toInt()),
                    IntSize(side.toInt() + 1, side.toInt() + 1),
                    filterQuality = FilterQuality.None,
                )
                x += side
            }
            y += side
        }
    }
}

private fun noiseTile(random: Random): ImageBitmap =
    imageFromArgb(LEGACY_TILE_SIZE, LEGACY_TILE_SIZE, IntArray(LEGACY_TILE_SIZE * LEGACY_TILE_SIZE) {
        (0xFF shl 24) or LegacyFonts.gamePalette[random.nextInt(16)]
    })

/** Legacy write_status() without a vehicle: only write_1p_info() (goal counters and the picked id). */
@Composable
internal fun NoVehicleStatus(view: PlayerView, playfieldWidth: Int) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        HudOverlay(
            hudState = view.hud,
            playerView = view,
            playfield = Rect(0f, 0f, width, height),
            scale = width / playfieldWidth,
            goalCountersOnly = true,
            modifier = Modifier.fillMaxSize(),
        )
    }
}


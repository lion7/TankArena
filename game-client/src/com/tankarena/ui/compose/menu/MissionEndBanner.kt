package com.tankarena.ui.compose.menu

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import com.tankarena.render.kubriko.LegacySpriteCatalog
import com.tankarena.ui.compose.MissionOutcome
import org.jetbrains.compose.resources.imageResource

/**
 * How long the banner stays over the still running game: legacy `game.end_round = tinit(300)`
 * at 100 timer ticks per second.
 */
const val MISSION_END_BANNER_MILLIS: Long = 3_000

/** Legacy `b_size`: the banner pictures are 33×33 tiles. */
private const val TILE = 33

/** Width of the legacy playfield the banner is centred in, in playfield pixels. */
private const val PLAYFIELD_WIDTH = 640

/**
 * Legacy `write_winner_1p` (src/world/status.c): a 4×2 grid of 33 px tiles centred in the
 * playfield, `MISSIONA`..`MISSIOND` on top and, one tile lower, `COMPLA`..`COMPLD` for a
 * completed mission or `FAILEDA`..`FAILEDD` (with the skull) for a failed one. Scaled with the
 * playfield ([playfieldWidth] playfield pixels across this composable), nearest-neighbour.
 */
@Composable
fun MissionEndBanner(
    outcome: MissionOutcome,
    modifier: Modifier = Modifier,
    playfieldWidth: Int = PLAYFIELD_WIDTH,
) {
    val bottomRow = if (outcome == MissionOutcome.WON) "COMPL" else "FAILED"
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val pixel = maxWidth / playfieldWidth
        val viewHeight = maxHeight / pixel
        val x = (playfieldWidth - 4 * TILE) / 2f
        val y = (viewHeight - 3 * TILE) / 2f
        for (row in 0..1) {
            val prefix = if (row == 0) "MISSION" else bottomRow
            for (column in 0..3) {
                val resource = LegacySpriteCatalog.shared.findResource(prefix + ('A' + column)) ?: continue
                Box(
                    modifier = Modifier
                        .offset(pixel * (x + column * TILE), pixel * (y + row * 2 * TILE))
                        .size(pixel * TILE),
                ) {
                    Image(
                        bitmap = imageResource(resource),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        filterQuality = FilterQuality.None,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

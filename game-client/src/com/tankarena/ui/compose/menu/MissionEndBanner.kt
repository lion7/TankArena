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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import com.tankarena.render.kubriko.LegacySpriteCatalog
import com.tankarena.ui.compose.MissionOutcome
import com.tankarena.ui.compose.hud.rememberNightHudEffect
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
    /** Legacy `map.night`: the pictures are `night_vis`, in the lit night colours. */
    night: Boolean = false,
) {
    val bottomRow = if (outcome == MissionOutcome.WON) "COMPL" else "FAILED"
    TileBanner(listOf("MISSION", bottomRow), columns = 4, modifier = modifier, playfieldWidth = playfieldWidth, night = night)
}

/**
 * Legacy `write_winner_2p` (src/world/status.c): in the window of the player who won the round,
 * `YOUA` `YOUB` and one tile lower `WINA` `WINB`, centred like the mission banner.
 */
@Composable
fun YouWinBanner(modifier: Modifier = Modifier, playfieldWidth: Int = PLAYFIELD_WIDTH, night: Boolean = false) {
    TileBanner(listOf("YOU", "WIN"), columns = 2, modifier = modifier, playfieldWidth = playfieldWidth, night = night)
}

/** Rows of [columns] 33 px tiles `<prefix>A`, `<prefix>B`, ... centred, a blank tile row apart. */
@Composable
private fun TileBanner(prefixes: List<String>, columns: Int, modifier: Modifier, playfieldWidth: Int, night: Boolean) {
    val nightColours = if (night) rememberNightHudEffect(lit = true) else null
    BoxWithConstraints(modifier = modifier.fillMaxSize().graphicsLayer { renderEffect = nightColours }) {
        val pixel = maxWidth / playfieldWidth
        val viewHeight = maxHeight / pixel
        val x = (playfieldWidth - columns * TILE) / 2f
        val y = (viewHeight - 3 * TILE) / 2f
        prefixes.forEachIndexed { row, prefix ->
            for (column in 0 until columns) {
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

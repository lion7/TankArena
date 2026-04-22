package com.tankarena.ui.compose.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import com.tankarena.protocol.Team
import com.tankarena.protocol.snapshot.PlayerView

@Composable
fun RadarOverlay(
    playerView: PlayerView,
    worldWidth: Int,
    worldHeight: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(Color(0xCC000000))
            .border(1.dp, Color(0xFF1FA8FF))
            .size(120.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(Color(0x2200FF66), style = Stroke(width = 1f))
            playerView.radar.forEach { contact ->
                val px = if (worldWidth <= 0) 0f else size.width * contact.approximateX / worldWidth.toFloat()
                val py = if (worldHeight <= 0) 0f else size.height * contact.approximateY / worldHeight.toFloat()
                drawCircle(
                    color = when (contact.team) {
                        Team.PLAYER -> Color(0xFF6DD3FF)
                        Team.ENEMY -> Color(0xFFFF6D6D)
                        else -> Color(0xFFB0BEC5)
                    },
                    radius = 3f,
                    center = Offset(px, py),
                    style = Fill,
                )
            }
        }
    }
}

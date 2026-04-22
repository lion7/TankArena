package com.tankarena.render.kubriko.actor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.protocol.ActorView
import com.tankarena.protocol.Team
import com.tankarena.render.kubriko.RuntimeSnapshot

internal class GoalActor(
    id: Long,
    snapshot: RuntimeSnapshot,
) : RenderActor(
    id = id,
    snapshot = snapshot,
    width = LEGACY_TILE_SIZE,
    height = LEGACY_TILE_SIZE,
) {
    private var team: Team = Team.NEUTRAL

    fun sync(actor: ActorView) {
        team = actor.team
        setCenter(
            x = snapshot.renderOffsetX + actor.x,
            y = snapshot.renderOffsetY + actor.y,
        )
    }

    override fun DrawScope.draw() {
        drawCircle(
            color = when (team) {
                Team.PLAYER -> Color(0xFF6DD3FF)
                Team.ENEMY -> Color(0xFFFFB454)
                Team.NEUTRAL -> Color(0xFFB8C1CC)
            },
            radius = body.size.width.raw / 2f - 2f,
            style = Stroke(width = 2f),
        )
    }
}

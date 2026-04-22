package com.tankarena.render.kubriko.actor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.tankarena.protocol.ProjectileView
import com.tankarena.render.kubriko.RuntimeSnapshot

private const val PROJECTILE_BODY_SIZE = 3

internal class ProjectileActor(
    id: Long,
    snapshot: RuntimeSnapshot,
) : RenderActor(
    id = id,
    snapshot = snapshot,
    width = PROJECTILE_BODY_SIZE,
    height = PROJECTILE_BODY_SIZE,
) {
    fun sync(projectile: ProjectileView) {
        setCenter(
            x = snapshot.renderOffsetX + projectile.x,
            y = snapshot.renderOffsetY + projectile.y,
        )
    }

    override fun DrawScope.draw() {
        val center = body.size.width.raw / 2f
        drawRect(Color.Black, Offset(center - 0.5f, center - 1.5f), Size(1f, 3f))
        drawRect(Color.Black, Offset(center - 1.5f, center - 0.5f), Size(3f, 1f))
    }
}

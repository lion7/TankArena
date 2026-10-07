package com.tankarena.render.kubriko.actor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.tankarena.render.kubriko.RuntimeSnapshot

internal class WallActor(
    val centerX: Int,
    val centerY: Int,
    width: Int,
    height: Int,
    snapshot: RuntimeSnapshot,
) : RenderActor(
    id = ((centerX.toLong() shl 32) xor centerY.toLong()),
    snapshot = snapshot,
    width = width,
    height = height,
) {
    init {
        setCenter(
            x = snapshot.renderOffsetX + centerX,
            y = snapshot.renderOffsetY + centerY,
        )
    }

    override fun DrawScope.draw() {
        drawRect(color = Color(0xFF4F5B66))
    }
}

package com.tankarena.render.actor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.tankarena.render.RenderBody
import com.tankarena.render.RuntimeSnapshot
import com.tankarena.render.SceneDrawable

internal abstract class RenderActor(
    val id: Long,
    protected val snapshot: RuntimeSnapshot,
    width: Int,
    height: Int,
) : SceneDrawable {
    final override val body: RenderBody = RenderBody(
        position = Offset(0f, 0f),
        size = Size(width.toFloat(), height.toFloat()),
    )

    override val layerIndex: Int = 2

    protected fun setCenter(x: Float, y: Float) {
        body.position = Offset(x, y)
    }
}

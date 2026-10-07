package com.tankarena.render.kubriko.actor

import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.actor.traits.Visible
import com.tankarena.render.kubriko.RuntimeSnapshot

internal abstract class RenderActor(
    val id: Long,
    protected val snapshot: RuntimeSnapshot,
    width: Int,
    height: Int,
) : Visible {
    final override val body: BoxBody = BoxBody(
        initialPosition = sceneOffsetOf(0f, 0f),
        initialSize = sceneSizeOf(width.toFloat(), height.toFloat()),
    )

    override val layerIndex: Int = 2

    protected fun setCenter(x: Float, y: Float) {
        body.position = sceneOffsetOf(x, y)
    }
}

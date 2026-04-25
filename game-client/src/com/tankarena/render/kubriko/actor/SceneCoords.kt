package com.tankarena.render.kubriko.actor

import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.SceneOffset
import com.pandulapeter.kubriko.types.SceneSize

internal fun sceneOffsetOf(x: Float, y: Float): SceneOffset = SceneOffset(
    x = x.sceneUnit,
    y = y.sceneUnit,
)

internal fun sceneSizeOf(width: Float, height: Float): SceneSize = SceneSize(
    width = width.sceneUnit,
    height = height.sceneUnit,
)

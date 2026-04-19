package com.tankarena.sim.actor

import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.SceneOffset
import com.tankarena.core.Int2

internal fun Int2.toSceneOffset(): SceneOffset = SceneOffset(
    x = x.toFloat().sceneUnit,
    y = y.toFloat().sceneUnit,
)

internal fun sceneOffsetOf(x: Int, y: Int): SceneOffset = SceneOffset(
    x = x.toFloat().sceneUnit,
    y = y.toFloat().sceneUnit,
)

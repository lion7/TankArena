package com.tankarena.client

import com.tankarena.core.PlayerAction
import com.tankarena.core.PlayerIntent
import korlibs.event.Key
import korlibs.korge.input.InputKeys

class KeyboardActionMapper(
    private val playerId: Int,
) {
    private var fireHeldLastFrame: Boolean = false

    fun sample(keys: InputKeys): PlayerIntent {
        val actions = linkedSetOf<PlayerAction>()

        if (keys[Key.W] || keys[Key.UP]) actions += PlayerAction.MoveUp
        if (keys[Key.S] || keys[Key.DOWN]) actions += PlayerAction.MoveDown
        if (keys[Key.A] || keys[Key.LEFT]) actions += PlayerAction.MoveLeft
        if (keys[Key.D] || keys[Key.RIGHT]) actions += PlayerAction.MoveRight

        val fireDown = keys[Key.SPACE]
        if (fireDown && !fireHeldLastFrame) actions += PlayerAction.FirePrimary
        fireHeldLastFrame = fireDown

        return PlayerIntent(playerId = playerId, activeActions = actions)
    }
}

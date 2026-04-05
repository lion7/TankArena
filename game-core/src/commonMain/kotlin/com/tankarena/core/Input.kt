package com.tankarena.core

enum class PlayerAction {
    MoveUp,
    MoveDown,
    MoveLeft,
    MoveRight,
    FirePrimary,
    FireSecondary,
}

data class PlayerIntent(
    val playerId: Int,
    val activeActions: Set<PlayerAction>,
)

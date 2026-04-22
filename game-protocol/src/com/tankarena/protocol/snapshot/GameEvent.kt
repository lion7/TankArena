package com.tankarena.protocol.snapshot

import kotlinx.serialization.Serializable

@Serializable
sealed interface GameEvent {
    @Serializable
    data class Fired(val actorId: Long, val x: Int, val y: Int) : GameEvent

    @Serializable
    data class Exploded(val x: Int, val y: Int) : GameEvent

    @Serializable
    data class TankDestroyed(val actorId: Long) : GameEvent

    @Serializable
    data class TankSpawned(val actorId: Long) : GameEvent

    @Serializable
    data class DamageTaken(val actorId: Long, val amount: Int) : GameEvent

    @Serializable
    data class MissionWon(val playerId: Int) : GameEvent

    @Serializable
    data class MissionLost(val playerId: Int) : GameEvent
}

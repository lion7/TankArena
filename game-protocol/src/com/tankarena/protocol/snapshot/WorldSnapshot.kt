package com.tankarena.protocol.snapshot

import kotlinx.serialization.Serializable

/**
 * Full authoritative world snapshot emitted by the server every server tick.
 * Clients replicate this onto existing render actors by matching [ActorState.actorId].
 *
 * For now this is a full-state snapshot per tick; the shape accommodates per-actor deltas
 * or binary framing later without schema churn on the consumer side.
 */
@Serializable
data class WorldSnapshot(
    val tick: Long,
    val actors: List<ActorState> = emptyList(),
    val events: List<GameEvent> = emptyList(),
)

@Serializable
data class PlayerView(
    val playerId: Int,
    val controlledActorId: Long?,
    val cameraCenterX: Int,
    val cameraCenterY: Int,
    val hud: HudState,
    val radar: List<RadarContact> = emptyList(),
)

@Serializable
data class HudState(
    val armor: Int = 0,
    val fuel: Int = 0,
    val lives: Int = 0,
    val missionCode: String = "",
    val statusText: String = "",
)

@Serializable
data class RadarContact(
    val actorId: Long,
    val approximateX: Int,
    val approximateY: Int,
    val kind: RadarContactKind,
)

@Serializable
enum class RadarContactKind { TANK, TURRET, PROJECTILE, GOAL }

/**
 * Top-level envelope broadcast to all connected clients each server tick.
 * Pairs a world-authoritative snapshot with per-player views (camera, HUD, radar).
 */
@Serializable
data class ServerFrame(
    val tick: Long,
    val world: WorldSnapshot,
    val playerViews: List<PlayerView> = emptyList(),
)

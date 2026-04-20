package com.tankarena.protocol

import kotlinx.serialization.Serializable

@Serializable
data class InputFrame(
    val playerId: Int,
    val inputSequence: Long,
    val forward: Boolean = false,
    val reverse: Boolean = false,
    val turnLeft: Boolean = false,
    val turnRight: Boolean = false,
    val aimLeft: Boolean = false,
    val aimRight: Boolean = false,
    val firePrimary: Boolean = false,
    val fireSecondary: Boolean = false,
    val shield: Boolean = false,
    val cycleWeaponLeft: Boolean = false,
    val cycleWeaponRight: Boolean = false,
)

@Serializable
data class FrameEnvelope(
    val serverTick: Long,
    val globalEvents: List<GlobalEvent> = emptyList(),
    val playerFrames: List<PlayerFrame> = emptyList(),
)

@Serializable
data class PlayerFrame(
    val playerId: Int,
    val controlledActorId: Long?,
    val camera: CameraView,
    val replicatedActors: List<ActorView> = emptyList(),
    val replicatedProjectiles: List<ProjectileView> = emptyList(),
    val replicatedPickups: List<PickupView> = emptyList(),
    val dynamicTileStates: List<DynamicTileState> = emptyList(),
    val radarContacts: List<RadarContact> = emptyList(),
    val hudState: HudState = HudState(playerId = playerId),
    val playerEvents: List<PlayerEvent> = emptyList(),
)

@Serializable
data class CameraView(
    val centerX: Int = 0,
    val centerY: Int = 0,
    val width: Int = 640,
    val height: Int = 400,
)

@Serializable
data class ActorView(
    val id: Long,
    val type: ActorType,
    val team: Team = Team.NEUTRAL,
    val x: Int,
    val y: Int,
    val vx: Float = 0f,
    val vy: Float = 0f,
    val health: Int = 0,
    val stateFlags: Set<ActorStateFlag> = emptySet(),
    val tankType: Int = 0,
    val bodyDirection: Int = 0,
    val turretDirection: Int = 0,
    val primaryCooldownTicks: Int = 0,
)

@Serializable
data class ProjectileView(
    val id: Long,
    val ownerId: Long,
    val x: Int,
    val y: Int,
    val vx: Int,
    val vy: Int,
)

@Serializable
data class PickupView(
    val id: Long,
    val x: Int,
    val y: Int,
    val type: String,
)

@Serializable
data class DynamicTileState(
    val tileX: Int,
    val tileY: Int,
    val state: String,
)

@Serializable
data class RadarContact(
    val id: Long,
    val approximateX: Int,
    val approximateY: Int,
    val team: Team? = null,
    val typeHint: ActorType,
)

@Serializable
data class HudState(
    val playerId: Int,
    val armor: Int = 0,
    val fuel: Int = 0,
    val lives: Int = 0,
    val missionProgress: Int = 0,
    val missionCode: String = "",
    val statusText: String = "",
)

@Serializable
sealed interface GlobalEvent {
    @Serializable
    data class WeaponFired(val actorId: Long, val x: Int, val y: Int) : GlobalEvent

    @Serializable
    data class ProjectileExploded(val x: Int, val y: Int) : GlobalEvent

    @Serializable
    data class ActorDestroyed(val actorId: Long) : GlobalEvent
}

@Serializable
sealed interface PlayerEvent {
    @Serializable
    data class DamageTaken(val actorId: Long, val amount: Int) : PlayerEvent

    @Serializable
    data object MissionWon : PlayerEvent

    @Serializable
    data object MissionLost : PlayerEvent
}

@Serializable
enum class ActorType { TANK, TURRET, PROJECTILE, GOAL, PICKUP }

@Serializable
enum class Team { PLAYER, ENEMY, NEUTRAL }

@Serializable
enum class ActorStateFlag { ALIVE, CONTROLLED, INVULNERABLE }

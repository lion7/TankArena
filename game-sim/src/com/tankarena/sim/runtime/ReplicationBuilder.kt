package com.tankarena.sim.runtime

import com.tankarena.content.CanonicalMapDefinition
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.protocol.Team
import com.tankarena.protocol.snapshot.ActorState
import com.tankarena.protocol.snapshot.GameEvent
import com.tankarena.protocol.snapshot.GoalState
import com.tankarena.protocol.snapshot.HudState
import com.tankarena.protocol.snapshot.PlayerView
import com.tankarena.protocol.snapshot.ProjectileOwnerKind
import com.tankarena.protocol.snapshot.ProjectileState
import com.tankarena.protocol.snapshot.RadarContact
import com.tankarena.protocol.snapshot.RadarContactKind
import com.tankarena.protocol.snapshot.ServerFrame
import com.tankarena.protocol.snapshot.TankState
import com.tankarena.protocol.snapshot.TurretState
import com.tankarena.protocol.snapshot.WorldSnapshot
import com.tankarena.sim.ProjectileOwnerKind as SimProjectileOwnerKind
import com.tankarena.sim.SimulationEvent
import com.tankarena.sim.WorldState

private const val LEGACY_VIEWPORT_WIDTH = 640
private const val LEGACY_VIEWPORT_HEIGHT = 400

private data class Camera(
    val centerX: Int,
    val centerY: Int,
    val width: Int,
    val height: Int,
)

internal class ReplicationBuilder(
    private val map: CanonicalMapDefinition,
) {
    fun buildServerFrame(state: WorldState, events: List<SimulationEvent>): ServerFrame {
        val actors = buildList<ActorState> {
            state.tanks.forEach { tank ->
                add(
                    TankState(
                        actorId = tank.id,
                        team = tank.team.toProtocolTeam(),
                        tankType = tank.tankType,
                        x = tank.position.x,
                        y = tank.position.y,
                        vx = tank.velocityX,
                        vy = tank.velocityY,
                        bodyDirection = tank.bodyDirection,
                        turretDirection = tank.turretDirection,
                        armor = tank.armor,
                        alive = tank.isAlive,
                        primaryCooldownTicks = tank.primaryCooldownTicks,
                    )
                )
            }
            state.turrets.forEach { turret ->
                add(
                    TurretState(
                        actorId = turret.id,
                        team = Team.ENEMY,
                        turretType = turret.turretType,
                        x = turret.position.x,
                        y = turret.position.y,
                        turretDirection = turret.direction,
                        primaryCooldownTicks = turret.cooldownTicks,
                    )
                )
            }
            state.projectiles.forEach { projectile ->
                add(
                    ProjectileState(
                        actorId = projectile.id,
                        ownerId = projectile.ownerId,
                        x = projectile.position.x,
                        y = projectile.position.y,
                        vx = projectile.velocity.x,
                        vy = projectile.velocity.y,
                        ownerKind = projectile.ownerKind.toSnapshotOwnerKind(),
                    )
                )
            }
            state.goals.forEach { goal ->
                add(
                    GoalState(
                        actorId = goal.id,
                        team = Team.NEUTRAL,
                        x = goal.position.x,
                        y = goal.position.y,
                        captured = goal.isClaimed,
                    )
                )
            }
        }

        val playerViews = state.tanks
            .filter { it.playerIndex >= 0 }
            .sortedBy { it.playerIndex }
            .map { controlledTank ->
                val camera = Camera(
                    centerX = controlledTank.position.x,
                    centerY = controlledTank.position.y,
                    width = LEGACY_VIEWPORT_WIDTH,
                    height = LEGACY_VIEWPORT_HEIGHT,
                )
                PlayerView(
                    playerId = controlledTank.playerIndex,
                    controlledActorId = controlledTank.id,
                    cameraCenterX = camera.centerX,
                    cameraCenterY = camera.centerY,
                    cameraWidth = camera.width,
                    cameraHeight = camera.height,
                    hud = HudState(
                        armor = controlledTank.armor,
                        fuel = controlledTank.fuel,
                        lives = controlledTank.lives,
                        missionProgress = state.mission.goalGood,
                        missionCode = map.metadata.missionCode,
                        statusText = state.mission.status.name,
                    ),
                    radar = buildRadar(state, camera, controlledTank.id),
                )
            }

        return ServerFrame(
            tick = state.tick,
            world = WorldSnapshot(
                tick = state.tick,
                actors = actors,
                events = mapEvents(events),
            ),
            playerViews = playerViews,
        )
    }

    private fun buildRadar(
        state: WorldState,
        camera: Camera,
        controlledActorId: Long,
    ): List<RadarContact> {
        return state.tanks
            .filter { it.id != controlledActorId && it.isAlive }
            .filterNot { insideViewport(camera, it.position.x, it.position.y) }
            .map { tank ->
                RadarContact(
                    actorId = tank.id,
                    approximateX = tank.position.x / LEGACY_TILE_SIZE * LEGACY_TILE_SIZE,
                    approximateY = tank.position.y / LEGACY_TILE_SIZE * LEGACY_TILE_SIZE,
                    kind = RadarContactKind.TANK,
                    team = tank.team.toProtocolTeam(),
                )
            } + state.turrets
            .filterNot { insideViewport(camera, it.position.x, it.position.y) }
            .map { turret ->
                RadarContact(
                    actorId = turret.id,
                    approximateX = turret.position.x / LEGACY_TILE_SIZE * LEGACY_TILE_SIZE,
                    approximateY = turret.position.y / LEGACY_TILE_SIZE * LEGACY_TILE_SIZE,
                    kind = RadarContactKind.TURRET,
                    team = Team.ENEMY,
                )
            }
    }

    private fun mapEvents(events: List<SimulationEvent>): List<GameEvent> {
        return events.mapNotNull { event ->
            when (event) {
                is SimulationEvent.FireProjectile -> GameEvent.Fired(
                    actorId = event.ownerId,
                    x = event.origin.x,
                    y = event.origin.y,
                )
                is SimulationEvent.Explosion -> GameEvent.Exploded(
                    x = event.position.x,
                    y = event.position.y,
                )
                is SimulationEvent.TankHit -> GameEvent.DamageTaken(
                    actorId = event.tankId,
                    amount = event.damage,
                )
                is SimulationEvent.TankDestroyed -> GameEvent.TankDestroyed(actorId = event.tankId)
                is SimulationEvent.TankRespawned -> GameEvent.TankSpawned(actorId = event.tankId)
                SimulationEvent.MissionWon -> GameEvent.MissionWon(playerId = -1)
                SimulationEvent.MissionLost -> GameEvent.MissionLost(playerId = -1)
                else -> null
            }
        }
    }

    private fun insideViewport(camera: Camera, x: Int, y: Int): Boolean {
        return x in (camera.centerX - camera.width / 2)..(camera.centerX + camera.width / 2) &&
            y in (camera.centerY - camera.height / 2)..(camera.centerY + camera.height / 2)
    }
}

private fun Int.toProtocolTeam(): Team = when (this) {
    0 -> Team.PLAYER
    1 -> Team.ENEMY
    else -> Team.NEUTRAL
}

private fun SimProjectileOwnerKind.toSnapshotOwnerKind(): ProjectileOwnerKind = when (this) {
    SimProjectileOwnerKind.TANK -> ProjectileOwnerKind.TANK
    SimProjectileOwnerKind.TURRET -> ProjectileOwnerKind.TURRET
}

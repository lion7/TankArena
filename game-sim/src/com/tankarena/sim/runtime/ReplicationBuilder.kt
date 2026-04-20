package com.tankarena.sim.runtime

import com.tankarena.content.CanonicalMapDefinition
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.protocol.ActorStateFlag
import com.tankarena.protocol.ActorType
import com.tankarena.protocol.ActorView
import com.tankarena.protocol.CameraView
import com.tankarena.protocol.FrameEnvelope
import com.tankarena.protocol.GlobalEvent
import com.tankarena.protocol.HudState
import com.tankarena.protocol.PlayerEvent
import com.tankarena.protocol.PlayerFrame
import com.tankarena.protocol.ProjectileView
import com.tankarena.protocol.RadarContact
import com.tankarena.protocol.Team
import com.tankarena.sim.SimulationEvent
import com.tankarena.sim.WorldState

private const val LEGACY_VIEWPORT_WIDTH = 640
private const val LEGACY_VIEWPORT_HEIGHT = 400
private const val RELEVANCE_MARGIN_TILES = 3

internal class ReplicationBuilder(
    private val map: CanonicalMapDefinition,
) {
    fun build(
        state: WorldState,
        events: List<SimulationEvent>,
    ): FrameEnvelope {
        val playerFrames = state.tanks
            .filter { it.playerIndex >= 0 }
            .sortedBy { it.playerIndex }
            .map { controlledTank ->
                val camera = CameraView(
                    centerX = controlledTank.position.x,
                    centerY = controlledTank.position.y,
                    width = LEGACY_VIEWPORT_WIDTH,
                    height = LEGACY_VIEWPORT_HEIGHT,
                )
                val relevantActors = state.tanks.filter { tank ->
                    tank.isAlive && insideRelevance(camera, tank.position.x, tank.position.y)
                }
                val relevantTurrets = state.turrets.filter { turret ->
                    insideRelevance(camera, turret.position.x, turret.position.y)
                }
                val relevantProjectiles = state.projectiles.filter { projectile ->
                    insideRelevance(camera, projectile.position.x, projectile.position.y)
                }
                val radarContacts = buildRadarContacts(state, camera, controlledTank.id)
                PlayerFrame(
                    playerId = controlledTank.playerIndex,
                    controlledActorId = controlledTank.id,
                    camera = camera,
                    replicatedActors = relevantActors.map { tank ->
                        ActorView(
                            id = tank.id,
                            type = ActorType.TANK,
                            team = tank.team.toProtocolTeam(),
                            x = tank.position.x,
                            y = tank.position.y,
                            vx = tank.velocityX,
                            vy = tank.velocityY,
                            health = tank.armor,
                            stateFlags = buildSet {
                                if (tank.isAlive) add(ActorStateFlag.ALIVE)
                                if (tank.id == controlledTank.id) add(ActorStateFlag.CONTROLLED)
                            },
                            tankType = tank.tankType,
                            bodyDirection = tank.bodyDirection,
                            turretDirection = tank.turretDirection,
                            primaryCooldownTicks = tank.primaryCooldownTicks,
                        )
                    } + relevantTurrets.map { turret ->
                        ActorView(
                            id = turret.id,
                            type = ActorType.TURRET,
                            team = Team.ENEMY,
                            x = turret.position.x,
                            y = turret.position.y,
                            health = 100,
                            tankType = turret.turretType,
                            bodyDirection = turret.direction,
                            turretDirection = turret.direction,
                        )
                    },
                    replicatedProjectiles = relevantProjectiles.map { projectile ->
                        ProjectileView(
                            id = projectile.id,
                            ownerId = projectile.ownerId,
                            x = projectile.position.x,
                            y = projectile.position.y,
                            vx = projectile.velocity.x,
                            vy = projectile.velocity.y,
                        )
                    },
                    radarContacts = radarContacts,
                    hudState = HudState(
                        playerId = controlledTank.playerIndex,
                        armor = controlledTank.armor,
                        fuel = controlledTank.fuel,
                        lives = controlledTank.lives,
                        missionProgress = state.mission.goalGood,
                        missionCode = map.metadata.missionCode,
                        statusText = state.mission.status.name,
                    ),
                    playerEvents = mapPlayerEvents(events, controlledTank.id),
                )
            }

        return FrameEnvelope(
            serverTick = state.tick,
            globalEvents = mapGlobalEvents(events),
            playerFrames = playerFrames,
        )
    }

    private fun buildRadarContacts(
        state: WorldState,
        camera: CameraView,
        controlledActorId: Long,
    ): List<RadarContact> {
        return state.tanks
            .filter { it.id != controlledActorId && it.isAlive }
            .filterNot { insideViewport(camera, it.position.x, it.position.y) }
            .map { tank ->
                RadarContact(
                    id = tank.id,
                    approximateX = tank.position.x / LEGACY_TILE_SIZE * LEGACY_TILE_SIZE,
                    approximateY = tank.position.y / LEGACY_TILE_SIZE * LEGACY_TILE_SIZE,
                    team = tank.team.toProtocolTeam(),
                    typeHint = ActorType.TANK,
                )
            } + state.turrets
            .filterNot { insideViewport(camera, it.position.x, it.position.y) }
            .map { turret ->
                RadarContact(
                    id = turret.id,
                    approximateX = turret.position.x / LEGACY_TILE_SIZE * LEGACY_TILE_SIZE,
                    approximateY = turret.position.y / LEGACY_TILE_SIZE * LEGACY_TILE_SIZE,
                    team = Team.ENEMY,
                    typeHint = ActorType.TURRET,
                )
            }
    }

    private fun mapGlobalEvents(events: List<SimulationEvent>): List<GlobalEvent> {
        return events.mapNotNull { event ->
            when (event) {
                is SimulationEvent.FireProjectile -> GlobalEvent.WeaponFired(
                    actorId = event.ownerId,
                    x = event.origin.x,
                    y = event.origin.y,
                )
                is SimulationEvent.Explosion -> GlobalEvent.ProjectileExploded(
                    x = event.position.x,
                    y = event.position.y,
                )
                is SimulationEvent.TankDestroyed -> GlobalEvent.ActorDestroyed(actorId = event.tankId)
                else -> null
            }
        }
    }

    private fun mapPlayerEvents(events: List<SimulationEvent>, controlledActorId: Long): List<PlayerEvent> {
        return events.mapNotNull { event ->
            when (event) {
                is SimulationEvent.TankHit ->
                    PlayerEvent.DamageTaken(actorId = event.tankId, amount = event.damage)
                        .takeIf { event.tankId == controlledActorId }
                SimulationEvent.MissionWon -> PlayerEvent.MissionWon
                SimulationEvent.MissionLost -> PlayerEvent.MissionLost
                else -> null
            }
        }
    }

    private fun insideRelevance(camera: CameraView, x: Int, y: Int): Boolean {
        val margin = RELEVANCE_MARGIN_TILES * LEGACY_TILE_SIZE
        return x in (camera.centerX - camera.width / 2 - margin)..(camera.centerX + camera.width / 2 + margin) &&
            y in (camera.centerY - camera.height / 2 - margin)..(camera.centerY + camera.height / 2 + margin)
    }

    private fun insideViewport(camera: CameraView, x: Int, y: Int): Boolean {
        return x in (camera.centerX - camera.width / 2)..(camera.centerX + camera.width / 2) &&
            y in (camera.centerY - camera.height / 2)..(camera.centerY + camera.height / 2)
    }
}

private fun Int.toProtocolTeam(): Team = when (this) {
    0 -> Team.PLAYER
    1 -> Team.ENEMY
    else -> Team.NEUTRAL
}

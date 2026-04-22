package com.tankarena.render.kubriko

import com.pandulapeter.kubriko.actor.Actor
import com.pandulapeter.kubriko.manager.ActorManager
import com.pandulapeter.kubriko.sprites.SpriteManager
import com.tankarena.protocol.ActorType
import com.tankarena.protocol.ActorView
import com.tankarena.protocol.PlayerFrame
import com.tankarena.protocol.ProjectileView
import com.tankarena.render.kubriko.actor.GoalActor
import com.tankarena.render.kubriko.actor.ProjectileActor
import com.tankarena.render.kubriko.actor.TankActor
import com.tankarena.render.kubriko.actor.TurretActor
import org.jetbrains.compose.resources.DrawableResource

internal class ReplicatedActorScene(
    private val snapshot: RuntimeSnapshot,
    private val actorManager: ActorManager,
    private val spriteManager: SpriteManager,
    private val sprites: LegacySpriteCatalog,
) {
    private val tanks = LinkedHashMap<Long, TankActor>()
    private val turrets = LinkedHashMap<Long, TurretActor>()
    private val goals = LinkedHashMap<Long, GoalActor>()
    private val projectiles = LinkedHashMap<Long, ProjectileActor>()

    fun sync(playerFrame: PlayerFrame) {
        syncActors(playerFrame.replicatedActors)
        syncProjectiles(playerFrame.replicatedProjectiles)
    }

    fun collectSpriteResources(target: MutableSet<DrawableResource>) {
        tanks.values.forEach { it.collectSpriteResources(target) }
        turrets.values.forEach { it.collectSpriteResources(target) }
    }

    private fun syncActors(actors: List<ActorView>) {
        val tankIds = LinkedHashSet<Long>()
        val turretIds = LinkedHashSet<Long>()
        val goalIds = LinkedHashSet<Long>()

        for (actor in actors) {
            when (actor.type) {
                ActorType.TANK -> {
                    tankIds += actor.id
                    tanks.getOrPut(actor.id) {
                        TankActor(
                            id = actor.id,
                            snapshot = snapshot,
                            spriteManager = spriteManager,
                            sprites = sprites,
                        ).also { actorManager.add(it) }
                    }.sync(actor)
                }

                ActorType.TURRET -> {
                    turretIds += actor.id
                    turrets.getOrPut(actor.id) {
                        TurretActor(
                            id = actor.id,
                            snapshot = snapshot,
                            spriteManager = spriteManager,
                            sprites = sprites,
                        ).also { actorManager.add(it) }
                    }.sync(actor)
                }

                ActorType.GOAL -> {
                    goalIds += actor.id
                    goals.getOrPut(actor.id) {
                        GoalActor(
                            id = actor.id,
                            snapshot = snapshot,
                        ).also { actorManager.add(it) }
                    }.sync(actor)
                }

                else -> Unit
            }
        }

        prune(tanks, tankIds)
        prune(turrets, turretIds)
        prune(goals, goalIds)
    }

    private fun syncProjectiles(projectiles: List<ProjectileView>) {
        val projectileIds = LinkedHashSet<Long>()
        for (projectile in projectiles) {
            projectileIds += projectile.id
            this.projectiles.getOrPut(projectile.id) {
                ProjectileActor(
                    id = projectile.id,
                    snapshot = snapshot,
                ).also { actorManager.add(it) }
            }.sync(projectile)
        }
        prune(this.projectiles, projectileIds)
    }

    private fun <T : Actor> prune(actorsById: MutableMap<Long, T>, keepIds: Set<Long>) {
        if (actorsById.isEmpty()) return
        val staleIds = actorsById.keys.filterNot(keepIds::contains)
        if (staleIds.isEmpty()) return
        val staleActors = staleIds.mapNotNull(actorsById::remove)
        actorManager.remove(staleActors)
    }
}

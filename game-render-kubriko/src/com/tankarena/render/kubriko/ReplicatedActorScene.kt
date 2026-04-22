package com.tankarena.render.kubriko

import com.pandulapeter.kubriko.actor.Actor
import com.pandulapeter.kubriko.manager.ActorManager
import com.pandulapeter.kubriko.sprites.SpriteManager
import com.tankarena.protocol.snapshot.GoalState
import com.tankarena.protocol.snapshot.ProjectileState
import com.tankarena.protocol.snapshot.TankState
import com.tankarena.protocol.snapshot.TurretState
import com.tankarena.protocol.snapshot.WallState
import com.tankarena.protocol.snapshot.WorldSnapshot
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

    fun sync(world: WorldSnapshot) {
        val tankIds = LinkedHashSet<Long>()
        val turretIds = LinkedHashSet<Long>()
        val goalIds = LinkedHashSet<Long>()
        val projectileIds = LinkedHashSet<Long>()

        for (state in world.actors) {
            when (state) {
                is TankState -> {
                    tankIds += state.actorId
                    tanks.getOrPut(state.actorId) {
                        TankActor(
                            id = state.actorId,
                            snapshot = snapshot,
                            spriteManager = spriteManager,
                            sprites = sprites,
                        ).also { actorManager.add(it) }
                    }.sync(state)
                }
                is TurretState -> {
                    turretIds += state.actorId
                    turrets.getOrPut(state.actorId) {
                        TurretActor(
                            id = state.actorId,
                            snapshot = snapshot,
                            spriteManager = spriteManager,
                            sprites = sprites,
                        ).also { actorManager.add(it) }
                    }.sync(state)
                }
                is GoalState -> {
                    goalIds += state.actorId
                    goals.getOrPut(state.actorId) {
                        GoalActor(
                            id = state.actorId,
                            snapshot = snapshot,
                        ).also { actorManager.add(it) }
                    }.sync(state)
                }
                is ProjectileState -> {
                    projectileIds += state.actorId
                    projectiles.getOrPut(state.actorId) {
                        ProjectileActor(
                            id = state.actorId,
                            snapshot = snapshot,
                        ).also { actorManager.add(it) }
                    }.sync(state)
                }
                is WallState -> Unit
            }
        }

        prune(tanks, tankIds)
        prune(turrets, turretIds)
        prune(goals, goalIds)
        prune(projectiles, projectileIds)
    }

    fun collectSpriteResources(target: MutableSet<DrawableResource>) {
        tanks.values.forEach { it.collectSpriteResources(target) }
        turrets.values.forEach { it.collectSpriteResources(target) }
    }

    private fun <T : Actor> prune(actorsById: MutableMap<Long, T>, keepIds: Set<Long>) {
        if (actorsById.isEmpty()) return
        val staleIds = actorsById.keys.filterNot(keepIds::contains)
        if (staleIds.isEmpty()) return
        val staleActors = staleIds.mapNotNull(actorsById::remove)
        actorManager.remove(staleActors)
    }
}

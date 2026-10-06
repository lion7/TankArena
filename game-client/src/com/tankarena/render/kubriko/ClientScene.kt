package com.tankarena.render.kubriko

import com.pandulapeter.kubriko.actor.Actor
import com.pandulapeter.kubriko.manager.ActorManager
import com.pandulapeter.kubriko.sprites.SpriteManager
import com.tankarena.content.LegacySpriteResources
import com.tankarena.protocol.snapshot.B52State
import com.tankarena.protocol.snapshot.DestroyerState
import com.tankarena.protocol.snapshot.EnforcerState
import com.tankarena.protocol.snapshot.FlagState
import com.tankarena.protocol.snapshot.GameEvent
import com.tankarena.protocol.snapshot.GoalState
import com.tankarena.protocol.snapshot.LockMarker
import com.tankarena.protocol.snapshot.LockMarkerState
import com.tankarena.protocol.snapshot.LockState
import com.tankarena.protocol.snapshot.MineState
import com.tankarena.protocol.snapshot.ProductState
import com.tankarena.protocol.snapshot.ProjectileKind
import com.tankarena.protocol.snapshot.ProjectileState
import com.tankarena.protocol.snapshot.TankState
import com.tankarena.protocol.snapshot.TrainState
import com.tankarena.protocol.snapshot.TurretState
import com.tankarena.protocol.snapshot.WallState
import com.tankarena.protocol.snapshot.WarpState
import com.tankarena.protocol.snapshot.WorldSnapshot
import com.tankarena.protocol.snapshot.ZeppelinState
import com.tankarena.render.kubriko.actor.GoalActor
import com.tankarena.render.kubriko.actor.ProjectileActor
import com.tankarena.render.kubriko.actor.SpriteActor
import com.tankarena.render.kubriko.actor.TankActor
import com.tankarena.render.kubriko.actor.TurretActor
import org.jetbrains.compose.resources.DrawableResource

/** Ticks each explosion frame stays on screen (5 frames at 100 Hz ≈ 0.3 s). */
private const val EXPLOSION_TICKS_PER_FRAME = 6
private const val EXPLOSION_FRAMES = 5
private const val EFFECT_LAYER = 3
private const val LOCK_MARKER_ID = Long.MIN_VALUE

internal class ClientScene(
    private val snapshot: RuntimeSnapshot,
    private val actorManager: ActorManager,
    private val spriteManager: SpriteManager,
    private val sprites: LegacySpriteCatalog,
) {
    private val tanks = LinkedHashMap<Long, TankActor>()
    private val turrets = LinkedHashMap<Long, TurretActor>()
    private val goals = LinkedHashMap<Long, GoalActor>()
    private val projectiles = LinkedHashMap<Long, ProjectileActor>()
    private val objects = LinkedHashMap<Long, SpriteActor>()

    private class Effect(val actor: SpriteActor, val startTick: Long)

    private val effects = mutableListOf<Effect>()
    private var nextEffectId = -1L
    private val tankPositions = HashMap<Long, Pair<Int, Int>>()

    private var lockMarkerActor: SpriteActor? = null

    fun sync(world: WorldSnapshot, events: List<GameEvent> = world.events, lockMarker: LockMarker? = null) {
        val tankIds = LinkedHashSet<Long>()
        val turretIds = LinkedHashSet<Long>()
        val goalIds = LinkedHashSet<Long>()
        val projectileIds = LinkedHashSet<Long>()
        val objectIds = LinkedHashSet<Long>()

        for (state in world.actors) {
            when (state) {
                is TankState -> {
                    tankIds += state.actorId
                    tankPositions[state.actorId] = state.x to state.y
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
                    if (state.captured) continue
                    goalIds += state.actorId
                    goals.getOrPut(state.actorId) {
                        GoalActor(
                            id = state.actorId,
                            snapshot = snapshot,
                        ).also { actorManager.add(it) }
                    }.sync(state)
                }
                is ProjectileState -> when (state.kind) {
                    // Legacy bullets are plain pixels; rockets and mortar shells have sprites.
                    ProjectileKind.BULLET, ProjectileKind.CHAIN -> {
                        projectileIds += state.actorId
                        projectiles.getOrPut(state.actorId) {
                            ProjectileActor(
                                id = state.actorId,
                                snapshot = snapshot,
                            ).also { actorManager.add(it) }
                        }.sync(state)
                    }
                    ProjectileKind.ROCKET -> syncObject(objectIds, state.actorId, state.x, state.y, rocketSprite(state))
                    ProjectileKind.MORTAR -> syncObject(objectIds, state.actorId, state.x, state.y, "MORT3")
                }
                is MineState -> syncObject(objectIds, state.actorId, state.x, state.y, "MINE")
                is FlagState -> syncObject(objectIds, state.actorId, state.x, state.y, "VLAG${state.flagType.mod(4) + 1}")
                is ProductState -> if (!state.isCollected) {
                    syncObject(objectIds, state.actorId, state.x, state.y, productSprite(state.productType))
                }
                is WarpState -> syncObject(objectIds, state.actorId, state.x, state.y, "WARP1")
                is TrainState -> if (state.alive) {
                    syncObject(objectIds, state.actorId, state.x, state.y, if (state.isEngine) "WAGO0-2" else "WAGO1-2")
                }
                is ZeppelinState -> if (state.alive) syncObject(objectIds, state.actorId, state.x, state.y, "ZEPAA")
                is B52State -> if (state.alive) syncObject(objectIds, state.actorId, state.x, state.y, "B52AA")
                // Terrain draws walls; locks, destroyers and enforcers are invisible editor markers.
                is WallState, is LockState, is DestroyerState, is EnforcerState -> Unit
            }
        }

        prune(tanks, tankIds)
        prune(turrets, turretIds)
        prune(goals, goalIds)
        prune(projectiles, projectileIds)
        prune(objects, objectIds)
        tankPositions.keys.retainAll(tankIds)
        rocketPositions.keys.retainAll(objectIds)
        rocketDirections.keys.retainAll(objectIds)
        syncEffects(world.tick, events)
        syncLockMarker(lockMarker)
    }

    /** The rocket lock-on marker of this viewport's player (legacy `MARKER + l`). */
    private fun syncLockMarker(marker: LockMarker?) {
        if (marker == null) {
            lockMarkerActor?.let(actorManager::remove)
            lockMarkerActor = null
            return
        }
        val actor = lockMarkerActor
            ?: SpriteActor(LOCK_MARKER_ID, snapshot, spriteManager, sprites, layerIndex = EFFECT_LAYER)
                .also { actorManager.add(it); lockMarkerActor = it }
        actor.spriteName = when (marker.state) {
            LockMarkerState.SEARCHING -> "MARKER"
            LockMarkerState.ON_TARGET -> "MARKER2"
            LockMarkerState.LAUNCHED -> "MARKER3"
            LockMarkerState.OUT_OF_RANGE -> "MARKER4"
        }
        actor.moveTo(marker.x, marker.y)
    }

    fun collectSpriteResources(target: MutableSet<DrawableResource>) {
        tanks.values.forEach { it.collectSpriteResources(target) }
        turrets.values.forEach { it.collectSpriteResources(target) }
        objects.values.forEach { it.collectSpriteResources(target) }
        for (stage in 0 until EXPLOSION_FRAMES) {
            sprites.findResource(LegacySpriteResources.nameForExplosion(stage))?.let(target::add)
        }
        for (name in listOf("MARKER", "MARKER2", "MARKER3", "MARKER4")) {
            sprites.findResource(name)?.let(target::add)
        }
    }

    private fun syncObject(ids: MutableSet<Long>, actorId: Long, x: Int, y: Int, sprite: String) {
        ids += actorId
        objects.getOrPut(actorId) {
            SpriteActor(actorId, snapshot, spriteManager, sprites).also { actorManager.add(it) }
        }.apply {
            spriteName = sprite
            moveTo(x, y)
        }
    }

    /** Rockets steer, so their facing is derived from movement between snapshots. */
    private fun rocketSprite(state: ProjectileState): String {
        rocketPositions[state.actorId]?.let { (px, py) ->
            val dx = state.x - px
            val dy = state.y - py
            if (dx != 0 || dy != 0) {
                rocketDirections[state.actorId] = LegacySpriteResources.direction16FromFacing(dx.coerceIn(-1, 1), dy.coerceIn(-1, 1))
            }
        }
        rocketPositions[state.actorId] = state.x to state.y
        val direction = rocketDirections[state.actorId] ?: 0
        return "ROCK1-${LegacySpriteResources.turretFrameForDirection(direction) + 1}"
    }

    private val rocketPositions = HashMap<Long, Pair<Int, Int>>()
    private val rocketDirections = HashMap<Long, Int>()

    private fun productSprite(productType: Int): String {
        val index = productType.mod(15) + 1
        return if (index < 10) "PRODUCT$index" else "PRODUC$index"
    }

    /** Explosions (and destroyed tanks) become short EXP1..EXP5 animations. */
    private fun syncEffects(tick: Long, events: List<GameEvent>) {
        for (event in events) {
            when (event) {
                is GameEvent.Explosion -> spawnExplosion(event.x, event.y, tick)
                is GameEvent.TankDestroyed -> tankPositions[event.actorId]?.let { (x, y) -> spawnExplosion(x, y, tick) }
                else -> Unit
            }
        }
        val iterator = effects.iterator()
        while (iterator.hasNext()) {
            val effect = iterator.next()
            val stage = ((tick - effect.startTick) / EXPLOSION_TICKS_PER_FRAME).toInt()
            if (stage >= EXPLOSION_FRAMES || stage < 0) {
                actorManager.remove(effect.actor)
                iterator.remove()
            } else {
                effect.actor.spriteName = LegacySpriteResources.nameForExplosion(stage)
            }
        }
    }

    private fun spawnExplosion(x: Int, y: Int, tick: Long) {
        val actor = SpriteActor(nextEffectId--, snapshot, spriteManager, sprites, layerIndex = EFFECT_LAYER).apply {
            spriteName = LegacySpriteResources.nameForExplosion(0)
            moveTo(x, y)
        }
        actorManager.add(actor)
        effects += Effect(actor, tick)
    }

    private fun <T : Actor> prune(actorsById: MutableMap<Long, T>, keepIds: Set<Long>) {
        if (actorsById.isEmpty()) return
        val staleIds = actorsById.keys.filterNot(keepIds::contains)
        if (staleIds.isEmpty()) return
        val staleActors = staleIds.mapNotNull(actorsById::remove)
        actorManager.remove(staleActors)
    }
}

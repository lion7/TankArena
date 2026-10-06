package com.tankarena.render.kubriko

import com.pandulapeter.kubriko.actor.Actor
import com.pandulapeter.kubriko.manager.ActorManager
import com.pandulapeter.kubriko.sprites.SpriteManager
import com.tankarena.content.LegacySpriteResources
import com.tankarena.protocol.snapshot.B52State
import com.tankarena.protocol.snapshot.DestroyerState
import com.tankarena.protocol.snapshot.EffectKind
import com.tankarena.protocol.snapshot.EnforcerState
import com.tankarena.protocol.snapshot.ExplosionKind
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

private const val EFFECT_LAYER = 3

/**
 * Legacy `eff[]` (src/world/effects.c): each effect is a run of pictures, each shown for its
 * delay in ticks. After the first frame the original adds up to half the delay again at random.
 */
private val EFFECT_FRAMES: Map<EffectKind, List<Pair<String, Int>>> = mapOf(
    EffectKind.SMOKE to listOf("SMOKE1" to 50, "SMOKE2" to 50),
    EffectKind.RUIN to listOf("RUIN1" to 25, "RUIN2" to 25, "RUIN3" to 25, "RUIN4" to 25),
    EffectKind.FIRE to listOf("FIRE1" to 13, "FIRE2" to 25, "FIRE3" to 25),
    EffectKind.EXPLOSION to listOf("EXP1" to 25, "EXP2" to 25, "EXP3" to 25, "EXP4" to 25, "EXP5" to 25),
    EffectKind.CHAIN to listOf("CHAIN1" to 13, "CHAIN2" to 13, "CHAIN3" to 13),
    EffectKind.FLAME to listOf("FLAME1" to 25, "FLAME2" to 25, "FLAME3" to 25, "FLAME4" to 25, "FLAME5" to 25),
    EffectKind.PLUF to listOf("PLUF1" to 13, "PLUF2" to 13),
    EffectKind.SPLASH to listOf("SPLASH1" to 25, "SPLASH2" to 25, "SPLASH3" to 25),
    EffectKind.SFIRE to listOf("SFIRE" to 10),
    EffectKind.ROOK to listOf("ROOK1" to 25, "ROOK2" to 25, "ROOK3" to 25, "ROOK4" to 25, "ROOK5" to 25),
    EffectKind.LEXPLOSION to listOf("IXP1" to 25, "IXP2" to 25, "IXP3" to 25, "IXP4" to 25, "IXP5" to 25),
    EffectKind.MORTAR_FIRE to listOf("MFIRE1" to 13, "MFIRE2" to 25, "MFIRE3" to 25),
    EffectKind.LAVA to listOf("LAVA1" to 25, "LAVA2" to 25, "LAVA3" to 25, "LAVA4" to 25),
)
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

    private class Effect(val actor: SpriteActor, val frames: List<Pair<String, Int>>, var frame: Int, var nextFrameTick: Long)

    private val effects = mutableListOf<Effect>()
    private var nextEffectId = -1L

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
                    // Legacy write_bullets(): flames and mortar shells change picture as they fly (`b->dist`).
                    ProjectileKind.MORTAR -> syncObject(objectIds, state.actorId, state.x, state.y, "MORT${flightFrame(state.rangeLeft)}")
                    ProjectileKind.FLAME -> syncObject(objectIds, state.actorId, state.x, state.y, "FLAME${flightFrame(state.rangeLeft)}")
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
        for (frames in EFFECT_FRAMES.values) {
            frames.forEach { (name, _) -> sprites.findResource(name)?.let(target::add) }
        }
        for (index in 1..5) {
            sprites.findResource("FLAME$index")?.let(target::add)
            sprites.findResource("MORT$index")?.let(target::add)
        }
        sprites.findResource("ARMOR")?.let(target::add)
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

    /** Legacy `FLAME + 4 - dist / 25` (and the same for MORTAR): frame 1 at launch, 5 when nearly spent. */
    private fun flightFrame(rangeLeft: Int): Int = (5 - rangeLeft / 25).coerceIn(1, 5)

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

    /** Legacy create_effect()/control_effects(): server effect events play as picture runs. */
    private fun syncEffects(tick: Long, events: List<GameEvent>) {
        for (event in events) {
            when (event) {
                is GameEvent.Effect -> spawnEffect(event.kind, event.x, event.y, tick)
                is GameEvent.Explosion -> spawnEffect(
                    if (event.kind == ExplosionKind.ROCKET) EffectKind.LEXPLOSION else EffectKind.EXPLOSION,
                    event.x,
                    event.y,
                    tick,
                )
                else -> Unit
            }
        }
        val iterator = effects.iterator()
        while (iterator.hasNext()) {
            val effect = iterator.next()
            while (tick >= effect.nextFrameTick && effect.frame < effect.frames.size) {
                effect.frame += 1
                val delay = effect.frames.getOrNull(effect.frame)?.second ?: 0
                effect.nextFrameTick += delay + effectRandom.nextInt(delay / 2 + 1)
            }
            if (effect.frame >= effect.frames.size) {
                actorManager.remove(effect.actor)
                iterator.remove()
            } else {
                effect.actor.spriteName = effect.frames[effect.frame].first
            }
        }
    }

    private val effectRandom = kotlin.random.Random(0)

    private fun spawnEffect(kind: EffectKind, x: Int, y: Int, tick: Long) {
        val frames = EFFECT_FRAMES[kind] ?: return
        val actor = SpriteActor(nextEffectId--, snapshot, spriteManager, sprites, layerIndex = EFFECT_LAYER).apply {
            spriteName = frames.first().first
            moveTo(x, y)
        }
        actorManager.add(actor)
        effects += Effect(actor, frames, frame = 0, nextFrameTick = tick + frames.first().second)
    }

    private fun <T : Actor> prune(actorsById: MutableMap<Long, T>, keepIds: Set<Long>) {
        if (actorsById.isEmpty()) return
        val staleIds = actorsById.keys.filterNot(keepIds::contains)
        if (staleIds.isEmpty()) return
        val staleActors = staleIds.mapNotNull(actorsById::remove)
        actorManager.remove(staleActors)
    }
}

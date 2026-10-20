package nl.tankarena.render

import androidx.collection.MutableLongSet
import androidx.compose.ui.geometry.Offset
import nl.tankarena.protocol.snapshot.AbombState
import nl.tankarena.protocol.snapshot.B52State
import nl.tankarena.protocol.snapshot.BonusState
import nl.tankarena.protocol.snapshot.ManState
import nl.tankarena.protocol.snapshot.DestroyerState
import nl.tankarena.protocol.snapshot.EffectKind
import nl.tankarena.protocol.snapshot.EnforcerState
import nl.tankarena.protocol.snapshot.ExplosionKind
import nl.tankarena.protocol.snapshot.FlagState
import nl.tankarena.protocol.snapshot.GameEvent
import nl.tankarena.protocol.snapshot.GoalState
import nl.tankarena.protocol.snapshot.LockMarker
import nl.tankarena.protocol.snapshot.LockMarkerState
import nl.tankarena.protocol.snapshot.LockState
import nl.tankarena.protocol.snapshot.MineState
import nl.tankarena.protocol.snapshot.FlamePartState
import nl.tankarena.protocol.snapshot.ProductState
import nl.tankarena.protocol.snapshot.ProjectileKind
import nl.tankarena.protocol.snapshot.ProjectileState
import nl.tankarena.protocol.snapshot.TankState
import nl.tankarena.protocol.snapshot.TrainState
import nl.tankarena.protocol.snapshot.TurretState
import nl.tankarena.protocol.snapshot.WarpState
import nl.tankarena.protocol.snapshot.WorldSnapshot
import nl.tankarena.protocol.snapshot.ZeppelinState
import nl.tankarena.render.actor.GoalActor
import nl.tankarena.render.actor.ProjectileActor
import nl.tankarena.render.actor.SpriteActor
import nl.tankarena.render.actor.TankActor
import nl.tankarena.render.actor.TiledSpriteActor
import nl.tankarena.content.LEGACY_TILE_SIZE
import nl.tankarena.render.actor.TurretActor
import org.jetbrains.compose.resources.DrawableResource

/** Above vehicles (2) and the top structure layer (3). */
private const val EFFECT_LAYER = 4
private const val VEHICLE_LAYER = 2
private const val GROUND_EFFECT_LAYER = 1
private const val HIGH_FLIGHT_HEIGHT = 4
private const val MORTAR_ARC = 15
/** Legacy make_zeppelin(): it flies at height 20. */
private const val ZEPPELIN_HEIGHT = 20

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
    EffectKind.OIL_SPLASH to listOf("OLIE1" to 25, "OLIE2" to 25, "OLIE3" to 25),
    EffectKind.SMOKE_SCREEN to listOf("SMOKES1" to 50, "SMOKES2" to 50, "SMOKES3" to 50, "SMOKES4" to 50) +
        List(19) { (if (it % 2 == 0) "SMOKES5" else "SMOKES6") to 200 } +
        listOf("SMOKES4" to 50, "SMOKES3" to 50, "SMOKES2" to 50, "SMOKES1" to 50),
    EffectKind.BLOOD to listOf("BLOOD1" to 50, "BLOOD2" to 50, "BLOOD3" to 50),
    EffectKind.EXHAUST to listOf("EXHAUST2" to 25),
    EffectKind.ROCKET_EXHAUST to listOf("EXHAUST1" to 100, "EXHAUST2" to 25),
    EffectKind.TELEPORT_SPARK1 to (1..6).map { "TEL$it" to 30 },
    EffectKind.TELEPORT_SPARK2 to (2..6).map { "TEL$it" to 30 },
    EffectKind.TELEPORT_SPARK3 to (3..6).map { "TEL$it" to 30 },
    EffectKind.DUST to listOf("DUST1" to 25, "DUST2" to 25, "DUST3" to 25),
    EffectKind.PARTS_SMOKE to listOf("PARTS1" to 13, "PARTS2" to 13, "PARTS3" to 13),
)
private const val LOCK_MARKER_ID = Long.MIN_VALUE

internal class ClientScene(
    private val snapshot: RuntimeSnapshot,
    private val scene: RenderScene,
    private val pictures: PictureCache,
    private val sprites: LegacySpriteCatalog,
) {
    private val tanks = LinkedHashMap<Long, TankActor>()
    private val turrets = LinkedHashMap<Long, TurretActor>()
    private val goals = LinkedHashMap<Long, GoalActor>()
    private val projectiles = LinkedHashMap<Long, ProjectileActor>()
    private val objects = LinkedHashMap<Long, SpriteActor>()
    private val tiled = LinkedHashMap<Long, TiledSpriteActor>()

    private class Effect(val actor: SpriteActor, val frames: List<Pair<String, Int>>, var frame: Int, var nextFrameTick: Long)

    private val effects = mutableListOf<Effect>()
    private var nextEffectId = -1L

    private var lockMarkerActor: SpriteActor? = null

    // The actors seen in this sync, kept between syncs (and without boxing) so a tick makes no garbage.
    private val tankIds = MutableLongSet()
    private val turretIds = MutableLongSet()
    private val goalIds = MutableLongSet()
    private val projectileIds = MutableLongSet()
    private val objectIds = MutableLongSet()
    private val tiledIds = MutableLongSet()

    /** [viewerActorId]: the vehicle this viewport's player drives, who still sees it while cloaked. */
    fun sync(
        world: WorldSnapshot,
        events: List<GameEvent> = world.events,
        lockMarker: LockMarker? = null,
        viewerActorId: Long? = null,
    ) {
        tankIds.clear()
        turretIds.clear()
        goalIds.clear()
        projectileIds.clear()
        objectIds.clear()
        tiledIds.clear()

        for (state in world.actors) {
            when (state) {
                is TankState -> {
                    tankIds += state.actorId
                    // Legacy write_objects(): from height 4 up a vehicle is drawn above the top layer.
                    val layer = if (state.height >= HIGH_FLIGHT_HEIGHT) EFFECT_LAYER else VEHICLE_LAYER
                    tanks[state.actorId]?.takeIf { it.layerIndex != layer }?.let {
                        scene.remove(it)
                        tanks.remove(state.actorId)
                    }
                    tanks.getOrPut(state.actorId) {
                        TankActor(
                            id = state.actorId,
                            snapshot = snapshot,
                            pictures = pictures,
                            sprites = sprites,
                            layerIndex = layer,
                        ).also { scene.add(it) }
                    }.sync(state, ownVehicle = state.actorId == viewerActorId)
                }
                is TurretState -> {
                    turretIds += state.actorId
                    turrets.getOrPut(state.actorId) {
                        TurretActor(
                            id = state.actorId,
                            snapshot = snapshot,
                            pictures = pictures,
                            sprites = sprites,
                        ).also { scene.add(it) }
                    }.sync(state)
                }
                is GoalState -> {
                    if (state.captured) continue
                    goalIds += state.actorId
                    goals.getOrPut(state.actorId) {
                        GoalActor(
                            id = state.actorId,
                            snapshot = snapshot,
                        ).also { scene.add(it) }
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
                            ).also { scene.add(it) }
                        }.sync(state)
                    }
                    ProjectileKind.ROCKET -> syncObject(objectIds, state.actorId, state.x, state.y, null)
                        .showDirectional(rocketDirection(state)) { "ROCK1-${it + 1}" }
                    // Legacy write_bullets(): a small rocket points where it flies.
                    ProjectileKind.SROCK -> syncObject(objectIds, state.actorId, state.x, state.y, null)
                        .showDirectional(state.direction) { "ROCK1-${it + 1}" }
                    // Legacy write_bullets(): flames and mortar shells change picture as they fly
                    // (`b->dist`), and a shell's shadow falls `h` away along its arc.
                    ProjectileKind.MORTAR -> syncObject(objectIds, state.actorId, state.x, state.y, MORT_NAMES[flightFrame(state.rangeLeft)])
                        .apply { shadowOffset = mortarArcHeight(state.rangeLeft) }
                    // A plane's bomb shrinks as it falls: MORT1 + dist / 50, its shadow closing in.
                    ProjectileKind.PLANEBOMB -> syncObject(
                        objectIds,
                        state.actorId,
                        state.x,
                        state.y,
                        "MORT${(1 + state.rangeLeft / 50).coerceIn(1, 5)}",
                    ).apply { shadowOffset = state.rangeLeft * state.dropHeight / 120 }
                    ProjectileKind.FLAME -> syncObject(objectIds, state.actorId, state.x, state.y, FLAME_NAMES[flightFrame(state.rangeLeft)])
                }
                // Legacy write_bonus(): the bonus type's icon.
                is BonusState -> syncObject(objectIds, state.actorId, state.x, state.y, ICON_NAMES[state.bonusType.mod(15) + 1])
                // Legacy write_man(): MAN-1..5 or MAN-6..10 while walking, turned to his direction.
                is ManState -> syncObject(objectIds, state.actorId, state.x, state.y, null)
                    .showDirectional(state.direction) { "MAN-${state.frame + it + 1}" }
                // Legacy write_abomb(): ABOMB1..4 while it counts down, nothing in water or once it goes off.
                is AbombState -> if (!state.exploding && !state.hidden) {
                    syncObject(objectIds, state.actorId, state.x, state.y, ABOMB_NAMES[(4 - state.countdown).coerceIn(1, 4)])
                }
                // Legacy write_parts(): a burning piece of a fuel dump, FLAME1..5 as it flies on.
                is FlamePartState -> syncObject(objectIds, state.actorId, state.x, state.y, FLAME_NAMES[state.frame + 1])
                // Legacy write_mine(): only heavy mines show, and not on water.
                is MineState -> if (state.heavy && !state.hidden) syncObject(objectIds, state.actorId, state.x, state.y, "RMINE")
                // Legacy write_flag(): a carried flag is not drawn (its carrier shows a flag icon).
                is FlagState -> if (!state.isCarried) {
                    syncObject(objectIds, state.actorId, state.x, state.y, VLAG_NAMES[state.flagType.mod(4) + 1])
                }
                is ProductState -> if (!state.isCollected) {
                    syncObject(objectIds, state.actorId, state.x, state.y, productSpriteName(state.productType))
                }
                is WarpState -> syncObject(objectIds, state.actorId, state.x, state.y, "WARP1")
                // Legacy write_train(): WAGO<picture>-1..5, turned to the car's direction.
                is TrainState -> if (state.alive) {
                    syncObject(objectIds, state.actorId, state.x, state.y, null)
                        .showDirectional(state.direction) { "WAGO${state.picture}-${it + 1}" }
                }
                // Legacy write_zeppelin(): 8×4 pictures from 148 px left of and 82 px above its centre.
                is ZeppelinState -> if (state.alive) {
                    syncTiled(tiledIds, state.actorId, state.x, state.y, EFFECT_LAYER, extent = 10, shadow = ZEPPELIN_HEIGHT) {
                        (0 until 32).map { l ->
                            val name = zeppelinPicture(l, state.frame)
                            TiledSpriteActor.Tile(name, -148 + (l % 8) * LEGACY_TILE_SIZE, -82 + (l / 8) * LEGACY_TILE_SIZE)
                        }
                    }
                }
                is B52State -> if (state.alive && !(state.wreck && state.onWater)) {
                    val layer = if (state.height >= HIGH_FLIGHT_HEIGHT) EFFECT_LAYER else VEHICLE_LAYER
                    syncTiled(tiledIds, state.actorId, state.x, state.y, layer, extent = 4, shadow = if (state.wreck) 0 else state.height) {
                        b52Tiles(state)
                    }
                }
                // Locks, destroyers and enforcers are invisible markers.
                is LockState, is DestroyerState, is EnforcerState -> Unit
            }
        }

        prune(tanks, tankIds)
        prune(turrets, turretIds)
        prune(goals, goalIds)
        prune(projectiles, projectileIds)
        prune(objects, objectIds)
        prune(tiled, tiledIds)
        if (rocketPositions.isNotEmpty()) rocketPositions.keys.retainAll { it in objectIds }
        if (rocketDirections.isNotEmpty()) rocketDirections.keys.retainAll { it in objectIds }
        syncEffects(world.tick, events)
        syncLockMarker(lockMarker)
    }

    /** The rocket lock-on marker of this viewport's player (legacy `MARKER + l`). */
    private fun syncLockMarker(marker: LockMarker?) {
        if (marker == null) {
            lockMarkerActor?.let(scene::remove)
            lockMarkerActor = null
            return
        }
        val actor = lockMarkerActor
            ?: SpriteActor(LOCK_MARKER_ID, snapshot, pictures, sprites, layerIndex = EFFECT_LAYER)
                .also { scene.add(it); lockMarkerActor = it }
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
        tiled.values.forEach { it.collectSpriteResources(target) }
        for (frames in EFFECT_FRAMES.values) {
            frames.forEach { (name, _) -> sprites.findResource(name)?.let(target::add) }
        }
        for (index in 1..5) {
            sprites.findResource(FLAME_NAMES[index])?.let(target::add)
            sprites.findResource(MORT_NAMES[index])?.let(target::add)
        }
        sprites.findResource("ARMOR")?.let(target::add)
        for (name in MARKER_NAMES) {
            sprites.findResource(name)?.let(target::add)
        }
    }

    private fun syncObject(ids: MutableLongSet, actorId: Long, x: Int, y: Int, sprite: String?): SpriteActor {
        ids += actorId
        return objects.getOrPut(actorId) {
            SpriteActor(actorId, snapshot, pictures, sprites).also { scene.add(it) }
        }.apply {
            spriteName = sprite
            flipHorizontal = false
            flipVertical = false
            shadowOffset = 0
            moveTo(x, y)
        }
    }

    private fun syncTiled(
        ids: MutableLongSet,
        actorId: Long,
        x: Int,
        y: Int,
        layer: Int,
        extent: Int,
        shadow: Int,
        tiles: () -> List<TiledSpriteActor.Tile>,
    ) {
        ids += actorId
        tiled[actorId]?.takeIf { it.layerIndex != layer }?.let {
            scene.remove(it)
            tiled.remove(actorId)
        }
        tiled.getOrPut(actorId) {
            TiledSpriteActor(actorId, snapshot, pictures, sprites, layer, extent).also { scene.add(it) }
        }.apply {
            this.tiles = tiles()
            shadowOffset = shadow
            moveTo(x, y)
        }
    }

    /** Legacy `ZEP%c%c` / `ZEP2%c%c` with the index spelled in two letters. */
    private fun zeppelinPicture(index: Int, frame: Int): String =
        (if (frame == 0) "ZEP" else "ZEP2") + ('A' + index / 26) + ('A' + index % 26)

    /**
     * Legacy write_b52() / write_b52_wreck(): 2×2 pictures `B52<dir><tile>` (wreck `_52<dir><tile>`)
     * from 32 px left of and above the centre, with the open bomb bay `B52B<dir>-<frame>` over the
     * two tiles at the back while a stick of bombs is coming.
     */
    private fun b52Tiles(state: B52State): List<TiledSpriteActor.Tile> {
        val d = state.heading.mod(4)
        val tiles = mutableListOf<TiledSpriteActor.Tile>()
        for (l in 0 until 4) {
            val j = l % 2
            val k = l / 2
            val dx = -32 + j * LEGACY_TILE_SIZE
            val dy = -32 + k * LEGACY_TILE_SIZE
            if (state.wreck) {
                tiles += TiledSpriteActor.Tile("_52${'A' + d}${'A' + l}", dx, dy, castsShadow = false)
                continue
            }
            val bayTile = (d == 0 && k != 0) || (d == 1 && j == 0) || (d == 2 && k == 0) || (d == 3 && j != 0)
            if (bayTile && state.bombBay != 4) tiles += TiledSpriteActor.Tile("B52B${d + 1}-${state.bombBay + 1}", dx, dy, castsShadow = false)
            tiles += TiledSpriteActor.Tile("B52${'A' + d}${'A' + l}", dx, dy)
        }
        return tiles
    }

    /** Legacy write_bullets(): `h = -15·dist²/3600 + 15·dist/30`, the height of a shell on its arc. */
    private fun mortarArcHeight(rangeLeft: Int): Int = -MORTAR_ARC * rangeLeft * rangeLeft / 3600 + MORTAR_ARC * rangeLeft / 30

    /** Legacy `FLAME + 4 - dist / 25` (and the same for MORTAR): frame 1 at launch, 5 when nearly spent. */
    private fun flightFrame(rangeLeft: Int): Int = (5 - rangeLeft / 25).coerceIn(1, 5)

    /** Rockets steer, so their facing is derived from movement between snapshots. */
    private fun rocketDirection(state: ProjectileState): Int {
        rocketPositions[state.actorId]?.let { (px, py) ->
            val dx = state.x - px
            val dy = state.y - py
            if (dx != 0 || dy != 0) {
                rocketDirections[state.actorId] = nl.tankarena.core.LegacyDirections.fromAngle(dx, dy)
            }
        }
        rocketPositions[state.actorId] = state.x to state.y
        return rocketDirections[state.actorId] ?: 0
    }

    private val rocketPositions = HashMap<Long, Pair<Int, Int>>()
    private val rocketDirections = HashMap<Long, Int>()


    /** Legacy create_effect()/control_effects(): server effect events play as picture runs. */
    private fun syncEffects(tick: Long, events: List<GameEvent>) {
        for (event in events) {
            when (event) {
                is GameEvent.Effect -> spawnEffect(event.kind, event.x, event.y, tick)
                is GameEvent.Debris -> for (piece in DebrisPiece.throwFrom(event.kind, event.x, event.y, tick, effectRandom)) {
                    debris += if (event.kind == nl.tankarena.protocol.snapshot.DebrisKind.PARTS) {
                        val sprite = SpriteActor(nextEffectId--, snapshot, pictures, sprites, layerIndex = EFFECT_LAYER)
                        scene.add(sprite)
                        DebrisVisual(piece, null, sprite)
                    } else {
                        val pixel = PixelDrawable(Offset(piece.x, piece.y))
                        scene.add(pixel)
                        DebrisVisual(piece, pixel, null)
                    }
                }
                is GameEvent.Explosion -> spawnEffect(
                    if (event.kind == ExplosionKind.ROCKET) EffectKind.LEXPLOSION else EffectKind.EXPLOSION,
                    event.x,
                    event.y,
                    tick,
                )
                else -> Unit
            }
        }
        stepDebris(tick)
        val iterator = effects.iterator()
        while (iterator.hasNext()) {
            val effect = iterator.next()
            while (tick >= effect.nextFrameTick && effect.frame < effect.frames.size) {
                effect.frame += 1
                val delay = effect.frames.getOrNull(effect.frame)?.second ?: 0
                effect.nextFrameTick += delay + effectRandom.nextInt(delay / 2 + 1)
            }
            if (effect.frame >= effect.frames.size) {
                scene.remove(effect.actor)
                iterator.remove()
            } else {
                effect.actor.spriteName = effect.frames[effect.frame].first
            }
        }
    }

    private val effectRandom = kotlin.random.Random(0)

    /** Legacy `parts` that only show, flying on the client. */
    private val debris = mutableListOf<DebrisVisual>()
    private var lastDebrisTick = Long.MIN_VALUE

    /** Legacy control_parts(): pieces fly on once per tick and are gone after their distance. */
    private fun stepDebris(tick: Long) {
        if (tick == lastDebrisTick) return
        lastDebrisTick = tick
        val iterator = debris.iterator()
        while (iterator.hasNext()) {
            val visual = iterator.next()
            val piece = visual.piece
            if (!piece.advance()) {
                visual.pixel?.let(scene::remove)
                visual.sprite?.let(scene::remove)
                iterator.remove()
                continue
            }
            visual.pixel?.body?.position = Offset(piece.x, piece.y)
            visual.sprite?.let { sprite ->
                sprite.spriteName = "PART${piece.partLetter}${piece.frame + 1}"
                sprite.moveTo(piece.x.toInt(), piece.y.toInt())
                // Legacy control_parts(): a piece of wall smokes as it flies.
                if (tick >= piece.smokeTick) {
                    spawnEffect(EffectKind.PARTS_SMOKE, piece.x.toInt(), piece.y.toInt(), tick)
                    piece.smokeTick = tick + 20 + effectRandom.nextInt(20)
                }
            }
        }
    }

    private fun spawnEffect(kind: EffectKind, x: Int, y: Int, tick: Long) {
        val frames = EFFECT_FRAMES[kind] ?: return
        // Legacy create_effect(..., 0, ...): dust and oil splashes lie on the ground, under the vehicles.
        val layer = if (kind == EffectKind.DUST || kind == EffectKind.OIL_SPLASH) GROUND_EFFECT_LAYER else EFFECT_LAYER
        val actor = SpriteActor(nextEffectId--, snapshot, pictures, sprites, layerIndex = layer).apply {
            spriteName = frames.first().first
            moveTo(x, y)
        }
        scene.add(actor)
        effects += Effect(actor, frames, frame = 0, nextFrameTick = tick + frames.first().second)
    }

    private fun <T : SceneDrawable> prune(actorsById: MutableMap<Long, T>, keepIds: MutableLongSet) {
        // Nearly always nothing is gone: only then build the lists.
        if (actorsById.size == keepIds.size) return
        val staleIds = actorsById.keys.filterNot(keepIds::contains)
        if (staleIds.isEmpty()) return
        val staleActors = staleIds.mapNotNull(actorsById::remove)
        scene.remove(staleActors)
    }
}

/** Legacy `intern[PRODUCTS + type]`: PRODUCT1..9, PRODUC10..15. */
internal fun productSpriteName(productType: Int): String = PRODUCT_NAMES[productType.mod(15)]

/**
 * Picture names `<prefix>1`, `<prefix>2`, ..., made once: they are asked for per object per tick.
 * Beyond the table a name is built as before.
 */
private class NumberedNames(private val prefix: String, count: Int) {
    private val names = Array(count) { "$prefix${it + 1}" }

    operator fun get(number: Int): String = names.getOrNull(number - 1) ?: "$prefix$number"
}

private val MORT_NAMES = NumberedNames("MORT", 5)
private val FLAME_NAMES = NumberedNames("FLAME", 10)
private val ICON_NAMES = NumberedNames("ICON", 15)
private val ABOMB_NAMES = NumberedNames("ABOMB", 4)
private val VLAG_NAMES = NumberedNames("VLAG", 4)
private val MARKER_NAMES = listOf("MARKER", "MARKER2", "MARKER3", "MARKER4")

private val PRODUCT_NAMES = Array(15) { val index = it + 1; if (index < 10) "PRODUCT$index" else "PRODUC$index" }

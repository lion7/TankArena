package com.tankarena.render.kubriko

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.drawscope.Stroke
import com.pandulapeter.kubriko.Kubriko
import com.pandulapeter.kubriko.KubrikoViewport
import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.actor.traits.Dynamic
import com.pandulapeter.kubriko.actor.traits.Visible
import com.pandulapeter.kubriko.collision.CollisionManager
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.manager.ActorManager
import com.pandulapeter.kubriko.manager.Manager
import com.pandulapeter.kubriko.manager.MetadataManager
import com.pandulapeter.kubriko.manager.StateManager
import com.pandulapeter.kubriko.manager.ViewportManager
import com.pandulapeter.kubriko.sprites.SpriteManager
import com.pandulapeter.kubriko.types.FrameRate
import com.pandulapeter.kubriko.types.SceneOffset
import com.pandulapeter.kubriko.types.SceneSize
import com.tankarena.content.CanonicalMapDefinition
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.LegacyPictureVariant
import com.tankarena.content.LegacySpriteResources
import com.tankarena.content.TankArenaWorld
import com.tankarena.sim.ProjectileState
import com.tankarena.sim.TankState
import com.tankarena.sim.TurretState
import com.tankarena.sim.WorldState
import org.jetbrains.compose.resources.DrawableResource
import kotlin.math.max
import kotlin.math.min

private const val TILE_SIZE = LEGACY_TILE_SIZE.toFloat()

// The original DOS game reserved a 640x480 framebuffer with the bottom 80px
// dedicated to the HUD strip. The actual playfield is therefore 640x400. We
// match that aspect ratio so legacy maps render at the same on-screen tile
// size as the original.
private const val LEGACY_PLAYFIELD_WIDTH = 640f
private const val LEGACY_PLAYFIELD_HEIGHT = 400f
/** Aspect ratio of the legacy 640x400 playfield (the 640x480 framebuffer minus the 80px HUD). */
const val LEGACY_PLAYFIELD_ASPECT_RATIO: Float =
    LEGACY_PLAYFIELD_WIDTH / LEGACY_PLAYFIELD_HEIGHT

@Composable
fun TankArenaViewport(
    map: CanonicalMapDefinition?,
    worldState: WorldState,
    modifier: Modifier = Modifier,
    debugCollisionOverlay: Boolean = false,
) {
    val runtime = remember(map?.metadata?.name) {
        TankArenaKubrikoRuntime(map = map, initialState = worldState)
    }

    DisposableEffect(runtime) {
        onDispose(runtime::dispose)
    }

    LaunchedEffect(runtime, map, worldState, debugCollisionOverlay) {
        runtime.sync(map = map, state = worldState)
        runtime.setDebugCollisionOverlayEnabled(debugCollisionOverlay)
    }

    KubrikoViewport(
        modifier = modifier,
        kubriko = runtime.kubriko,
        WindowInsets(0, 0, 0, 0),
    )
}

private class TankArenaKubrikoRuntime(
    map: CanonicalMapDefinition?,
    initialState: WorldState,
) {
    private val sprites = LegacySpriteCatalog.shared
    private val snapshot = RuntimeSnapshot(map = map, worldState = initialState)
    private val spriteManager = SpriteManager.newInstance(
        isLoggingEnabled = false,
        instanceNameForLogging = "tank-arena-sprites",
    )
    private val terrainActor = TerrainActor(snapshot, spriteManager, sprites)
    private val entityActor = EntityActor(snapshot, spriteManager, sprites)
    private val debugOverlayActor = DebugCollisionOverlayActor(snapshot)
    private val actorManager = ActorManager.newInstance(
        initialActors = listOf(terrainActor, entityActor, debugOverlayActor),
        shouldUpdateActorsWhileNotRunning = false,
        shouldPutFarAwayActorsToSleep = false,
        invisibleActorMinimumRefreshTimeInMillis = 16,
        isLoggingEnabled = false,
        instanceNameForLogging = "tank-arena-actors",
    )
    // Non-authoritative collision manager: registered here so the client engine
    // mirrors the server's manager wiring, but it is **not** the source of
    // truth for gameplay. The simulation runs CollisionDispatch on the
    // server-authoritative SimulationHost. This instance only exists so that a
    // future visual proxy (e.g. spawn-flash or shield bubble overlap effects)
    // can hook into the client's tick loop the standard Kubriko way.
    private val collisionManager = CollisionManager.newInstance(
        isLoggingEnabled = false,
        instanceNameForLogging = "tank-arena-collision-debug",
    )
    private val viewportManager = ViewportManager.newInstance(
        aspectRatioMode = ViewportManager.AspectRatioMode.Fixed(
            ratio = LEGACY_PLAYFIELD_ASPECT_RATIO,
            width = LEGACY_PLAYFIELD_WIDTH.sceneUnit,
            alignment = Alignment.Center,
        ),
        initialScaleFactor = 1f,
        minimumScaleFactor = 0.25f,
        maximumScaleFactor = 6f,
        viewportEdgeBuffer = 0f.sceneUnit,
        isLoggingEnabled = false,
        instanceNameForLogging = "tank-arena-viewport",
        frameRate = FrameRate.NORMAL,
    )
    private val stateManager = StateManager.newInstance(
        shouldAutoStart = true,
        focusDebounce = 0,
        isLoggingEnabled = false,
        instanceNameForLogging = "tank-arena-state",
    )
    private val metadataManager = MetadataManager.newInstance(
        isLoggingEnabled = false,
        instanceNameForLogging = "tank-arena-metadata",
    )

    val kubriko: Kubriko = Kubriko.newInstance(
        manager = arrayOf<Manager>(
            metadataManager,
            stateManager,
            viewportManager,
            spriteManager,
            collisionManager,
            actorManager,
        ),
        isLoggingEnabled = false,
        instanceNameForLogging = "tank-arena-runtime",
    )

    fun sync(map: CanonicalMapDefinition?, state: WorldState) {
        snapshot.map = map
        snapshot.worldState = state
        terrainActor.syncBounds(state)
        entityActor.syncBounds(state)
        debugOverlayActor.syncBounds(state)
        preloadFrameSprites(map, state)
        positionCamera(map, state)
    }

    fun setDebugCollisionOverlayEnabled(enabled: Boolean) {
        debugOverlayActor.enabled = enabled
    }

    fun dispose() {
        kubriko.dispose()
    }

    private fun preloadFrameSprites(map: CanonicalMapDefinition?, state: WorldState) {
        val resources = LinkedHashSet<DrawableResource>()
        if (map != null) {
            val world = map.metadata.world
            val width = map.metadata.widthTiles
            val height = map.metadata.heightTiles
            for (index in 0 until width * height) {
                addTileSprite(resources, world, map.layers.base[index])
                addTileSprite(resources, world, map.layers.solid[index])
                addTileSprite(resources, world, map.layers.top[index])
            }
        }
        for (tank in state.tanks) {
            val direction = LegacySpriteResources.direction16FromFacing(tank.facing.x, tank.facing.y)
            val frame = LegacySpriteResources.tankBodyFrameForFacing(direction)
            sprites.findResource(LegacySpriteResources.nameForTankBody(tank.tankType, frame))
                ?.let(resources::add)
            val turretDirection = LegacySpriteResources.direction16FromFacing(
                tank.turretFacing.x,
                tank.turretFacing.y,
            )
            val turretFrame = LegacySpriteResources.turretFrameForDirection(turretDirection)
            sprites.findResource(LegacySpriteResources.nameForTurret(tank.tankType, turretFrame))
                ?.let(resources::add)
        }
        for (turret in state.turrets) {
            val frame = LegacySpriteResources.turretFrameForDirection(turret.direction)
            sprites.findResource(LegacySpriteResources.nameForTurret(turret.turretType, frame))
                ?.let(resources::add)
        }
        if (resources.isNotEmpty()) {
            spriteManager.preload(resources)
        }
    }

    private fun addTileSprite(
        target: MutableSet<DrawableResource>,
        world: TankArenaWorld,
        tileId: Int,
    ) {
        if (tileId < 0) return
        val name = LegacySpriteResources.nameForTile(world, tileId, LegacyPictureVariant.INTACT)
            ?: return
        sprites.findResource(name)?.let(target::add)
    }

    private fun positionCamera(map: CanonicalMapDefinition?, state: WorldState) {
        val bounds = state.bounds
        val mapFitsInsideViewport = bounds.widthPixels <= LEGACY_PLAYFIELD_WIDTH &&
            bounds.heightPixels <= LEGACY_PLAYFIELD_HEIGHT
        val target = when {
            mapFitsInsideViewport -> SceneOffset(
                x = (bounds.widthPixels / 2f).sceneUnit,
                y = (bounds.heightPixels / 2f).sceneUnit,
            )
            else -> {
                val tank = state.tanks.firstOrNull()
                if (tank != null) {
                    SceneOffset(
                        x = tank.position.x.toFloat().sceneUnit,
                        y = tank.position.y.toFloat().sceneUnit,
                    )
                } else {
                    SceneOffset(
                        x = (bounds.widthPixels / 2f).sceneUnit,
                        y = (bounds.heightPixels / 2f).sceneUnit,
                    )
                }
            }
        }
        viewportManager.setCameraPosition(target)
    }
}

private class RuntimeSnapshot(
    var map: CanonicalMapDefinition?,
    var worldState: WorldState,
)

private class TerrainActor(
    private val snapshot: RuntimeSnapshot,
    private val spriteManager: SpriteManager,
    private val sprites: LegacySpriteCatalog,
) : Visible {
    override var body: BoxBody = createWorldBody(snapshot.worldState)

    override val layerIndex: Int = 0

    fun syncBounds(state: WorldState) {
        body = createWorldBody(state)
    }

    override fun DrawScope.draw() {
        val world = snapshot.worldState
        val map = snapshot.map
        drawRect(
            color = Color(0xFF0B1020),
            topLeft = Offset.Zero,
            size = Size(world.bounds.widthPixels.toFloat(), world.bounds.heightPixels.toFloat()),
            style = Fill,
        )

        if (map == null) {
            drawGrid()
            return
        }

        val width = map.metadata.widthTiles
        val height = map.metadata.heightTiles
        val theme = map.metadata.world
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = x + y * width
                val px = x * TILE_SIZE
                val py = y * TILE_SIZE
                val base = map.layers.base[index]
                val solid = map.layers.solid[index]
                val top = map.layers.top[index]

                if (base >= 0) drawTile(theme, base, px, py)
                if (solid >= 0) drawTile(theme, solid, px, py)
                if (top >= 0) drawTile(theme, top, px, py)
            }
        }
    }

    private fun DrawScope.drawTile(world: TankArenaWorld, tileId: Int, x: Float, y: Float) {
        val name = LegacySpriteResources.nameForTile(world, tileId, LegacyPictureVariant.INTACT)
            ?: return
        val resource = sprites.findResource(name) ?: return
        val image = spriteManager.get(resource)
        if (image == null) {
            drawRect(
                color = fallbackTileColor(tileId),
                topLeft = Offset(x, y),
                size = Size(TILE_SIZE, TILE_SIZE),
            )
            return
        }
        drawSprite(image, x, y, TILE_SIZE.toInt(), TILE_SIZE.toInt())
    }

    private fun DrawScope.drawGrid() {
        val width = snapshot.worldState.bounds.widthPixels
        val height = snapshot.worldState.bounds.heightPixels
        var x = 0f
        while (x < width) {
            drawRect(
                color = Color(0xFF15213A),
                topLeft = Offset(x, 0f),
                size = Size(1f, height.toFloat()),
            )
            x += TILE_SIZE
        }
        var y = 0f
        while (y < height) {
            drawRect(
                color = Color(0xFF15213A),
                topLeft = Offset(0f, y),
                size = Size(width.toFloat(), 1f),
            )
            y += TILE_SIZE
        }
    }

    private fun fallbackTileColor(tileId: Int): Color {
        val seed = max(tileId, 0)
        val r = 40 + (seed * 53) % 140
        val g = 50 + (seed * 29) % 120
        val b = 60 + (seed * 11) % 100
        return Color(r, g, b)
    }
}

private class EntityActor(
    private val snapshot: RuntimeSnapshot,
    private val spriteManager: SpriteManager,
    private val sprites: LegacySpriteCatalog,
) : Visible, Dynamic {
    override var body: BoxBody = createWorldBody(snapshot.worldState)
    override val layerIndex: Int = 2

    fun syncBounds(state: WorldState) {
        body = createWorldBody(state)
    }

    override fun update(deltaTimeInMilliseconds: Int) = Unit

    override fun DrawScope.draw() {
        for (turret in snapshot.worldState.turrets) {
            drawTurret(turret)
        }
        for (tank in snapshot.worldState.tanks) {
            drawTank(tank)
        }
        for (projectile in snapshot.worldState.projectiles) {
            drawProjectile(projectile)
        }
    }

    private fun DrawScope.drawTank(tank: TankState) {
        if (!tank.isAlive) {
            val stage = (snapshot.worldState.tick.toInt().coerceAtLeast(0)) % 5
            val explosionResource = sprites.findResource(LegacySpriteResources.nameForExplosion(stage))
            val image = explosionResource?.let(spriteManager::get)
            if (image != null) {
                drawSpriteCentered(
                    image,
                    centerX = tank.position.x.toFloat(),
                    centerY = tank.position.y.toFloat(),
                    width = LEGACY_TILE_SIZE,
                    height = LEGACY_TILE_SIZE,
                )
            } else {
                drawRect(
                    color = Color(0xCCFF7733),
                    topLeft = Offset(tank.position.x.toFloat() - 14f, tank.position.y.toFloat() - 14f),
                    size = Size(28f, 28f),
                )
            }
            return
        }

        val direction = LegacySpriteResources.direction16FromFacing(tank.facing.x, tank.facing.y)
        val bodyFrame = LegacySpriteResources.tankBodyFrameForFacing(direction)
        val bodyName = LegacySpriteResources.nameForTankBody(tank.tankType, bodyFrame)
        val bodyResource = sprites.findResource(bodyName)
        val bodyImage = bodyResource?.let(spriteManager::get)
        if (bodyImage != null) {
            drawSpriteCentered(
                bodyImage,
                centerX = tank.position.x.toFloat(),
                centerY = tank.position.y.toFloat(),
                width = LEGACY_TILE_SIZE,
                height = LEGACY_TILE_SIZE,
            )
        } else {
            drawRect(
                color = Color(0xFF6DD3FF),
                topLeft = Offset(tank.position.x.toFloat() - 12f, tank.position.y.toFloat() - 12f),
                size = Size(24f, 24f),
            )
        }

        val turretDirection = LegacySpriteResources.direction16FromFacing(
            tank.turretFacing.x,
            tank.turretFacing.y,
        )
        val turretFrame = LegacySpriteResources.turretFrameForDirection(turretDirection)
        val turretName = LegacySpriteResources.nameForTurret(tank.tankType, turretFrame)
        val turretResource = sprites.findResource(turretName) ?: return
        val turretImage = spriteManager.get(turretResource) ?: return
        drawSpriteCentered(
            turretImage,
            centerX = tank.position.x.toFloat(),
            centerY = tank.position.y.toFloat(),
            width = LEGACY_TILE_SIZE,
            height = LEGACY_TILE_SIZE,
        )
    }

    private fun DrawScope.drawTurret(turret: TurretState) {
        val frame = LegacySpriteResources.turretFrameForDirection(turret.direction)
        val name = LegacySpriteResources.nameForTurret(turret.turretType, frame)
        val resource = sprites.findResource(name)
        val image = resource?.let(spriteManager::get)
        if (image == null) {
            drawRect(
                color = Color(0xFFFFB454),
                topLeft = Offset(turret.position.x.toFloat() - 10f, turret.position.y.toFloat() - 10f),
                size = Size(20f, 20f),
            )
            return
        }
        drawSpriteCentered(
            image,
            centerX = turret.position.x.toFloat(),
            centerY = turret.position.y.toFloat(),
            width = LEGACY_TILE_SIZE,
            height = LEGACY_TILE_SIZE,
        )
    }

    private fun DrawScope.drawProjectile(projectile: ProjectileState) {
        // The legacy engine renders main-cannon bullets as a 5-pixel cross in
        // black; no sprite lookup needed. Mirror that with a small black plus.
        val cx = projectile.position.x.toFloat()
        val cy = projectile.position.y.toFloat()
        drawRect(
            color = Color.Black,
            topLeft = Offset(cx - 0.5f, cy - 1.5f),
            size = Size(1f, 3f),
        )
        drawRect(
            color = Color.Black,
            topLeft = Offset(cx - 1.5f, cy - 0.5f),
            size = Size(3f, 1f),
        )
    }
}

private fun createWorldBody(state: WorldState): BoxBody = BoxBody(
    SceneOffset(0f.sceneUnit, 0f.sceneUnit),
    SceneSize(
        state.bounds.widthPixels.toFloat().sceneUnit,
        state.bounds.heightPixels.toFloat().sceneUnit,
    ),
)

/**
 * Optional debug overlay that draws collision-mask outlines for tanks,
 * turrets, and solid map tiles using the same conventions as the
 * server-authoritative [com.tankarena.sim.runtime.SimulationHost]. Drawn
 * straight from the [WorldState] snapshot — no gameplay decisions are made
 * here. Toggled via the `debugCollisionOverlay` flag on [TankArenaViewport].
 */
private class DebugCollisionOverlayActor(
    private val snapshot: RuntimeSnapshot,
) : Visible {

    override var body: BoxBody = createWorldBody(snapshot.worldState)
    override val layerIndex: Int = 100

    var enabled: Boolean = false

    fun syncBounds(state: WorldState) {
        body = createWorldBody(state)
    }

    override fun DrawScope.draw() {
        if (!enabled) return
        val world = snapshot.worldState
        val map = snapshot.map
        val tankSide = (LEGACY_TILE_SIZE - 4).toFloat()
        val stroke = Stroke(width = 1f)

        if (map != null) {
            val width = map.metadata.widthTiles
            val height = map.metadata.heightTiles
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val index = x + y * width
                    if (map.layers.solid[index] < 0) continue
                    drawRect(
                        color = Color(0xAAFF3355),
                        topLeft = Offset(x * TILE_SIZE, y * TILE_SIZE),
                        size = Size(TILE_SIZE, TILE_SIZE),
                        style = stroke,
                    )
                }
            }
        }

        for (tank in world.tanks) {
            val cx = tank.position.x.toFloat()
            val cy = tank.position.y.toFloat()
            drawRect(
                color = if (tank.isAlive) Color(0xAA66FFAA) else Color(0x66666666),
                topLeft = Offset(cx - tankSide / 2f, cy - tankSide / 2f),
                size = Size(tankSide, tankSide),
                style = stroke,
            )
        }

        for (turret in world.turrets) {
            val cx = turret.position.x.toFloat()
            val cy = turret.position.y.toFloat()
            drawRect(
                color = Color(0xAAFFAA33),
                topLeft = Offset(cx - tankSide / 2f, cy - tankSide / 2f),
                size = Size(tankSide, tankSide),
                style = stroke,
            )
        }

        for (projectile in world.projectiles) {
            val cx = projectile.position.x.toFloat()
            val cy = projectile.position.y.toFloat()
            drawCircle(
                color = Color(0xAA33CCFF),
                radius = 2f,
                center = Offset(cx, cy),
                style = stroke,
            )
        }
    }
}

private fun DrawScope.drawSprite(
    image: ImageBitmap,
    x: Float,
    y: Float,
    width: Int,
    height: Int,
) {
    val srcWidth = min(image.width, LEGACY_TILE_SIZE)
    val srcHeight = min(image.height, LEGACY_TILE_SIZE)
    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(srcWidth, srcHeight),
        dstOffset = IntOffset(x.toInt(), y.toInt()),
        dstSize = IntSize(width, height),
        filterQuality = FilterQuality.None,
    )
}

private fun DrawScope.drawSpriteCentered(
    image: ImageBitmap,
    centerX: Float,
    centerY: Float,
    width: Int,
    height: Int,
) {
    drawSprite(
        image = image,
        x = centerX - width / 2f,
        y = centerY - height / 2f,
        width = width,
        height = height,
    )
}

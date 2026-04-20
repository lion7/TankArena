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
import com.pandulapeter.kubriko.Kubriko
import com.pandulapeter.kubriko.KubrikoViewport
import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.actor.traits.Dynamic
import com.pandulapeter.kubriko.actor.traits.Visible
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
import com.tankarena.protocol.ActorType
import com.tankarena.protocol.ActorView
import com.tankarena.protocol.PlayerFrame
import com.tankarena.protocol.ProjectileView
import org.jetbrains.compose.resources.DrawableResource
import kotlin.math.max
import kotlin.math.min

private const val TILE_SIZE = LEGACY_TILE_SIZE.toFloat()
private const val LEGACY_PLAYFIELD_WIDTH = 640f
private const val LEGACY_PLAYFIELD_HEIGHT = 400f

const val LEGACY_PLAYFIELD_ASPECT_RATIO: Float =
    LEGACY_PLAYFIELD_WIDTH / LEGACY_PLAYFIELD_HEIGHT

@Composable
fun TankArenaViewport(
    map: CanonicalMapDefinition?,
    playerFrame: PlayerFrame,
    worldWidth: Int,
    worldHeight: Int,
    modifier: Modifier = Modifier,
) {
    val runtime = remember(map?.metadata?.name, playerFrame.playerId) {
        TankArenaKubrikoRuntime(
            map = map,
            initialFrame = playerFrame,
            worldWidth = worldWidth,
            worldHeight = worldHeight,
        )
    }

    DisposableEffect(runtime) {
        onDispose(runtime::dispose)
    }

    LaunchedEffect(runtime, map, playerFrame, worldWidth, worldHeight) {
        runtime.sync(map, playerFrame, worldWidth, worldHeight)
    }

    KubrikoViewport(
        modifier = modifier,
        kubriko = runtime.kubriko,
        WindowInsets(0, 0, 0, 0),
    )
}

private class TankArenaKubrikoRuntime(
    map: CanonicalMapDefinition?,
    initialFrame: PlayerFrame,
    worldWidth: Int,
    worldHeight: Int,
) {
    private val sprites = LegacySpriteCatalog.shared
    private val snapshot = RuntimeSnapshot(map, initialFrame, worldWidth, worldHeight)
    private val spriteManager = SpriteManager.newInstance(
        isLoggingEnabled = false,
        instanceNameForLogging = "tank-arena-sprites",
    )
    private val terrainActor = TerrainActor(snapshot, spriteManager, sprites)
    private val entityActor = EntityActor(snapshot, spriteManager, sprites)
    private val actorManager = ActorManager.newInstance(
        initialActors = listOf(terrainActor, entityActor),
        shouldUpdateActorsWhileNotRunning = false,
        shouldPutFarAwayActorsToSleep = false,
        invisibleActorMinimumRefreshTimeInMillis = 16,
        isLoggingEnabled = false,
        instanceNameForLogging = "tank-arena-actors",
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
            actorManager,
        ),
        isLoggingEnabled = false,
        instanceNameForLogging = "tank-arena-runtime",
    )

    fun sync(map: CanonicalMapDefinition?, playerFrame: PlayerFrame, worldWidth: Int, worldHeight: Int) {
        snapshot.map = map
        snapshot.playerFrame = playerFrame
        snapshot.worldWidth = worldWidth
        snapshot.worldHeight = worldHeight
        terrainActor.syncBounds(worldWidth, worldHeight)
        entityActor.syncBounds(worldWidth, worldHeight)
        preloadFrameSprites(map, playerFrame)
        viewportManager.setCameraPosition(
            SceneOffset(
                playerFrame.camera.centerX.toFloat().sceneUnit,
                playerFrame.camera.centerY.toFloat().sceneUnit,
            ),
        )
    }

    fun dispose() {
        kubriko.dispose()
    }

    private fun preloadFrameSprites(map: CanonicalMapDefinition?, playerFrame: PlayerFrame) {
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
        for (actor in playerFrame.replicatedActors) {
            when (actor.type) {
                ActorType.TANK -> {
                    val bodyFrame = LegacySpriteResources.tankBodyFrameForFacing(actor.bodyDirection)
                    sprites.findResource(LegacySpriteResources.nameForTankBody(actor.tankType, bodyFrame))
                        ?.let(resources::add)
                    val turretFrame = LegacySpriteResources.turretFrameForDirection(actor.turretDirection)
                    sprites.findResource(LegacySpriteResources.nameForTurret(actor.tankType, turretFrame))
                        ?.let(resources::add)
                }
                ActorType.TURRET -> {
                    val frame = LegacySpriteResources.turretFrameForDirection(actor.turretDirection)
                    sprites.findResource(LegacySpriteResources.nameForTurret(actor.tankType, frame))
                        ?.let(resources::add)
                }
                else -> Unit
            }
        }
        if (resources.isNotEmpty()) {
            spriteManager.preload(resources)
        }
    }

    private fun addTileSprite(target: MutableSet<DrawableResource>, world: TankArenaWorld, tileId: Int) {
        if (tileId < 0) return
        val name = LegacySpriteResources.nameForTile(world, tileId, LegacyPictureVariant.INTACT) ?: return
        sprites.findResource(name)?.let(target::add)
    }
}

private class RuntimeSnapshot(
    var map: CanonicalMapDefinition?,
    var playerFrame: PlayerFrame,
    var worldWidth: Int,
    var worldHeight: Int,
)

private class TerrainActor(
    private val snapshot: RuntimeSnapshot,
    private val spriteManager: SpriteManager,
    private val sprites: LegacySpriteCatalog,
) : Visible {
    override var body: BoxBody = createWorldBody(snapshot.worldWidth, snapshot.worldHeight)
    override val layerIndex: Int = 0

    fun syncBounds(worldWidth: Int, worldHeight: Int) {
        body = createWorldBody(worldWidth, worldHeight)
    }

    override fun DrawScope.draw() {
        val map = snapshot.map
        drawRect(
            color = Color(0xFF0B1020),
            topLeft = Offset.Zero,
            size = Size(snapshot.worldWidth.toFloat(), snapshot.worldHeight.toFloat()),
            style = Fill,
        )
        if (map == null) return
        val width = map.metadata.widthTiles
        val height = map.metadata.heightTiles
        val theme = map.metadata.world
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = x + y * width
                val px = x * TILE_SIZE
                val py = y * TILE_SIZE
                drawTile(theme, map.layers.base[index], px, py)
                drawTile(theme, map.layers.solid[index], px, py)
                drawTile(theme, map.layers.top[index], px, py)
            }
        }
    }

    private fun DrawScope.drawTile(world: TankArenaWorld, tileId: Int, x: Float, y: Float) {
        if (tileId < 0) return
        val name = LegacySpriteResources.nameForTile(world, tileId, LegacyPictureVariant.INTACT) ?: return
        val resource = sprites.findResource(name)
        val image = resource?.let(spriteManager::get)
        if (image == null) {
            val seed = max(tileId, 0)
            drawRect(
                color = Color(40 + (seed * 53) % 140, 50 + (seed * 29) % 120, 60 + (seed * 11) % 100),
                topLeft = Offset(x, y),
                size = Size(TILE_SIZE, TILE_SIZE),
            )
            return
        }
        drawSprite(image, x, y, TILE_SIZE.toInt(), TILE_SIZE.toInt())
    }
}

private class EntityActor(
    private val snapshot: RuntimeSnapshot,
    private val spriteManager: SpriteManager,
    private val sprites: LegacySpriteCatalog,
) : Visible, Dynamic {
    override var body: BoxBody = createWorldBody(snapshot.worldWidth, snapshot.worldHeight)
    override val layerIndex: Int = 2

    fun syncBounds(worldWidth: Int, worldHeight: Int) {
        body = createWorldBody(worldWidth, worldHeight)
    }

    override fun update(deltaTimeInMilliseconds: Int) = Unit

    override fun DrawScope.draw() {
        for (actor in snapshot.playerFrame.replicatedActors) {
            when (actor.type) {
                ActorType.TANK -> drawTank(actor)
                ActorType.TURRET -> drawTurret(actor)
                else -> Unit
            }
        }
        for (projectile in snapshot.playerFrame.replicatedProjectiles) {
            drawProjectile(projectile)
        }
    }

    private fun DrawScope.drawTank(actor: ActorView) {
        val bodyFrame = LegacySpriteResources.tankBodyFrameForFacing(actor.bodyDirection)
        val bodyResource = sprites.findResource(LegacySpriteResources.nameForTankBody(actor.tankType, bodyFrame))
        val bodyImage = bodyResource?.let(spriteManager::get)
        if (bodyImage != null) {
            drawSpriteCentered(bodyImage, actor.x.toFloat(), actor.y.toFloat(), LEGACY_TILE_SIZE, LEGACY_TILE_SIZE)
        } else {
            drawRect(
                color = if (actor.team == com.tankarena.protocol.Team.PLAYER) Color(0xFF6DD3FF) else Color(0xFFFF6D6D),
                topLeft = Offset(actor.x.toFloat() - 12f, actor.y.toFloat() - 12f),
                size = Size(24f, 24f),
            )
        }
        val turretFrame = LegacySpriteResources.turretFrameForDirection(actor.turretDirection)
        val turretResource = sprites.findResource(LegacySpriteResources.nameForTurret(actor.tankType, turretFrame))
        val turretImage = turretResource?.let(spriteManager::get) ?: return
        drawSpriteCentered(turretImage, actor.x.toFloat(), actor.y.toFloat(), LEGACY_TILE_SIZE, LEGACY_TILE_SIZE)
    }

    private fun DrawScope.drawTurret(actor: ActorView) {
        val frame = LegacySpriteResources.turretFrameForDirection(actor.turretDirection)
        val resource = sprites.findResource(LegacySpriteResources.nameForTurret(actor.tankType, frame))
        val image = resource?.let(spriteManager::get)
        if (image == null) {
            drawRect(
                color = Color(0xFFFFB454),
                topLeft = Offset(actor.x.toFloat() - 10f, actor.y.toFloat() - 10f),
                size = Size(20f, 20f),
            )
            return
        }
        drawSpriteCentered(image, actor.x.toFloat(), actor.y.toFloat(), LEGACY_TILE_SIZE, LEGACY_TILE_SIZE)
    }

    private fun DrawScope.drawProjectile(projectile: ProjectileView) {
        val cx = projectile.x.toFloat()
        val cy = projectile.y.toFloat()
        drawRect(Color.Black, Offset(cx - 0.5f, cy - 1.5f), Size(1f, 3f))
        drawRect(Color.Black, Offset(cx - 1.5f, cy - 0.5f), Size(3f, 1f))
    }
}

private fun createWorldBody(worldWidth: Int, worldHeight: Int): BoxBody = BoxBody(
    SceneOffset(0f.sceneUnit, 0f.sceneUnit),
    SceneSize(worldWidth.toFloat().sceneUnit, worldHeight.toFloat().sceneUnit),
)

private fun DrawScope.drawSprite(
    image: ImageBitmap,
    x: Float,
    y: Float,
    width: Int,
    height: Int,
) {
    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(min(image.width, LEGACY_TILE_SIZE), min(image.height, LEGACY_TILE_SIZE)),
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
    drawSprite(image, centerX - width / 2f, centerY - height / 2f, width, height)
}

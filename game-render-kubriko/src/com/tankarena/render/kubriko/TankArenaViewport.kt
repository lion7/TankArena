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
import com.pandulapeter.kubriko.types.FrameRate
import com.pandulapeter.kubriko.types.SceneOffset
import com.pandulapeter.kubriko.types.SceneSize
import com.tankarena.content.CanonicalMapDefinition
import com.tankarena.content.LegacyAssetRegistry
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.SpriteFrameRef
import com.tankarena.content.TileLayerKind
import com.tankarena.sim.ProjectileState
import com.tankarena.sim.TankState
import com.tankarena.sim.TurretState
import com.tankarena.sim.WorldState
import kotlin.math.max

private const val TILE_SIZE = LEGACY_TILE_SIZE.toFloat()
private const val VIEWPORT_WIDTH = 640f
private const val VIEWPORT_HEIGHT = 400f

@Composable
fun TankArenaViewport(
    map: CanonicalMapDefinition?,
    worldState: WorldState,
    modifier: Modifier = Modifier,
) {
    val runtime = remember(map?.metadata?.name) {
        TankArenaKubrikoRuntime(
            map = map,
            initialState = worldState,
            assets = DesktopBitmapCache(),
        )
    }

    DisposableEffect(runtime) {
        onDispose(runtime::dispose)
    }

    LaunchedEffect(runtime, map, worldState) {
        runtime.sync(map = map, state = worldState)
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
    private val assets: DesktopBitmapCache,
) {
    private val snapshot = RuntimeSnapshot(map = map, worldState = initialState)

    private val terrainActor = TerrainActor(snapshot, assets)
    private val entityActor = EntityActor(snapshot, assets)
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
            ratio = VIEWPORT_WIDTH / VIEWPORT_HEIGHT,
            width = VIEWPORT_WIDTH.sceneUnit,
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
        state.tanks.firstOrNull()?.let { tank ->
            viewportManager.setCameraPosition(
                SceneOffset(
                    x = (tank.position.x - VIEWPORT_WIDTH / 2f).sceneUnit,
                    y = (tank.position.y - VIEWPORT_HEIGHT / 2f).sceneUnit,
                )
            )
        }
    }

    fun dispose() {
        kubriko.dispose()
    }
}

private class RuntimeSnapshot(
    var map: CanonicalMapDefinition?,
    var worldState: WorldState,
)

private class TerrainActor(
    private val snapshot: RuntimeSnapshot,
    private val assets: DesktopBitmapCache,
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
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = x + y * width
                val px = x * TILE_SIZE
                val py = y * TILE_SIZE
                val base = map.layers.base[index]
                val solid = map.layers.solid[index]
                val top = map.layers.top[index]
                val world = map.metadata.world

                if (base >= 0) {
                    drawTile(LegacyAssetRegistry.resolveTileFrame(TileLayerKind.BASE, base, world), px, py, 1f)
                }
                if (solid >= 0) {
                    drawTile(LegacyAssetRegistry.resolveTileFrame(TileLayerKind.SOLID, solid, world), px, py, 0.94f)
                }
                if (top >= 0) {
                    drawTile(LegacyAssetRegistry.resolveTileFrame(TileLayerKind.TOP, top, world), px, py, 0.55f)
                }
            }
        }
    }

    private fun DrawScope.drawTile(
        frame: SpriteFrameRef,
        x: Float,
        y: Float,
        alpha: Float,
    ) {
        val image = assets.get(frame.sheet.assetId)
        if (image == null) {
            drawRect(
                color = tileColor(frame.column + frame.row * 10, layerBias = 0).copy(alpha = alpha),
                topLeft = Offset(x, y),
                size = Size(TILE_SIZE, TILE_SIZE),
            )
            return
        }
        drawImage(
            image = image,
            srcOffset = IntOffset(frame.x, frame.y),
            srcSize = IntSize(frame.width, frame.height),
            dstOffset = IntOffset(x.toInt(), y.toInt()),
            dstSize = IntSize(LEGACY_TILE_SIZE, LEGACY_TILE_SIZE),
            alpha = alpha,
        )
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

    private fun tileColor(tile: Int, layerBias: Int): Color {
        val seed = max(tile + layerBias * 17, 0)
        val r = 40 + (seed * 53) % 140
        val g = 50 + (seed * 29) % 120
        val b = 60 + (seed * 11) % 100
        return Color(r, g, b)
    }
}

private class EntityActor(
    private val snapshot: RuntimeSnapshot,
    private val assets: DesktopBitmapCache,
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
            drawProjectileExplosion(tank.position.x.toFloat(), tank.position.y.toFloat())
            return
        }
        val animationColumn = ((snapshot.worldState.tick / 6L) % 8L).toInt()
        val bodyFrame = LegacyAssetRegistry.resolveTankFrame(
            variant = tank.tankType,
            facingX = tank.facing.x,
            facingY = tank.facing.y,
            animationFrame = animationColumn,
        )
        val bodyImage = assets.get(bodyFrame.sheet.assetId)
        if (bodyImage == null) {
            drawRect(
                color = Color(0xFF6DD3FF),
                topLeft = Offset(tank.position.x.toFloat() - 12f, tank.position.y.toFloat() - 12f),
                size = Size(24f, 24f),
            )
        } else {
            drawFrame(
                image = bodyImage,
                frame = bodyFrame,
                centerX = tank.position.x.toFloat(),
                centerY = tank.position.y.toFloat(),
                width = LEGACY_TILE_SIZE,
                height = LEGACY_TILE_SIZE,
            )
        }

        // Overlay the turret on top of the body using the independent turret facing.
        val turretDirection = directionFromFacing(tank.turretFacing)
        val turretFrame = LegacyAssetRegistry.resolveTurretFrame(
            turretType = tank.tankType,
            direction = turretDirection,
        )
        val turretImage = assets.get(turretFrame.sheet.assetId) ?: return
        drawFrame(
            image = turretImage,
            frame = turretFrame,
            centerX = tank.position.x.toFloat(),
            centerY = tank.position.y.toFloat(),
            width = LEGACY_TILE_SIZE,
            height = LEGACY_TILE_SIZE,
        )
    }

    private fun DrawScope.drawProjectileExplosion(x: Float, y: Float) {
        drawRect(
            color = Color(0xCCFF7733),
            topLeft = Offset(x - 14f, y - 14f),
            size = Size(28f, 28f),
        )
    }

    private fun DrawScope.drawTurret(turret: TurretState) {
        val frame = LegacyAssetRegistry.resolveTurretFrame(
            turretType = turret.turretType,
            direction = turret.direction,
        )
        val image = assets.get(frame.sheet.assetId)
        if (image == null) {
            drawRect(
                color = Color(0xFFFFB454),
                topLeft = Offset(turret.position.x.toFloat() - 10f, turret.position.y.toFloat() - 10f),
                size = Size(20f, 20f),
            )
            return
        }
        drawFrame(
            image = image,
            frame = frame,
            centerX = turret.position.x.toFloat(),
            centerY = turret.position.y.toFloat(),
            width = LEGACY_TILE_SIZE,
            height = LEGACY_TILE_SIZE,
        )
    }

    private fun DrawScope.drawProjectile(projectile: ProjectileState) {
        val frame = LegacyAssetRegistry.resolveProjectileFrame()
        val image = assets.get(frame.sheet.assetId)
        if (image == null) {
            drawRect(
                color = Color(0xFFFF645A),
                topLeft = Offset(projectile.position.x.toFloat() - 3f, projectile.position.y.toFloat() - 3f),
                size = Size(6f, 6f),
            )
            return
        }
        drawFrame(
            image = image,
            frame = frame,
            centerX = projectile.position.x.toFloat(),
            centerY = projectile.position.y.toFloat(),
            width = 12,
            height = 12,
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

private data class FrameRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

private val SpriteFrameRef.x: Int get() = column * sheet.frameWidth
private val SpriteFrameRef.y: Int get() = row * sheet.frameHeight
private val SpriteFrameRef.width: Int get() = sheet.frameWidth
private val SpriteFrameRef.height: Int get() = sheet.frameHeight

private fun frameAt(sheet: com.tankarena.content.SpriteSheetDefinition, column: Int, row: Int): FrameRect {
    return FrameRect(
        x = column * sheet.frameWidth,
        y = row * sheet.frameHeight,
        width = sheet.frameWidth,
        height = sheet.frameHeight,
    )
}

private fun directionFromFacing(facing: com.tankarena.core.Int2): Int {
    val sx = facing.x.coerceIn(-1, 1)
    val sy = facing.y.coerceIn(-1, 1)
    return when {
        sx == 0 && sy < 0 -> 0
        sx > 0 && sy < 0 -> 2
        sx > 0 && sy == 0 -> 4
        sx > 0 && sy > 0 -> 6
        sx == 0 && sy > 0 -> 8
        sx < 0 && sy > 0 -> 10
        sx < 0 && sy == 0 -> 12
        sx < 0 && sy < 0 -> 14
        else -> 0
    }
}

private fun DrawScope.drawFrame(
    image: ImageBitmap,
    frame: SpriteFrameRef,
    centerX: Float,
    centerY: Float,
    width: Int,
    height: Int,
) {
    drawImage(
        image = image,
        srcOffset = IntOffset(frame.x, frame.y),
        srcSize = IntSize(frame.width, frame.height),
        dstOffset = IntOffset((centerX - width / 2f).toInt(), (centerY - height / 2f).toInt()),
        dstSize = IntSize(width, height),
    )
}

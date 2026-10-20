package com.tankarena.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import com.tankarena.content.MapSceneSidecar
import com.tankarena.protocol.snapshot.GameEvent
import com.tankarena.protocol.snapshot.PlayerView
import com.tankarena.protocol.snapshot.ServerFrame
import com.tankarena.protocol.snapshot.WorldSnapshot

@Composable
fun TankArenaViewport(
    sidecar: MapSceneSidecar?,
    serverFrame: ServerFrame,
    playerId: Int,
    worldWidth: Int,
    worldHeight: Int,
    modifier: Modifier = Modifier,
    events: ViewportEventQueue? = null,
    /** Visible scene size: legacy `buf_x` × `buf_y`, the map window on the 640×480 screen. */
    playfieldWidth: Int = LEGACY_PLAYFIELD_WIDTH.toInt(),
    playfieldHeight: Int = LEGACY_PLAYFIELD_HEIGHT.toInt(),
    onGeometryChanged: (ViewportGeometry) -> Unit = {},
) {
    val playerView = serverFrame.playerViews.firstOrNull { it.playerId == playerId }
        ?: serverFrame.playerViews.firstOrNull()
        ?: return

    val runtime = remember(sidecar?.metadata?.name, playerView.playerId, playfieldWidth, playfieldHeight) {
        ViewportRuntime(
            playfieldWidth = playfieldWidth,
            playfieldHeight = playfieldHeight,
            sidecar = sidecar,
            initialView = playerView,
            initialWorld = serverFrame.world,
            worldWidth = worldWidth,
            worldHeight = worldHeight,
        )
    }

    DisposableEffect(runtime) {
        onDispose(runtime::dispose)
    }

    LaunchedEffect(runtime, sidecar, serverFrame, worldWidth, worldHeight) {
        runtime.sync(sidecar, playerView, serverFrame.world, worldWidth, worldHeight, events?.drain() ?: serverFrame.world.events)
        onGeometryChanged(runtime.geometry)
    }

    // The window: the map scaled to fill the width at the playfield's aspect ratio, centred.
    Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
        Box(
            modifier = viewportEffects(modifier, sidecar, runtime)
                .align(Alignment.Center)
                .aspectRatio(playfieldWidth.toFloat() / playfieldHeight)
                .clipToBounds(),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                runtime.sceneVersion.intValue
                runtime.draw(this)
            }
        }
    }
}

/**
 * Legacy night missions: the view goes through the night palette, lit where lights fall. By day,
 * legacy palette_effects(): water, conveyor belts, teleporters, glow and lava cycle.
 */
private fun viewportEffects(modifier: Modifier, sidecar: MapSceneSidecar?, runtime: ViewportRuntime): Modifier =
    if (sidecar?.metadata?.night == true) {
        modifier.graphicsLayer {
            runtime.nightVersion.value
            renderEffect = runtime.nightFilter(size.width, size.height)?.asComposeRenderEffect()
        }
    } else {
        modifier.graphicsLayer {
            runtime.cyclingVersion.value
            renderEffect = runtime.cyclingFilter().asComposeRenderEffect()
        }
    }

/** One player window's scene, kept in step with the server frames it is given. */
private class ViewportRuntime(
    private val playfieldWidth: Int,
    private val playfieldHeight: Int,
    sidecar: MapSceneSidecar?,
    initialView: PlayerView,
    initialWorld: WorldSnapshot,
    worldWidth: Int,
    worldHeight: Int,
) {
    private val sprites = LegacySpriteCatalog.shared
    private val snapshot = RuntimeSnapshot(
        sidecar = sidecar,
        playerView = initialView,
        geometry = resolveViewportGeometry(initialView, worldWidth, worldHeight, playfieldWidth, playfieldHeight),
    ).also {
        it.viewWidth = playfieldWidth
        it.viewHeight = playfieldHeight
    }
    private val pictures = PictureCache.shared
    private val terrainActor = TerrainActor(snapshot, pictures, sprites)
    private val topLayerActor = TopLayerActor(snapshot, pictures, sprites)
    private val scene = RenderScene().apply {
        add(terrainActor)
        add(topLayerActor)
    }

    /** Bumped on every sync, so the window redraws. */
    val sceneVersion = mutableIntStateOf(0)

    private val nightLighting = NightLighting(sprites)
    /** Bumped whenever the night light mask is redrawn, so the layer picks up the new filter. */
    val nightVersion = mutableIntStateOf(0)

    private val paletteCycling = PaletteCycling()
    /** Bumped whenever a cycled palette range rotated. */
    val cyclingVersion = mutableIntStateOf(0)

    fun cyclingFilter(): org.jetbrains.skia.ImageFilter = paletteCycling.filter()

    fun nightFilter(width: Float, height: Float): org.jetbrains.skia.ImageFilter? =
        nightLighting.filter(width, height, playfieldWidth, playfieldHeight)

    private val clientScene = ClientScene(
        snapshot = snapshot,
        scene = scene,
        pictures = pictures,
        sprites = sprites,
    )

    init {
        sync(sidecar, initialView, initialWorld, worldWidth, worldHeight)
    }

    val geometry: ViewportGeometry
        get() = snapshot.geometry

    fun sync(
        sidecar: MapSceneSidecar?,
        playerView: PlayerView,
        world: WorldSnapshot,
        worldWidth: Int,
        worldHeight: Int,
        events: List<GameEvent> = world.events,
    ) {
        snapshot.sidecar = sidecar
        snapshot.playerView = playerView
        snapshot.geometry = resolveViewportGeometry(playerView, worldWidth, worldHeight, playfieldWidth, playfieldHeight)
        terrainActor.syncBounds()
        topLayerActor.syncBounds()
        if (world.changedTiles.size != snapshot.changedTiles.size) {
            snapshot.changedTiles = world.changedTiles.associateBy { it.index }
        }
        clientScene.sync(world, events, playerView.lockMarker, playerView.controlledActorId)
        preloadSceneSprites(sidecar)
        if (sidecar?.metadata?.night != true) {
            paletteCycling.advanceTo(world.tick)
            cyclingVersion.intValue = paletteCycling.version
        }
        if (sidecar?.metadata?.night == true) {
            val extra = LinkedHashSet<org.jetbrains.compose.resources.DrawableResource>()
            nightLighting.update(world, snapshot, playfieldWidth, playfieldHeight, extra)
            pictures.preload(extra)
            nightVersion.intValue += 1
        }
        sceneVersion.intValue += 1
    }

    fun draw(scope: androidx.compose.ui.graphics.drawscope.DrawScope) {
        val geometry = snapshot.geometry
        scene.draw(scope, geometry.cameraCenterX, geometry.cameraCenterY, playfieldWidth.toFloat())
    }

    fun dispose() = Unit

    private var preloadedTilesFor: MapSceneSidecar? = null

    private fun preloadSceneSprites(sidecar: MapSceneSidecar?) {
        val resources = LinkedHashSet<org.jetbrains.compose.resources.DrawableResource>()
        val tiles = sidecar?.tileLayers
        // Tiles never change during a match; only walk the layers once per map.
        if (sidecar != null && tiles != null && preloadedTilesFor !== sidecar) {
            preloadedTilesFor = sidecar
            val world = sidecar.metadata.world
            val width = sidecar.metadata.widthTiles
            val height = sidecar.metadata.heightTiles
            for (variant in 1..4) {
                sprites.findResource("BACK${world.ordinal * 4 + variant}")?.let(resources::add)
            }
            for (index in 0 until width * height) {
                addTileSprite(resources, world, tiles.base[index], sprites)
                addTileSprite(resources, world, tiles.solid[index], sprites)
                addTileSprite(resources, world, tiles.top[index], sprites)
            }
        }
        clientScene.collectSpriteResources(resources)
        if (resources.isNotEmpty()) {
            pictures.preload(resources)
        }
    }
}

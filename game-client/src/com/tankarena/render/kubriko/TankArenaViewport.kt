package com.tankarena.render.kubriko

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import com.pandulapeter.kubriko.manager.ActorManager
import com.pandulapeter.kubriko.Kubriko
import com.pandulapeter.kubriko.KubrikoViewport
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.manager.Manager
import com.pandulapeter.kubriko.manager.MetadataManager
import com.pandulapeter.kubriko.manager.StateManager
import com.pandulapeter.kubriko.manager.ViewportManager
import com.pandulapeter.kubriko.sprites.SpriteManager
import com.pandulapeter.kubriko.types.TargetFrameRate
import com.pandulapeter.kubriko.types.SceneOffset
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
    /** Visible scene width; 640 for a full view, 320 for each half of the split screen. */
    playfieldWidth: Int = LEGACY_PLAYFIELD_WIDTH.toInt(),
    onGeometryChanged: (ViewportGeometry) -> Unit = {},
) {
    val playerView = serverFrame.playerViews.firstOrNull { it.playerId == playerId }
        ?: serverFrame.playerViews.firstOrNull()
        ?: return

    val runtime = remember(sidecar?.metadata?.name, playerView.playerId, playfieldWidth) {
        TankArenaKubrikoRuntime(
            playfieldWidth = playfieldWidth,
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

    KubrikoViewport(
        // Legacy night missions: the view goes through the night palette, lit where lights fall.
        modifier = if (sidecar?.metadata?.night == true) {
            modifier.graphicsLayer {
                runtime.nightVersion.value
                renderEffect = runtime.nightFilter(size.width, size.height)?.asComposeRenderEffect()
            }
        } else {
            modifier
        },
        kubriko = runtime.kubriko,
        WindowInsets(0, 0, 0, 0),
    )
}

private class TankArenaKubrikoRuntime(
    private val playfieldWidth: Int,
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
        geometry = resolveViewportGeometry(initialView, worldWidth, worldHeight, playfieldWidth),
    )
    private val spriteManager = SpriteManager.newInstance(
        isLoggingEnabled = false,
        instanceNameForLogging = "tank-arena-sprites",
    )
    private val terrainActor = TerrainActor(snapshot, spriteManager, sprites)
    private val topLayerActor = TopLayerActor(snapshot, spriteManager, sprites)
    private val actorManager = ActorManager.newInstance(
        initialActors = listOf(terrainActor, topLayerActor),
        shouldUpdateActorsWhileNotRunning = false,
        shouldPutFarAwayActorsToSleep = false,
        invisibleActorMinimumRefreshTimeInMillis = 16,
        isLoggingEnabled = false,
        instanceNameForLogging = "tank-arena-actors",
    )
    private val viewportManager = ViewportManager.newInstance(
        aspectRatioMode = ViewportManager.AspectRatioMode.Fixed(
            ratio = playfieldWidth / LEGACY_PLAYFIELD_HEIGHT,
            width = playfieldWidth.toFloat().sceneUnit,
        ),
        initialScaleFactor = 1f,
        minimumScaleFactor = 0.25f,
        maximumScaleFactor = 6f,
        viewportEdgeBuffer = 0f.sceneUnit,
        isLoggingEnabled = false,
        instanceNameForLogging = "tank-arena-viewport",
        initialTargetFrameRate = TargetFrameRate.DisplayDefault,
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
    private val scenePreloadManager = ScenePreloadManager(
        isLoggingEnabled = false,
        instanceNameForLogging = "tank-arena-preload",
    )

    val kubriko: Kubriko = Kubriko.newInstance(
        manager = arrayOf<Manager>(
            metadataManager,
            stateManager,
            viewportManager,
            spriteManager,
            actorManager,
            scenePreloadManager,
        ),
        isLoggingEnabled = false,
        instanceNameForLogging = "tank-arena-runtime",
    )

    private val nightLighting = NightLighting(spriteManager, sprites)
    /** Bumped whenever the night light mask is redrawn, so the layer picks up the new filter. */
    val nightVersion = mutableIntStateOf(0)

    fun nightFilter(width: Float, height: Float): org.jetbrains.skia.ImageFilter? =
        nightLighting.filter(width, height, playfieldWidth, LEGACY_PLAYFIELD_HEIGHT.toInt())

    private val clientScene = ClientScene(
        snapshot = snapshot,
        actorManager = actorManager,
        spriteManager = spriteManager,
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
        snapshot.geometry = resolveViewportGeometry(playerView, worldWidth, worldHeight, playfieldWidth)
        terrainActor.syncBounds()
        topLayerActor.syncBounds()
        if (world.changedTiles.size != snapshot.changedTiles.size) {
            snapshot.changedTiles = world.changedTiles.associateBy { it.index }
        }
        clientScene.sync(world, events, playerView.lockMarker, playerView.controlledActorId)
        preloadSceneSprites(sidecar)
        if (sidecar?.metadata?.night == true) {
            val extra = LinkedHashSet<org.jetbrains.compose.resources.DrawableResource>()
            nightLighting.collectResources(extra)
            nightLighting.update(world, snapshot, playfieldWidth, LEGACY_PLAYFIELD_HEIGHT.toInt(), extra)
            scenePreloadManager.requestPreload(extra)
            nightVersion.intValue += 1
        }
        viewportManager.setCameraPosition(
            SceneOffset(
                snapshot.geometry.cameraCenterX.sceneUnit,
                snapshot.geometry.cameraCenterY.sceneUnit,
            ),
        )
    }

    fun dispose() {
        kubriko.dispose()
    }

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
            scenePreloadManager.requestPreload(resources)
        }
    }
}

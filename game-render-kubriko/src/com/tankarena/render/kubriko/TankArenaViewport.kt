package com.tankarena.render.kubriko

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.pandulapeter.kubriko.manager.ActorManager
import com.pandulapeter.kubriko.Kubriko
import com.pandulapeter.kubriko.KubrikoViewport
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.manager.Manager
import com.pandulapeter.kubriko.manager.MetadataManager
import com.pandulapeter.kubriko.manager.StateManager
import com.pandulapeter.kubriko.manager.ViewportManager
import com.pandulapeter.kubriko.sprites.SpriteManager
import com.pandulapeter.kubriko.types.FrameRate
import com.pandulapeter.kubriko.types.SceneOffset
import com.tankarena.content.CanonicalMapDefinition
import com.tankarena.protocol.snapshot.PlayerView
import com.tankarena.protocol.snapshot.ServerFrame
import com.tankarena.protocol.snapshot.WorldSnapshot

@Composable
fun TankArenaViewport(
    map: CanonicalMapDefinition?,
    serverFrame: ServerFrame,
    playerId: Int,
    worldWidth: Int,
    worldHeight: Int,
    modifier: Modifier = Modifier,
    onGeometryChanged: (ViewportGeometry) -> Unit = {},
) {
    val playerView = serverFrame.playerViews.firstOrNull { it.playerId == playerId }
        ?: serverFrame.playerViews.firstOrNull()
        ?: return

    val runtime = remember(map?.metadata?.name, playerView.playerId) {
        TankArenaKubrikoRuntime(
            map = map,
            initialView = playerView,
            initialWorld = serverFrame.world,
            worldWidth = worldWidth,
            worldHeight = worldHeight,
        )
    }

    DisposableEffect(runtime) {
        onDispose(runtime::dispose)
    }

    LaunchedEffect(runtime, map, serverFrame, worldWidth, worldHeight) {
        runtime.sync(map, playerView, serverFrame.world, worldWidth, worldHeight)
        onGeometryChanged(runtime.geometry)
    }

    KubrikoViewport(
        modifier = modifier,
        kubriko = runtime.kubriko,
        WindowInsets(0, 0, 0, 0),
    )
}

private class TankArenaKubrikoRuntime(
    map: CanonicalMapDefinition?,
    initialView: PlayerView,
    initialWorld: WorldSnapshot,
    worldWidth: Int,
    worldHeight: Int,
) {
    private val sprites = LegacySpriteCatalog.shared
    private val snapshot = RuntimeSnapshot(
        map = map,
        playerView = initialView,
        geometry = resolveViewportGeometry(initialView, worldWidth, worldHeight),
    )
    private val spriteManager = SpriteManager.newInstance(
        isLoggingEnabled = false,
        instanceNameForLogging = "tank-arena-sprites",
    )
    private val terrainActor = TerrainActor(snapshot, spriteManager, sprites)
    private val actorManager = ActorManager.newInstance(
        initialActors = listOf(terrainActor),
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

    private val replicatedActorScene = ReplicatedActorScene(
        snapshot = snapshot,
        actorManager = actorManager,
        spriteManager = spriteManager,
        sprites = sprites,
    )

    init {
        sync(map, initialView, initialWorld, worldWidth, worldHeight)
    }

    val geometry: ViewportGeometry
        get() = snapshot.geometry

    fun sync(
        map: CanonicalMapDefinition?,
        playerView: PlayerView,
        world: WorldSnapshot,
        worldWidth: Int,
        worldHeight: Int,
    ) {
        snapshot.map = map
        snapshot.playerView = playerView
        snapshot.geometry = resolveViewportGeometry(playerView, worldWidth, worldHeight)
        terrainActor.syncBounds()
        replicatedActorScene.sync(world)
        preloadSceneSprites(map)
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

    private fun preloadSceneSprites(map: CanonicalMapDefinition?) {
        val resources = LinkedHashSet<org.jetbrains.compose.resources.DrawableResource>()
        if (map != null) {
            val world = map.metadata.world
            val width = map.metadata.widthTiles
            val height = map.metadata.heightTiles
            for (index in 0 until width * height) {
                addTileSprite(resources, world, map.layers.base[index], sprites)
                addTileSprite(resources, world, map.layers.solid[index], sprites)
                addTileSprite(resources, world, map.layers.top[index], sprites)
            }
        }
        replicatedActorScene.collectSpriteResources(resources)
        if (resources.isNotEmpty()) {
            spriteManager.preload(resources)
        }
    }
}

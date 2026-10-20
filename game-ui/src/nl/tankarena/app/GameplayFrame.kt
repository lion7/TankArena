package nl.tankarena.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import nl.tankarena.content.LEGACY_TILE_SIZE
import nl.tankarena.protocol.snapshot.PlayerView
import nl.tankarena.protocol.snapshot.ServerFrame
import nl.tankarena.protocol.snapshot.TankState
import nl.tankarena.render.TankArenaViewport
import nl.tankarena.render.ViewportEventQueue
import nl.tankarena.render.ViewportGeometry
import nl.tankarena.render.legacyMapWindows
import nl.tankarena.ui.compose.GameMode
import nl.tankarena.ui.compose.LegacyScreen
import nl.tankarena.ui.compose.LegacyVideoMode
import nl.tankarena.ui.compose.MissionOutcome
import nl.tankarena.ui.compose.hud.HudOverlay
import nl.tankarena.ui.compose.menu.MissionEndBanner
import nl.tankarena.ui.compose.menu.MissionEntry
import nl.tankarena.ui.compose.menu.PanelPattern
import nl.tankarena.ui.compose.menu.YouWinBanner

private val showDebugOverlay: Boolean = System.getProperty("tankarena.debugOverlay") == "true"

/**
 * The game screen for one tick: the panel, one map window per player with its status display, the
 * mission banners and the screen-wide effects (fade, A-bomb flash, grey view for the loser). It only
 * draws what it is given, so tests can render it offscreen from recorded frames.
 */
@Composable
internal fun GameplayFrame(
    mission: MissionEntry,
    mode: GameMode,
    serverFrame: ServerFrame,
    playerIds: List<Int>,
    eventQueues: List<ViewportEventQueue>,
    /** The outcome once the mission has been won or lost (legacy `game.winner`). */
    missionEndBanner: MissionOutcome?,
    /** Legacy fade: 0 black .. 1 the full palette. */
    brightness: Float,
    /** Legacy `win_size` (0..5). */
    windowSize: Int = 0,
    /** Legacy `show_radar`. */
    showRadar: Boolean = true,
    /** A message the shell shows to player one (legacy F11: "GAMMA CORRECTION"). */
    playerOneMessage: HudMessage? = null,
    /** Extended: fill the window, and widen the screen on a wide window. */
    fill: Boolean = false,
    wide: Boolean = false,
) {
    val worldWidth = mission.sidecar.metadata.widthTiles * LEGACY_TILE_SIZE
    val worldHeight = mission.sidecar.metadata.heightTiles * LEGACY_TILE_SIZE
    val views = playerIds.mapNotNull { id -> serverFrame.playerViews.firstOrNull { it.playerId == id } }
        .mapIndexed { index, view ->
            if (index == 0 && playerOneMessage != null) {
                view.copy(hud = view.hud.copy(messageLine1 = playerOneMessage.line1, messageLine2 = playerOneMessage.line2, messageSteps = playerOneMessage.steps))
            } else {
                view
            }
        }
    val night = mission.sidecar.metadata.night

    LegacyScreen(mode = LegacyVideoMode.GAME, brightness = brightness, fill = fill, wide = wide) {
        val screen = this
        val windows = legacyMapWindows(views.size.coerceAtLeast(1), worldWidth, worldHeight, windowSize, screenWidth)
        // Legacy set_palette() with `nuke_total` brightens the whole screen, panel included.
        Box(modifier = Modifier.fillMaxSize().nukeFlash(serverFrame.world.flash)) {
            screen.PanelPattern()
            views.forEachIndexed { index, view ->
                val window = windows[index]
                // Legacy write_screen(): the losing player's window turns grey (not at night).
                val lost = !night && if (mode == GameMode.PLAYER_VS_PLAYER) view.hud.roundLost else missionEndBanner == MissionOutcome.LOST
                Box(
                    modifier = with(screen) { Modifier.legacyBounds(window.left, window.top, window.width, window.height) }
                        .grayscale(lost),
                ) {
                    if (view.controlledActorId == null) {
                        // Legacy write_screen(): no vehicle, static; one player and two vs computer
                        // still show the goal counters and the id.
                        StaticNoise(playfieldWidth = window.width, modifier = Modifier.fillMaxSize())
                        if (mode != GameMode.PLAYER_VS_PLAYER) NoVehicleStatus(view, window.width)
                    } else PlayerPane(
                        mission = mission,
                        serverFrame = serverFrame,
                        playerView = view,
                        events = eventQueues[index],
                        worldWidth = worldWidth,
                        worldHeight = worldHeight,
                        playfieldWidth = window.width,
                        playfieldHeight = window.height,
                        playerVsPlayer = mode == GameMode.PLAYER_VS_PLAYER,
                        showRadar = showRadar,
                        // Legacy write_arrow(): only player-vs-player points at the other player.
                        // With teams (ADR 0027) the nearest player of the other side.
                        opponent = if (mode == GameMode.PLAYER_VS_PLAYER) {
                            val tanks = serverFrame.world.actors.filterIsInstance<TankState>()
                            val own = tanks.firstOrNull { it.actorId == view.controlledActorId }
                            views.filter { (it.playerId and 1) != (view.playerId and 1) }
                                .mapNotNull { other -> tanks.firstOrNull { it.actorId == other.controlledActorId } }
                                .minByOrNull { if (own == null) 0 else (it.x - own.x) * (it.x - own.x) + (it.y - own.y) * (it.y - own.y) }
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Legacy write_winner_1p in every window; player vs player write_winner_2p's
                    // "YOU WIN" in the winner's window only.
                    val banner = missionEndBanner
                    if (mode == GameMode.PLAYER_VS_PLAYER) {
                        if (view.hud.roundWon) YouWinBanner(playfieldWidth = window.width, night = mission.sidecar.metadata.night)
                    } else if (banner != null) {
                        MissionEndBanner(outcome = banner, playfieldWidth = window.width, night = mission.sidecar.metadata.night)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerPane(
    mission: MissionEntry,
    serverFrame: ServerFrame,
    playerView: PlayerView,
    events: ViewportEventQueue,
    worldWidth: Int,
    worldHeight: Int,
    playfieldWidth: Int,
    playfieldHeight: Int,
    playerVsPlayer: Boolean,
    modifier: Modifier,
    opponent: TankState? = null,
    showRadar: Boolean = true,
) {
    var viewportRect by remember { mutableStateOf(Rect.Zero) }
    var viewportGeometry by remember { mutableStateOf<ViewportGeometry?>(null) }
    Box(modifier = modifier) {
        TankArenaViewport(
            sidecar = mission.sidecar,
            serverFrame = serverFrame,
            playerId = playerView.playerId,
            worldWidth = worldWidth,
            worldHeight = worldHeight,
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    viewportRect = coordinates.boundsInParent()
                },
            events = events,
            playfieldWidth = playfieldWidth,
            playfieldHeight = playfieldHeight,
            onGeometryChanged = { viewportGeometry = it },
        )
        val geometry = viewportGeometry
        if (geometry != null && viewportRect != Rect.Zero) {
            val sceneWidth = playfieldWidth.toFloat()
            val sceneHeight = playfieldHeight.toFloat()
            val scale = viewportRect.width / sceneWidth
            HudOverlay(
                hudState = playerView.hud,
                playerView = playerView,
                playfield = visiblePlayfield(geometry, viewportRect, sceneWidth, sceneHeight),
                scale = scale,
                playerVsPlayer = playerVsPlayer,
                night = mission.sidecar.metadata.night,
                opponent = opponent?.takeIf { !it.cloaked },
                ownCloaked = serverFrame.world.actors.any { it is TankState && it.actorId == playerView.controlledActorId && it.cloaked },
                viewLeft = (geometry.cameraCenterX - sceneWidth / 2f - geometry.renderOffsetX).coerceAtLeast(0f),
                viewTop = (geometry.cameraCenterY - sceneHeight / 2f - geometry.renderOffsetY).coerceAtLeast(0f),
                showRadar = showRadar,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (showDebugOverlay) {
            ViewportDebugOverlay(
                playerView = playerView,
                geometry = viewportGeometry,
                viewportRect = viewportRect,
                sceneWidth = playfieldWidth.toFloat(),
                sceneHeight = playfieldHeight.toFloat(),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Draws the content without colour (legacy `b_convert(scr, gray, ...)` for a lost mission). */
private fun Modifier.grayscale(enabled: Boolean): Modifier = if (!enabled) this else drawWithContent {
    val paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
    drawIntoCanvas { it.saveLayer(Rect(Offset.Zero, size), paint) }
    drawContent()
    drawIntoCanvas { it.restore() }
}

/**
 * Legacy set_palette() with `nuke_total`: while an A-bomb goes off every colour is brightened by
 * up to 63 of 63 steps, which adds that much white to the picture.
 */
private fun Modifier.nukeFlash(level: Int): Modifier = if (level <= 0) this else drawWithContent {
    drawContent()
    val add = (level.coerceAtMost(63) / 63f)
    drawRect(color = Color(add, add, add), blendMode = androidx.compose.ui.graphics.BlendMode.Plus)
}

/**
 * The part of the viewport that shows the map, in pane pixels. Legacy maps smaller than the
 * 640×400 playfield are shown in a window of the map's own size, and the status display sits in
 * that window's corners.
 */
private fun visiblePlayfield(geometry: ViewportGeometry, viewportRect: Rect, sceneWidth: Float, sceneHeight: Float): Rect {
    val scale = viewportRect.width / sceneWidth
    val sceneLeft = geometry.cameraCenterX - sceneWidth / 2f
    val sceneTop = geometry.cameraCenterY - sceneHeight / 2f
    val left = maxOf(geometry.renderOffsetX, sceneLeft)
    val top = maxOf(geometry.renderOffsetY, sceneTop)
    val right = minOf(geometry.renderOffsetX + geometry.worldWidth, sceneLeft + sceneWidth)
    val bottom = minOf(geometry.renderOffsetY + geometry.worldHeight, sceneTop + sceneHeight)
    return Rect(
        left = viewportRect.left + (left - sceneLeft) * scale,
        top = viewportRect.top + (top - sceneTop) * scale,
        right = viewportRect.left + (right - sceneLeft) * scale,
        bottom = viewportRect.top + (bottom - sceneTop) * scale,
    )
}

@Composable
private fun ViewportDebugOverlay(
    playerView: PlayerView,
    geometry: ViewportGeometry?,
    viewportRect: Rect,
    sceneWidth: Float,
    sceneHeight: Float,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                color = Color(0xFF00D7FF),
                style = Stroke(width = 1f),
            )
            if (viewportRect != Rect.Zero) {
                drawRect(
                    color = Color(0xFFFFD54F),
                    topLeft = viewportRect.topLeft,
                    size = viewportRect.size,
                    style = Stroke(width = 2f),
                )
            }
            if (geometry != null && viewportRect != Rect.Zero) {
                val viewportSceneWidth = sceneWidth
                val viewportSceneHeight = sceneHeight
                val sceneLeft = geometry.cameraCenterX - viewportSceneWidth / 2f
                val sceneTop = geometry.cameraCenterY - viewportSceneHeight / 2f
                val scaleX = viewportRect.width / viewportSceneWidth
                val scaleY = viewportRect.height / viewportSceneHeight
                val worldLeft = viewportRect.left + (geometry.renderOffsetX - sceneLeft) * scaleX
                val worldTop = viewportRect.top + (geometry.renderOffsetY - sceneTop) * scaleY
                val worldWidthPx = geometry.worldWidth * scaleX
                val worldHeightPx = geometry.worldHeight * scaleY
                drawRect(
                    color = Color(0xFF4CAF50),
                    topLeft = Offset(worldLeft, worldTop),
                    size = androidx.compose.ui.geometry.Size(worldWidthPx, worldHeightPx),
                    style = Stroke(width = 2f),
                )
                val cameraCenter = Offset(
                    x = viewportRect.left + viewportRect.width / 2f,
                    y = viewportRect.top + viewportRect.height / 2f,
                )
                drawLine(
                    color = Color(0xFFFF5252),
                    start = Offset(cameraCenter.x - 8f, cameraCenter.y),
                    end = Offset(cameraCenter.x + 8f, cameraCenter.y),
                    strokeWidth = 2f,
                )
                drawLine(
                    color = Color(0xFFFF5252),
                    start = Offset(cameraCenter.x, cameraCenter.y - 8f),
                    end = Offset(cameraCenter.x, cameraCenter.y + 8f),
                    strokeWidth = 2f,
                )
            }
        }
        if (geometry != null) {
            Text(
                text = buildString {
                    append("viewport=")
                    append(viewportRect.width.toInt())
                    append("x")
                    append(viewportRect.height.toInt())
                    append(" scene=")
                    append(geometry.sceneWidth)
                    append("x")
                    append(geometry.sceneHeight)
                    append(" world=")
                    append(geometry.worldWidth)
                    append("x")
                    append(geometry.worldHeight)
                    append(" offset=")
                    append(geometry.renderOffsetX.toInt())
                    append(",")
                    append(geometry.renderOffsetY.toInt())
                    append(" camera=")
                    append(geometry.cameraCenterX.toInt())
                    append(",")
                    append(geometry.cameraCenterY.toInt())
                },
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .background(Color(0xCC101010))
                    .padding(6.dp),
            )
        }
    }
}

/** A pilot's message shown by the shell rather than the game: two lines and the steps it has left. */
internal data class HudMessage(val line1: String, val line2: String, val steps: Int)


/** The free camera's zoom limits: a quarter of the game's scale up to four times it. */
internal const val MIN_CAMERA_ZOOM = 0.25f
internal const val MAX_CAMERA_ZOOM = 4f

/**
 * A replay's free camera: one view of the map, without status displays, around ([cameraX],
 * [cameraY]) at [zoom] (1: the game's own scale, where the screen is 480 map pixels high).
 * [onScale] reports the screen pixels per map pixel, for moving the camera with the mouse.
 */
@Composable
internal fun FreeCameraView(
    mission: MissionEntry,
    serverFrame: ServerFrame,
    events: ViewportEventQueue,
    cameraX: Float,
    cameraY: Float,
    zoom: Float,
    modifier: Modifier = Modifier,
    onScale: (Float) -> Unit = {},
) {
    val worldWidth = mission.sidecar.metadata.widthTiles * LEGACY_TILE_SIZE
    val worldHeight = mission.sidecar.metadata.heightTiles * LEGACY_TILE_SIZE
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current.density
        val scale = zoom * constraints.maxHeight / LEGACY_SCREEN_HEIGHT
        SideEffect { onScale(scale) }
        // As much of the map as fits, but never more than the map.
        val width = (constraints.maxWidth / scale).toInt().coerceIn(1, worldWidth)
        val height = (constraints.maxHeight / scale).toInt().coerceIn(1, worldHeight)
        val view = serverFrame.playerViews.firstOrNull() ?: return@BoxWithConstraints
        val frame = serverFrame.copy(
            playerViews = listOf(view.copy(cameraCenterX = cameraX.toInt(), cameraCenterY = cameraY.toInt())),
        )
        TankArenaViewport(
            sidecar = mission.sidecar,
            serverFrame = frame,
            playerId = view.playerId,
            worldWidth = worldWidth,
            worldHeight = worldHeight,
            modifier = Modifier.size(Dp(width * scale / density), Dp(height * scale / density)),
            events = events,
            playfieldWidth = width,
            playfieldHeight = height,
        )
    }
}

private const val LEGACY_SCREEN_HEIGHT = 480f

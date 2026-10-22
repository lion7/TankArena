package nl.tankarena.render

import androidx.collection.emptyIntObjectMap
import androidx.collection.IntObjectMap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import nl.tankarena.content.LEGACY_TILE_SIZE
import nl.tankarena.content.LegacyPictureVariant
import nl.tankarena.content.LegacySpriteResources
import nl.tankarena.content.MapSceneSidecar
import nl.tankarena.content.TankArenaWorld
import nl.tankarena.protocol.snapshot.PlayerView
import nl.tankarena.protocol.snapshot.TileState
import org.jetbrains.compose.resources.DrawableResource
import kotlin.math.max
import kotlin.math.min

internal const val TILE_SIZE = LEGACY_TILE_SIZE.toFloat()
/** Legacy `buf_x` × `buf_y` of a single-player game on the 640×480 screen (`compute_window_size()`). */
internal const val LEGACY_PLAYFIELD_WIDTH = 639f
internal const val LEGACY_PLAYFIELD_HEIGHT = 480f

data class ViewportGeometry(
    val worldWidth: Int,
    val worldHeight: Int,
    val sceneWidth: Int,
    val sceneHeight: Int,
    val renderOffsetX: Float,
    val renderOffsetY: Float,
    val cameraCenterX: Float,
    val cameraCenterY: Float,
)

internal class RuntimeSnapshot(
    var sidecar: MapSceneSidecar?,
    var playerView: PlayerView,
    var geometry: ViewportGeometry,
) {
    /** The visible scene size (legacy `buf_x` × `buf_y`), for drawing only the tiles in view. */
    var viewWidth: Int = LEGACY_PLAYFIELD_WIDTH.toInt()
    var viewHeight: Int = LEGACY_PLAYFIELD_HEIGHT.toInt()

    /**
     * The tile columns and rows in view, with a tile to spare on each side for the pictures and
     * shadows that reach into the next tile.
     */
    fun visibleTiles(widthTiles: Int, heightTiles: Int): Pair<IntRange, IntRange> {
        val left = geometry.cameraCenterX - viewWidth / 2f - renderOffsetX
        val top = geometry.cameraCenterY - viewHeight / 2f - renderOffsetY
        val firstX = (kotlin.math.floor(left / TILE_SIZE).toInt() - 1).coerceAtLeast(0)
        val firstY = (kotlin.math.floor(top / TILE_SIZE).toInt() - 1).coerceAtLeast(0)
        val lastX = (kotlin.math.floor((left + viewWidth) / TILE_SIZE).toInt() + 1).coerceAtMost(widthTiles - 1)
        val lastY = (kotlin.math.floor((top + viewHeight) / TILE_SIZE).toInt() + 1).coerceAtMost(heightTiles - 1)
        return firstX..lastX to firstY..lastY
    }

    /** Tiles the server reports as changed (destroyed buildings, bridges turned to water), by index. */
    var changedTiles: IntObjectMap<TileState> = emptyIntObjectMap()

    val sceneWidth: Int
        get() = geometry.sceneWidth

    val sceneHeight: Int
        get() = geometry.sceneHeight

    val renderOffsetX: Float
        get() = geometry.renderOffsetX

    val renderOffsetY: Float
        get() = geometry.renderOffsetY
}

internal fun resolveViewportGeometry(
    playerView: PlayerView,
    worldWidth: Int,
    worldHeight: Int,
    playfieldWidth: Int = LEGACY_PLAYFIELD_WIDTH.toInt(),
    playfieldHeight: Int = LEGACY_PLAYFIELD_HEIGHT.toInt(),
): ViewportGeometry {
    val viewportWidth = playfieldWidth.toFloat()
    val viewportHeight = playfieldHeight.toFloat()
    // A map smaller than the window gets a window of its own size (legacy compute_window_size());
    // the scene is only padded when the caller asks for a larger view than that.
    val sceneWidth = max(worldWidth.toFloat(), viewportWidth)
    val sceneHeight = max(worldHeight.toFloat(), viewportHeight)
    val renderOffsetX = ((sceneWidth - worldWidth) / 2f).coerceAtLeast(0f)
    val renderOffsetY = ((sceneHeight - worldHeight) / 2f).coerceAtLeast(0f)
    val sceneCenterX = sceneWidth / 2f
    val sceneCenterY = sceneHeight / 2f

    val cameraCenterX = if (sceneWidth <= viewportWidth) {
        sceneCenterX
    } else {
        (playerView.cameraCenterX + renderOffsetX)
            .coerceIn(viewportWidth / 2f, sceneWidth - viewportWidth / 2f)
    }

    val cameraCenterY = if (sceneHeight <= viewportHeight) {
        sceneCenterY
    } else {
        (playerView.cameraCenterY + renderOffsetY)
            .coerceIn(viewportHeight / 2f, sceneHeight - viewportHeight / 2f)
    }

    return ViewportGeometry(
        worldWidth = worldWidth,
        worldHeight = worldHeight,
        sceneWidth = sceneWidth.toInt(),
        sceneHeight = sceneHeight.toInt(),
        renderOffsetX = renderOffsetX,
        renderOffsetY = renderOffsetY,
        cameraCenterX = cameraCenterX,
        cameraCenterY = cameraCenterY,
    )
}

internal fun createWorldBody(worldWidth: Int, worldHeight: Int): RenderBody = RenderBody(
    position = Offset(0f, 0f),
    size = Size(worldWidth.toFloat(), worldHeight.toFloat()),
    pivot = Offset(0f, 0f),
)

/**
 * Legacy `bkground(x, y)` (original/src/include/function.h): every tile is first painted with one of four
 * world background pictures, `BACK1`..`BACK16` (`back[variant + 4 * map.background]`). The
 * original picks the variant from bytes of its own code segment (`_bkground` in
 * original/src/world/rawunder.s), which is effectively fixed noise; a hash of the tile gives the same look.
 */
fun backgroundTileName(world: TankArenaWorld, tileX: Int, tileY: Int): String {
    val hash = (tileX * 73_856_093) xor (tileY * 19_349_663)
    val variant = (hash ushr 7) and 3
    return BACKGROUND_NAMES[world.ordinal * 4 + variant]
}

/** `BACK1`..`BACK16`, made once: every visible tile asks for its name in every frame. */
private val BACKGROUND_NAMES = Array(TankArenaWorld.entries.size * 4) { "BACK${it + 1}" }

internal fun addTileSprite(
    target: MutableSet<DrawableResource>,
    world: TankArenaWorld,
    tileId: Int,
    sprites: LegacySpriteCatalog,
) {
    if (tileId < 0) return
    // Every variant: buildings switch to their ruined pictures when destroyed.
    for (variant in LegacyPictureVariant.entries) {
        val name = LegacySpriteResources.nameForTile(world, tileId, variant) ?: continue
        sprites.findResource(name)?.let(target::add)
    }
}

internal fun DrawScope.drawSprite(
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

internal fun DrawScope.drawSpriteCentered(
    image: ImageBitmap,
    centerX: Float,
    centerY: Float,
    width: Int,
    height: Int,
) {
    drawSprite(image, centerX - width / 2f, centerY - height / 2f, width, height)
}

/** A map window on the 640×480 game screen, in screen pixels. */
data class LegacyMapWindow(val left: Int, val top: Int, val width: Int, val height: Int)

/**
 * Legacy compute_window_size() (original/src/world/under.c) and write_screen() (original/src/game/mainloop.c):
 * one window per player, `buf_x` = 640 / players − 1 wide and 480 high, or the map's own size
 * when it is smaller. One window is centred across; two are spread with equal gaps. Both sit a
 * third of the free height from the top, and start on a multiple of 4 pixels.
 */
fun legacyMapWindows(
    players: Int,
    worldWidth: Int,
    worldHeight: Int,
    windowSize: Int = 0,
    /** Legacy pixels across the screen: 640, or more on Extended's wide screen. */
    screenWidth: Int = SCREEN_WIDTH,
): List<LegacyMapWindow> {
    if (players > 2) return fourWindows(players, worldWidth, worldHeight, windowSize, screenWidth)
    // Legacy compute_window_size(): every step of `win_size` (0..5) takes 5% of the screen off.
    val width = min((screenWidth - screenWidth * windowSize / 20) / players - 1, worldWidth)
    val height = min(SCREEN_HEIGHT - SCREEN_HEIGHT * windowSize / 20, worldHeight)
    val first = if (players == 1) (screenWidth - width) / 2 else (screenWidth - 2 * width) / 3
    val top = (SCREEN_HEIGHT - height) / 3
    return List(players) { index ->
        val left = if (index == 1) first + first + width + 1 else first
        LegacyMapWindow(left = left and 3.inv(), top = top, width = width, height = height)
    }
}

/**
 * Extended's three or four players (ADR 0027): two rows of two windows, team one (players 1 and 3)
 * on the left, as the original puts player one; gaps as the two-player layout has them.
 */
private fun fourWindows(players: Int, worldWidth: Int, worldHeight: Int, windowSize: Int, screenWidth: Int): List<LegacyMapWindow> {
    val width = min((screenWidth - screenWidth * windowSize / 20) / 2 - 1, worldWidth)
    val height = min((SCREEN_HEIGHT - SCREEN_HEIGHT * windowSize / 20) / 2 - 1, worldHeight)
    val firstX = (screenWidth - 2 * width) / 3
    val firstY = (SCREEN_HEIGHT - 2 * height) / 3
    return List(players) { index ->
        val left = if (index % 2 == 1) firstX + firstX + width + 1 else firstX
        val top = if (index >= 2) firstY + firstY + height + 1 else firstY
        LegacyMapWindow(left = left and 3.inv(), top = top, width = width, height = height)
    }
}

private const val SCREEN_WIDTH = 640
private const val SCREEN_HEIGHT = 480

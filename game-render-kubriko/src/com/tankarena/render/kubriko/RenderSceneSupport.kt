package com.tankarena.render.kubriko

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.SceneOffset
import com.pandulapeter.kubriko.types.SceneSize
import com.tankarena.content.CanonicalMapDefinition
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.LegacyPictureVariant
import com.tankarena.content.LegacySpriteResources
import com.tankarena.content.TankArenaWorld
import com.tankarena.protocol.PlayerFrame
import org.jetbrains.compose.resources.DrawableResource
import kotlin.math.max
import kotlin.math.min

internal const val TILE_SIZE = LEGACY_TILE_SIZE.toFloat()
internal const val LEGACY_PLAYFIELD_WIDTH = 640f
internal const val LEGACY_PLAYFIELD_HEIGHT = 400f

const val LEGACY_PLAYFIELD_ASPECT_RATIO: Float =
    LEGACY_PLAYFIELD_WIDTH / LEGACY_PLAYFIELD_HEIGHT

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
    var map: CanonicalMapDefinition?,
    var playerFrame: PlayerFrame,
    var geometry: ViewportGeometry,
) {
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
    playerFrame: PlayerFrame,
    worldWidth: Int,
    worldHeight: Int,
): ViewportGeometry {
    val viewportWidth = playerFrame.camera.width.toFloat()
    val viewportHeight = playerFrame.camera.height.toFloat()
    val sceneWidth = max(worldWidth.toFloat(), LEGACY_PLAYFIELD_WIDTH)
    val sceneHeight = max(worldHeight.toFloat(), LEGACY_PLAYFIELD_HEIGHT)
    val renderOffsetX = ((sceneWidth - worldWidth) / 2f).coerceAtLeast(0f)
    val renderOffsetY = ((sceneHeight - worldHeight) / 2f).coerceAtLeast(0f)
    val sceneCenterX = sceneWidth / 2f
    val sceneCenterY = sceneHeight / 2f

    val cameraCenterX = if (sceneWidth <= viewportWidth) {
        sceneCenterX
    } else {
        (playerFrame.camera.centerX + renderOffsetX)
            .coerceIn(viewportWidth / 2f, sceneWidth - viewportWidth / 2f)
    }

    val cameraCenterY = if (sceneHeight <= viewportHeight) {
        sceneCenterY
    } else {
        (playerFrame.camera.centerY + renderOffsetY)
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

internal fun createWorldBody(worldWidth: Int, worldHeight: Int): BoxBody = BoxBody(
    initialPosition = SceneOffset(0f.sceneUnit, 0f.sceneUnit),
    initialSize = SceneSize(worldWidth.toFloat().sceneUnit, worldHeight.toFloat().sceneUnit),
    initialPivot = SceneOffset(0f.sceneUnit, 0f.sceneUnit),
)

internal fun addTileSprite(
    target: MutableSet<DrawableResource>,
    world: TankArenaWorld,
    tileId: Int,
    sprites: LegacySpriteCatalog,
) {
    if (tileId < 0) return
    val name = LegacySpriteResources.nameForTile(world, tileId, LegacyPictureVariant.INTACT) ?: return
    sprites.findResource(name)?.let(target::add)
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

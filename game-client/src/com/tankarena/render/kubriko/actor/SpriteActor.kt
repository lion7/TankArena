package com.tankarena.render.kubriko.actor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.pandulapeter.kubriko.sprites.SpriteManager
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.render.kubriko.LegacySpriteCatalog
import com.tankarena.render.kubriko.RuntimeSnapshot
import org.jetbrains.compose.resources.DrawableResource

/**
 * Renders one replicated object as a single legacy picture, drawn at its native size and
 * centred on the object. Used for the object families that have no bespoke actor (mines,
 * rockets, mortars, flags, products, warps, trains, bombers, explosions).
 */
internal open class SpriteActor(
    id: Long,
    snapshot: RuntimeSnapshot,
    private val spriteManager: SpriteManager,
    private val sprites: LegacySpriteCatalog,
    private val fallbackColor: Color = Color(0xFFFFD54F),
    override val layerIndex: Int = 2,
) : RenderActor(
    id = id,
    snapshot = snapshot,
    width = LEGACY_TILE_SIZE,
    height = LEGACY_TILE_SIZE,
) {
    /** Legacy picture name to draw, or null to draw nothing this frame. */
    var spriteName: String? = null

    fun moveTo(worldX: Int, worldY: Int) {
        setCenter(
            x = snapshot.renderOffsetX + worldX,
            y = snapshot.renderOffsetY + worldY,
        )
    }

    fun collectSpriteResources(target: MutableSet<DrawableResource>) {
        spriteName?.let(sprites::findResource)?.let(target::add)
    }

    override fun DrawScope.draw() {
        val name = spriteName ?: return
        val image = sprites.findResource(name)?.let(spriteManager::get)
        val centre = LEGACY_TILE_SIZE / 2f
        if (image == null) {
            drawCircle(fallbackColor, radius = 3f, center = Offset(centre, centre))
            return
        }
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset((centre - image.width / 2f).toInt(), (centre - image.height / 2f).toInt()),
            dstSize = IntSize(image.width, image.height),
            filterQuality = FilterQuality.None,
        )
    }
}

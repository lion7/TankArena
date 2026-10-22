package nl.tankarena.render.actor

import nl.tankarena.render.PictureCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import nl.tankarena.content.LEGACY_TILE_SIZE
import nl.tankarena.render.LegacySpriteCatalog
import nl.tankarena.render.RuntimeSnapshot
import org.jetbrains.compose.resources.DrawableResource

/**
 * Renders one replicated object as a single legacy picture, drawn at its native size and
 * centred on the object. Used for the object families that have no bespoke actor (mines,
 * rockets, mortars, flags, products, warps, trains, bombers, explosions).
 */
internal open class SpriteActor(
    id: Long,
    snapshot: RuntimeSnapshot,
    private val pictures: PictureCache,
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
    /** Mirror the picture left-right / top-bottom (legacy b_add_layer_y() / b_add_layer_x()). */
    var flipHorizontal: Boolean = false
    var flipVertical: Boolean = false
    /** Legacy b_add_shadow(): also draw the picture's shadow this many pixels right (and twice that down). */
    var shadowOffset: Int = 0

    /**
     * Legacy b_add_layer_d() (original/src/world/under.c): a picture drawn in one of 16 directions from the
     * five pictures `base` (north) to `base + 4` (east), mirrored for the other directions.
     */
    fun showDirectional(direction: Int, picture: (Int) -> String) {
        val frame = directionalFrame(direction)
        spriteName = picture(frame.index)
        flipHorizontal = frame.flipHorizontal
        flipVertical = frame.flipVertical
    }

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
        val image = sprites.findResource(name)?.let(pictures::get)
        val centre = LEGACY_TILE_SIZE / 2f
        if (image == null) {
            drawCircle(fallbackColor, radius = 3f, center = Offset(centre, centre))
            return
        }
        val left = (centre - image.width / 2f).toInt()
        val top = (centre - image.height / 2f).toInt()
        if (shadowOffset != 0) {
            drawLegacyShadow(image, left + shadowOffset, top + 2 * shadowOffset, flipHorizontal, flipVertical)
        }
        drawLegacyPicture(image, left, top, flipHorizontal, flipVertical)
    }
}

/** One of the 16 directions as legacy b_add_layer_d() draws it: which of five pictures, and how mirrored. */
internal data class DirectionalFrame(val index: Int, val flipHorizontal: Boolean, val flipVertical: Boolean)

internal fun directionalFrame(direction: Int): DirectionalFrame {
    val dir = ((direction % 16) + 16) % 16
    return when {
        dir <= 4 -> DirectionalFrame(dir, flipHorizontal = false, flipVertical = false)
        dir <= 8 -> DirectionalFrame(8 - dir, flipHorizontal = false, flipVertical = true)
        dir <= 12 -> DirectionalFrame(dir - 8, flipHorizontal = true, flipVertical = true)
        else -> DirectionalFrame(16 - dir, flipHorizontal = true, flipVertical = false)
    }
}

/**
 * Legacy b_add_shadow() (original/src/graph/sprites.s): where the picture is opaque, the screen pixel
 * underneath becomes the palette grey `gray[]` of its brightness — a grey at about 57 % of the
 * pixel's luma (init.c: grey entry `luma / 6` of the 16-step ramp, whose entry 10 is 36/63). It is
 * drawn as two passes over what is already there: take its colour away, then darken it.
 */
internal fun DrawScope.drawLegacyShadow(
    image: ImageBitmap,
    left: Int,
    top: Int,
    flipHorizontal: Boolean = false,
    flipVertical: Boolean = false,
) {
    drawLegacyPicture(image, left, top, flipHorizontal, flipVertical, SHADOW_GREY, BlendMode.Saturation)
    drawLegacyPicture(image, left, top, flipHorizontal, flipVertical, SHADOW_DARKEN, BlendMode.Multiply)
}

private val SHADOW_GREY: ColorFilter = ColorFilter.tint(Color(0xFF808080), BlendMode.SrcIn)
private val SHADOW_DARKEN: ColorFilter = ColorFilter.tint(Color(0.57f, 0.57f, 0.57f), BlendMode.SrcIn)

/** Draw [image] at its native size with its top-left at ([left], [top]), optionally mirrored. */
internal fun DrawScope.drawLegacyPicture(
    image: ImageBitmap,
    left: Int,
    top: Int,
    flipHorizontal: Boolean = false,
    flipVertical: Boolean = false,
    colorFilter: ColorFilter? = null,
    blendMode: BlendMode = BlendMode.SrcOver,
) {
    val draw: DrawScope.() -> Unit = {
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset(left, top),
            dstSize = IntSize(image.width, image.height),
            filterQuality = FilterQuality.None,
            colorFilter = colorFilter,
            blendMode = blendMode,
        )
    }
    if (!flipHorizontal && !flipVertical) {
        draw()
        return
    }
    scale(
        scaleX = if (flipHorizontal) -1f else 1f,
        scaleY = if (flipVertical) -1f else 1f,
        pivot = Offset(left + image.width / 2f, top + image.height / 2f),
        block = draw,
    )
}

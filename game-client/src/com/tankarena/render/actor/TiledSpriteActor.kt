package com.tankarena.render.actor

import com.tankarena.render.PictureCache
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.render.LegacySpriteCatalog
import com.tankarena.render.RuntimeSnapshot
import org.jetbrains.compose.resources.DrawableResource

/**
 * An object drawn as a block of legacy pictures (the B52 is 2×2 tiles, the zeppelin 8×4), each
 * tile placed relative to the object's centre, with the legacy shadow `h` right and `2h` down.
 */
internal class TiledSpriteActor(
    id: Long,
    snapshot: RuntimeSnapshot,
    private val pictures: PictureCache,
    private val sprites: LegacySpriteCatalog,
    override val layerIndex: Int,
    private val extentTiles: Int,
) : RenderActor(
    id = id,
    snapshot = snapshot,
    width = extentTiles * LEGACY_TILE_SIZE,
    height = extentTiles * LEGACY_TILE_SIZE,
) {
    /** One picture: its legacy name and its top-left corner relative to the object's centre. */
    data class Tile(val name: String, val dx: Int, val dy: Int, val castsShadow: Boolean = true)

    var tiles: List<Tile> = emptyList()
    var shadowOffset: Int = 0

    fun moveTo(worldX: Int, worldY: Int) {
        setCenter(x = snapshot.renderOffsetX + worldX, y = snapshot.renderOffsetY + worldY)
    }

    fun collectSpriteResources(target: MutableSet<DrawableResource>) {
        tiles.forEach { tile -> sprites.findResource(tile.name)?.let(target::add) }
    }

    override fun DrawScope.draw() {
        val half = extentTiles * LEGACY_TILE_SIZE / 2
        val images = tiles.map { it to sprites.findResource(it.name)?.let(pictures::get) }
        if (shadowOffset != 0) {
            for ((tile, image) in images) {
                if (image == null || !tile.castsShadow) continue
                drawLegacyShadow(image, half + tile.dx + shadowOffset, half + tile.dy + 2 * shadowOffset)
            }
        }
        for ((tile, image) in images) {
            image ?: continue
            drawLegacyPicture(image, half + tile.dx, half + tile.dy)
        }
    }
}

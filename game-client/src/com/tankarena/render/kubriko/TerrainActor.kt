package com.tankarena.render.kubriko

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.actor.traits.Visible
import com.pandulapeter.kubriko.sprites.SpriteManager
import com.tankarena.content.LegacyPictureVariant
import com.tankarena.content.LegacySpriteResources
import com.tankarena.content.TankArenaWorld

internal class TerrainActor(
    private val snapshot: RuntimeSnapshot,
    private val spriteManager: SpriteManager,
    private val sprites: LegacySpriteCatalog,
) : Visible {
    override var body: BoxBody = createWorldBody(snapshot.sceneWidth, snapshot.sceneHeight)
    override val layerIndex: Int = 0

    fun syncBounds() {
        body = createWorldBody(snapshot.sceneWidth, snapshot.sceneHeight)
    }

    override fun DrawScope.draw() {
        // Outside the map the shell's tiled background shows through, as around the legacy
        // playfield window.
        val sidecar = snapshot.sidecar
        val tiles = sidecar?.tileLayers ?: return
        val width = sidecar.metadata.widthTiles
        val height = sidecar.metadata.heightTiles
        val theme = sidecar.metadata.world
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = x + y * width
                val px = snapshot.renderOffsetX + x * TILE_SIZE
                val py = snapshot.renderOffsetY + y * TILE_SIZE
                drawBackground(theme, x, y, px, py)
                drawTile(theme, tiles.base[index], px, py)
                drawTile(theme, tiles.solid[index], px, py)
                drawTile(theme, tiles.top[index], px, py)
            }
        }
    }

    private fun DrawScope.drawBackground(world: TankArenaWorld, tileX: Int, tileY: Int, x: Float, y: Float) {
        val image = sprites.findResource(backgroundTileName(world, tileX, tileY))?.let(spriteManager::get) ?: return
        drawSprite(image, x, y, TILE_SIZE.toInt(), TILE_SIZE.toInt())
    }

    private fun DrawScope.drawTile(world: TankArenaWorld, tileId: Int, x: Float, y: Float) {
        if (tileId < 0) return
        val name = LegacySpriteResources.nameForTile(world, tileId, LegacyPictureVariant.INTACT) ?: return
        val resource = sprites.findResource(name)
        val image = resource?.let(spriteManager::get)
        if (image == null) {
            val seed = tileId.coerceAtLeast(0)
            drawRect(
                color = Color(40 + (seed * 53) % 140, 50 + (seed * 29) % 120, 60 + (seed * 11) % 100),
                topLeft = Offset(x, y),
                size = Size(TILE_SIZE, TILE_SIZE),
            )
            return
        }
        drawSprite(image, x, y, TILE_SIZE.toInt(), TILE_SIZE.toInt())
    }
}

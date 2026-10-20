package nl.tankarena.render

import nl.tankarena.render.actor.drawLegacyShadow

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import nl.tankarena.content.LegacyPictureVariant
import nl.tankarena.content.PictureCatalog
import nl.tankarena.content.LegacySpriteResources
import nl.tankarena.content.TankArenaWorld

internal class TerrainActor(
    private val snapshot: RuntimeSnapshot,
    private val pictures: PictureCache,
    private val sprites: LegacySpriteCatalog,
) : SceneDrawable {
    override var body: RenderBody = createWorldBody(snapshot.sceneWidth, snapshot.sceneHeight)
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
        val (columns, rows) = snapshot.visibleTiles(width, height)
        for (y in rows) {
            for (x in columns) {
                val index = x + y * width
                val px = snapshot.renderOffsetX + x * TILE_SIZE
                val py = snapshot.renderOffsetY + y * TILE_SIZE
                val changed = snapshot.changedTiles[index]
                drawBackground(theme, x, y, px, py)
                drawTile(theme, changed?.base ?: tiles.base[index], px, py)
                // Legacy bottom_layer(): a fallen building shows its ruined picture (`ptr[1]`).
                val solid = changed?.solid ?: tiles.solid[index]
                val standing = changed?.solidStanding ?: initiallyStanding(theme, solid, tiles.top[index])
                drawTile(theme, solid, px, py, if (standing) LegacyPictureVariant.INTACT else LegacyPictureVariant.DAMAGED)
            }
        }
    }

    private fun DrawScope.drawBackground(world: TankArenaWorld, tileX: Int, tileY: Int, x: Float, y: Float) {
        val image = sprites.findResource(backgroundTileName(world, tileX, tileY))?.let(pictures::get) ?: return
        drawSprite(image, x, y, TILE_SIZE.toInt(), TILE_SIZE.toInt())
    }

    private fun DrawScope.drawTile(
        world: TankArenaWorld,
        tileId: Int,
        x: Float,
        y: Float,
        variant: LegacyPictureVariant = LegacyPictureVariant.INTACT,
    ) {
        if (tileId < 0) return
        val name = LegacySpriteResources.nameForTile(world, tileId, variant) ?: return
        val resource = sprites.findResource(name)
        val image = resource?.let(pictures::get)
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

/**
 * Legacy load_map(): a solid-layer structure stands while its normal strength is above zero; a
 * tile that is indestructible in either layer counts as standing.
 */
internal fun initiallyStanding(world: TankArenaWorld, solid: Int, top: Int): Boolean {
    val solidStrength = PictureCatalog.legacyRow(world, solid)?.strength?.firstOrNull() ?: 0
    val topStrength = PictureCatalog.legacyRow(world, top)?.strength?.firstOrNull() ?: 0
    return solidStrength > 0 || topStrength == 1000
}

/**
 * Legacy top layer (`mapm.m[2]`, drawn after the objects in src/world/under.c): roofs and tree
 * tops above the vehicles, only over a standing building, with the ruined picture once the
 * top-layer piece itself is destroyed.
 */
internal class TopLayerActor(
    private val snapshot: RuntimeSnapshot,
    private val pictures: PictureCache,
    private val sprites: LegacySpriteCatalog,
) : SceneDrawable {
    override var body: RenderBody = createWorldBody(snapshot.sceneWidth, snapshot.sceneHeight)
    override val layerIndex: Int = 3

    fun syncBounds() {
        body = createWorldBody(snapshot.sceneWidth, snapshot.sceneHeight)
    }

    /**
     * Legacy top_layer(): first every roof casts its shadow 1 px right and 2 px down, then the
     * roofs are drawn over the shadows.
     */
    override fun DrawScope.draw() {
        val sidecar = snapshot.sidecar ?: return
        val tiles = sidecar.tileLayers ?: return
        val width = sidecar.metadata.widthTiles
        val height = sidecar.metadata.heightTiles
        val world = sidecar.metadata.world
        val roofs = ArrayList<Triple<androidx.compose.ui.graphics.ImageBitmap, Int, Int>>()
        val (columns, rows) = snapshot.visibleTiles(width, height)
        for (y in rows) {
            for (x in columns) {
                val index = x + y * width
                val changed = snapshot.changedTiles[index]
                val top = changed?.top ?: tiles.top[index]
                if (top < 0) continue
                val standing = changed?.solidStanding ?: initiallyStanding(world, tiles.solid[index], top)
                if (!standing) continue
                val intact = changed?.topIntact ?: true
                val variant = if (intact) LegacyPictureVariant.INTACT else LegacyPictureVariant.DAMAGED
                val name = LegacySpriteResources.nameForTile(world, top, variant) ?: continue
                val image = sprites.findResource(name)?.let(pictures::get) ?: continue
                roofs += Triple(image, (snapshot.renderOffsetX + x * TILE_SIZE).toInt(), (snapshot.renderOffsetY + y * TILE_SIZE).toInt())
            }
        }
        for ((image, left, top) in roofs) drawLegacyShadow(image, left + 1, top + 2)
        for ((image, left, top) in roofs) drawSprite(image, left.toFloat(), top.toFloat(), TILE_SIZE.toInt(), TILE_SIZE.toInt())
    }
}

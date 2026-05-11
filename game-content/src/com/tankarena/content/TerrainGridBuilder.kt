package com.tankarena.content

import com.tankarena.protocol.snapshot.TerrainGrid
import com.tankarena.protocol.snapshot.TerrainMaterial
import com.tankarena.protocol.snapshot.TerrainTile

/**
 * Builds a [TerrainGrid] from a [TileLayers] and [MapMetadata].
 *
 * The legacy code applies speed multipliers from both layer 0 (base) and layer 1 (top),
 * multiplying them together. The material comes from whichever layer has a non-default entry.
 */
object TerrainGridBuilder {

    /**
     * Build a TerrainGrid from tile layers and map metadata.
     *
     * @param layers The tile layers (base + top picture indices).
     * @param metadata Map metadata including world type and dimensions.
     * @return A TerrainGrid with per-tile material and speed info.
     */
    fun build(layers: TileLayers?, metadata: MapMetadata): TerrainGrid {
        if (layers == null) {
            return TerrainGrid.empty(metadata.widthTiles, metadata.heightTiles)
        }

        val w = metadata.widthTiles
        val h = metadata.heightTiles
        val size = w * h
        val tiles = Array(size) { TerrainTile() }

        val base = layers.base
        val top = layers.top

        for (i in 0 until size) {
            val baseIdx = base.getOrElse(i) { -1 }
            val topIdx = top.getOrElse(i) { -1 }

            // Resolve base layer
            val baseTile = if (baseIdx >= 0) PictureCatalog.resolve(metadata.world, baseIdx) else TerrainTile()
            // Resolve top layer
            val topTile = if (topIdx >= 0) PictureCatalog.resolve(metadata.world, topIdx) else TerrainTile()

            // Speed multipliers multiply (legacy behavior: dv *= pic[layer0].speed; dv *= pic[layer1].speed)
            val speed = baseTile.speedMultiplier * topTile.speedMultiplier

            // Material: top layer overrides base where it has a non-NORMAL material
            val material = if (topTile.material != TerrainMaterial.NORMAL) topTile.material else baseTile.material

            tiles[i] = TerrainTile(material = material, speedMultiplier = speed)
        }

        return TerrainGrid.fromTiles(w, h, tiles)
    }
}

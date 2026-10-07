package com.tankarena.content

import com.tankarena.protocol.snapshot.TerrainGrid
import com.tankarena.protocol.snapshot.TerrainTile

/**
 * Builds a [TerrainGrid] from a [TileLayers] and [MapMetadata].
 *
 * Follows the legacy lookups in src/tanks/control.c:
 * - the tile type comes from layer 0 (base) and falls through to layer 1 (solid) when the
 *   base tile is missing or has type `NOTHING` (`get_underground()` / `get_type()`);
 * - the speed multiplies the base and solid pictures' speeds (`move_tank()`). The legacy code
 *   only counts the solid layer once its structure is destroyed, but a tank cannot stand on an
 *   intact structure, so multiplying both is equivalent.
 */
object TerrainGridBuilder {

    /**
     * Build a TerrainGrid from tile layers and map metadata.
     *
     * @param layers The tile layers (base + solid picture indices).
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
        val world = metadata.world
        val tiles = Array(size) { i -> tileFor(world, layers.base.getOrElse(i) { -1 }, layers.solid.getOrElse(i) { -1 }) }

        return TerrainGrid.fromTiles(w, h, tiles)
    }

    /** The terrain of one tile from its base and solid pictures (see the class comment). */
    fun tileFor(world: TankArenaWorld, baseIdx: Int, solidIdx: Int): TerrainTile {
        val baseRow = PictureCatalog.legacyRow(world, baseIdx)
        val typeIdx = if (baseRow == null || PictureCatalog.isNothing(baseRow.type)) solidIdx else baseIdx
        val speed = PictureCatalog.resolve(world, baseIdx).speedMultiplier *
            PictureCatalog.resolve(world, solidIdx).speedMultiplier
        return PictureCatalog.resolve(world, typeIdx).copy(speedMultiplier = speed)
    }
}

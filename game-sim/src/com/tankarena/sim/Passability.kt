package com.tankarena.sim

import com.tankarena.content.CanonicalMapDefinition
import com.tankarena.content.LEGACY_TILE_SIZE

/**
 * Tile-based passability grid used by the simulation for tank and projectile collisions.
 *
 * A tile is solid if its index in [CanonicalMapDefinition.layers].solid is `>= 0`.
 * Decorative top-layer tiles (trees, grass overlays, etc.) are intentionally left
 * passable for now; that distinction will be revisited when broader object families land.
 */
class Passability(
    val widthTiles: Int,
    val heightTiles: Int,
    private val solid: BooleanArray,
) {
    val widthPixels: Int = widthTiles * LEGACY_TILE_SIZE
    val heightPixels: Int = heightTiles * LEGACY_TILE_SIZE

    fun isSolidTile(tileX: Int, tileY: Int): Boolean {
        if (tileX < 0 || tileY < 0 || tileX >= widthTiles || tileY >= heightTiles) return true
        return solid[tileX + tileY * widthTiles]
    }

    fun isSolidPixel(x: Int, y: Int): Boolean {
        val tileX = floorDivTile(x)
        val tileY = floorDivTile(y)
        return isSolidTile(tileX, tileY)
    }

    fun isAabbBlocked(centerX: Int, centerY: Int, halfW: Int, halfH: Int): Boolean {
        val left = centerX - halfW
        val right = centerX + halfW
        val top = centerY - halfH
        val bottom = centerY + halfH
        if (isSolidPixel(left, top)) return true
        if (isSolidPixel(right, top)) return true
        if (isSolidPixel(left, bottom)) return true
        if (isSolidPixel(right, bottom)) return true
        if (isSolidPixel(centerX, centerY)) return true
        return false
    }

    private fun floorDivTile(value: Int): Int {
        return if (value >= 0) value / LEGACY_TILE_SIZE
        else -((-value + LEGACY_TILE_SIZE - 1) / LEGACY_TILE_SIZE)
    }

    companion object {
        fun fromMap(map: CanonicalMapDefinition): Passability {
            val w = map.metadata.widthTiles
            val h = map.metadata.heightTiles
            val solid = BooleanArray(w * h)
            val layer = map.layers.solid
            for (i in 0 until (w * h)) {
                solid[i] = layer.getOrElse(i) { -1 } >= 0
            }
            return Passability(widthTiles = w, heightTiles = h, solid = solid)
        }
    }
}

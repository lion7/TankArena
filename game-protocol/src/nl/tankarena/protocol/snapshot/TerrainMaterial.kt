package nl.tankarena.protocol.snapshot

import kotlinx.serialization.Serializable

/**
 * Terrain material classification derived from legacy picture type constants.
 * See docs/game/terrain.md and src/include/define.h.
 */
@Serializable
enum class TerrainMaterial {
    NORMAL,
    WATER,
    OIL,
    BUSHES,
    RUNWAY,
    RAILS,
    BRIDGE,
    PIT_BIG,
    PIT_SMALL,
    LAVA,
    FUEL_DUMP,
    ABOX,
    FIELD,
    HELISITE,
    SHELTER,
    ROAD,
    CRATER,
    LIGHT,
    STATION,
    WARP_IN,
    WARP_OUT,
    RAMP_SMALL,
    RAMP_BIG,
    BARRIER,
    START_CHECK,
}

/**
 * Which part of a pit a [TerrainMaterial.PIT_BIG] / [TerrainMaterial.PIT_SMALL] tile is, named after
 * the legacy `PIT_*` / `S_PIT_*` picture types: an edge or corner tile only swallows a tank on the
 * side that faces the pit's centre (`in_pit()` in src/check/pit.c).
 */
@Serializable
enum class PitShape { CENTER, TOP, BOTTOM, LEFT, RIGHT, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/**
 * Per-tile terrain info: material classification and speed multiplier.
 */
@Serializable
data class TerrainTile(
    val material: TerrainMaterial = TerrainMaterial.NORMAL,
    val speedMultiplier: Float = 1.0f,
    val pitShape: PitShape? = null,
    /** Legacy `NOTHING`: neither picture has a type (bare ground, where desert vehicles raise dust). */
    val bare: Boolean = false,
    /** Legacy `T_*` / `ROTATE_*` base pictures: a conveyor or turntable (src/world/transp.c). */
    val transport: Transport? = null,
)

/** Legacy transportation(): which way a conveyor carries things, or which way a turntable turns. */
@Serializable
enum class Transport { LEFT, RIGHT, UP, DOWN, ROTATE_LEFT, ROTATE_RIGHT }

/**
 * Terrain grid: O(1) lookup of terrain info at any tile coordinate.
 * Backed by a flat array indexed as [x + y * width].
 */
class TerrainGrid(
    private val width: Int,
    private val height: Int,
    private val data: Array<TerrainTile>,
) {
    init {
        require(data.size == width * height) {
            "data size ${data.size} != width $width * height $height"
        }
    }

    val tileWidth: Int get() = width
    val tileHeight: Int get() = height

    /** Replace a tile (legacy: a destroyed bridge leaves water). */
    fun update(tileX: Int, tileY: Int, tile: TerrainTile) {
        if (tileX < 0 || tileX >= width || tileY < 0 || tileY >= height) return
        data[tileX + tileY * width] = tile
    }

    /** Return the terrain tile at tile (x, y). Returns default NORMAL for out-of-bounds. */
    fun at(tileX: Int, tileY: Int): TerrainTile {
        if (tileX < 0 || tileX >= width || tileY < 0 || tileY >= height) {
            return TerrainTile()
        }
        return data[tileX + tileY * width]
    }

    /** Return the terrain tile at world pixel (px, py), given a tile size in pixels. */
    fun atPixel(px: Int, py: Int, tileSize: Int): TerrainTile {
        val tileX = px / tileSize
        val tileY = py / tileSize
        return at(tileX, tileY)
    }

    /** Return the material at tile (x, y). Returns NORMAL for out-of-bounds. */
    fun materialAt(tileX: Int, tileY: Int): TerrainMaterial = at(tileX, tileY).material

    /** Return the speed multiplier at tile (x, y). Returns 1.0 for out-of-bounds. */
    fun speedAt(tileX: Int, tileY: Int): Float = at(tileX, tileY).speedMultiplier

    companion object {
        /** Build a TerrainGrid from pre-computed tile data. */
        fun fromTiles(width: Int, height: Int, tiles: Array<TerrainTile>): TerrainGrid {
            return TerrainGrid(width, height, tiles)
        }

        /** Build an empty (all NORMAL) grid. */
        fun empty(width: Int, height: Int): TerrainGrid {
            return TerrainGrid(width, height, Array(width * height) { TerrainTile() })
        }
    }
}

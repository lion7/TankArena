package com.tankarena.content

import com.tankarena.protocol.snapshot.PitShape
import com.tankarena.protocol.snapshot.TerrainMaterial
import com.tankarena.protocol.snapshot.TerrainTile
import com.tankarena.protocol.snapshot.Transport

/**
 * The gameplay columns of one `pictureinfotype` row (src/include/types.h): its legacy `type`
 * constant name, `speed`, `str[3]` (strength against normal hits, crashes and flames; 1000 is
 * indestructible), `exptype` (effect when destroyed) and `linked` (`UP+DOWN+LEFT+RIGHT+STOP` flags).
 */
data class LegacyPictureTerrain(
    val type: String,
    val speed: Float,
    val strength: List<Int> = listOf(0, 0, 0),
    val explosion: String = "RUIN",
    val links: String = "NONE",
)

/**
 * Maps (world, picture-index) → terrain properties (material + speed multiplier).
 *
 * The rows come from [GeneratedLegacyPictureTerrain], which is generated from the pc0–pc3
 * arrays in src/data/pictures.c; this object only translates the legacy type constants
 * (src/include/define.h) into [TerrainMaterial]s.
 *
 * Defaults: speed = 1.0, material = NORMAL (unknown or negative index).
 */
object PictureCatalog {

    /** The legacy `pictureinfotype` row behind a picture index, or null for an unknown index. */
    fun legacyRow(world: TankArenaWorld, pictureIndex: Int): LegacyPictureTerrain? {
        if (pictureIndex < 0) return null
        return rowsFor(world).getOrNull(pictureIndex)
    }

    /** Number of pictures in [world]'s table: valid indices are `0 until pictureCount(world)`. */
    fun pictureCount(world: TankArenaWorld): Int = rowsFor(world).size

    /**
     * Resolve terrain info for a picture index in the given world.
     * Returns default NORMAL / 1.0 for unknown indices.
     */
    fun resolve(world: TankArenaWorld, pictureIndex: Int): TerrainTile {
        val row = legacyRow(world, pictureIndex) ?: return TerrainTile()
        return TerrainTile(
            material = materialFor(row.type),
            speedMultiplier = row.speed,
            pitShape = pitShapeFor(row.type),
        )
    }

    /** Legacy picture names of a picture index: intact, destroyed and (rarely) a third variant. */
    fun pictureNames(world: TankArenaWorld, pictureIndex: Int): List<String>? {
        if (pictureIndex < 0) return null
        val records = when (world) {
            TankArenaWorld.DESERT -> GeneratedLegacyPictureCatalog.desertRecords
            TankArenaWorld.TEMPERATE -> GeneratedLegacyPictureCatalog.temperateRecords
            TankArenaWorld.CITY -> GeneratedLegacyPictureCatalog.cityRecords
            TankArenaWorld.NIGHT -> GeneratedLegacyPictureCatalog.nightRecords
        }
        return records.getOrNull(pictureIndex)?.nameVariants
    }

    private fun rowsFor(world: TankArenaWorld): List<LegacyPictureTerrain> = when (world) {
        TankArenaWorld.DESERT -> GeneratedLegacyPictureTerrain.desert
        TankArenaWorld.TEMPERATE -> GeneratedLegacyPictureTerrain.temperate
        TankArenaWorld.CITY -> GeneratedLegacyPictureTerrain.city
        TankArenaWorld.NIGHT -> GeneratedLegacyPictureTerrain.night
    }

    /** Legacy transportation(): conveyors (`T_LEFT` ...) and turntables (`ROTATE_L`, `ROTATE_R`). */
    fun transportFor(type: String): Transport? = when (type) {
        "T_LEFT" -> Transport.LEFT
        "T_RIGHT" -> Transport.RIGHT
        "T_UP" -> Transport.UP
        "T_DOWN" -> Transport.DOWN
        "ROTATE_L" -> Transport.ROTATE_LEFT
        "ROTATE_R" -> Transport.ROTATE_RIGHT
        else -> null
    }

    /** True for the legacy `NOTHING` type (-1), which makes `get_type()` fall through to the next layer. */
    fun isNothing(type: String): Boolean = type == "NOTHING"

    internal fun materialFor(type: String): TerrainMaterial = when {
        type == "WATER" -> TerrainMaterial.WATER
        type == "OIL" -> TerrainMaterial.OIL
        type == "BUSHES" || type == "BUSH_SIDE" -> TerrainMaterial.BUSHES
        type == "RUNWAY" -> TerrainMaterial.RUNWAY
        type == "ROAD" -> TerrainMaterial.ROAD
        type.startsWith("RAILS_") -> TerrainMaterial.RAILS
        type.startsWith("BRIDGE_") -> TerrainMaterial.BRIDGE
        type.startsWith("PIT_") -> TerrainMaterial.PIT_BIG
        type.startsWith("S_PIT_") -> TerrainMaterial.PIT_SMALL
        type == "LAVA_PUT" -> TerrainMaterial.LAVA
        type == "FUEL_DUMP" -> TerrainMaterial.FUEL_DUMP
        type == "ABOX" -> TerrainMaterial.ABOX
        type.startsWith("FIELD_") -> TerrainMaterial.FIELD
        type == "HELISITE" -> TerrainMaterial.HELISITE
        type == "SHELTER" -> TerrainMaterial.SHELTER
        type == "PLOEF" || type == "ROAD_KRAT" || type == "NORM_KRAT" -> TerrainMaterial.CRATER
        type == "LIGHT_SRC" -> TerrainMaterial.LIGHT
        type == "STATION" -> TerrainMaterial.STATION
        type == "WARP_IN" -> TerrainMaterial.WARP_IN
        type == "WARP_OUT" -> TerrainMaterial.WARP_OUT
        type.startsWith("S_RAMP_") -> TerrainMaterial.RAMP_SMALL
        type.startsWith("B_RAMP_") -> TerrainMaterial.RAMP_BIG
        type.startsWith("BARRIER_") -> TerrainMaterial.BARRIER
        type == "START" || type.startsWith("CHECK_") -> TerrainMaterial.START_CHECK
        else -> TerrainMaterial.NORMAL
    }

    internal fun pitShapeFor(type: String): PitShape? = when (type.removePrefix("S_")) {
        "PIT_C" -> PitShape.CENTER
        "PIT_T" -> PitShape.TOP
        "PIT_B" -> PitShape.BOTTOM
        "PIT_L" -> PitShape.LEFT
        "PIT_R" -> PitShape.RIGHT
        "PIT_TL" -> PitShape.TOP_LEFT
        "PIT_TR" -> PitShape.TOP_RIGHT
        "PIT_BL" -> PitShape.BOTTOM_LEFT
        "PIT_BR" -> PitShape.BOTTOM_RIGHT
        else -> null
    }
}

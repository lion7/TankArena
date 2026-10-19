package com.tankarena.sim

import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.protocol.snapshot.TerrainMaterial
import com.tankarena.protocol.snapshot.PitShape
import com.tankarena.protocol.snapshot.TerrainTile

// What the ground does to vehicles: speed, water, lava, pits (src/tanks/control.c, src/check/pit.c).

/**
 * Apply terrain effects (lava damage, pit kill) to all tanks, and set the per-tank terrain
 * speed multiplier for the next motion step. Water only slows a tank down (its speed is in
 * the picture table); the legacy game never kills a tank for driving into it.
 */
internal fun Match.applyTerrainEffects() {
    val tanks = objects.all.filterIsInstance<Tank>()
    for (tank in tanks) {
        if (tank.armor <= 0) continue
        val tileX = tank.positionX / LEGACY_TILE_SIZE
        val tileY = tank.positionY / LEGACY_TILE_SIZE
        val tile = terrainGrid.at(tileX, tileY)

        tank.setTerrainSpeedMultiplier(tile.speedMultiplier)
        tank.setUnderground(runway = tile.material == TerrainMaterial.RUNWAY, water = tile.material == TerrainMaterial.WATER)

        // Legacy underground_effects(): HT_LAVA hits for 2 every 10 ticks while the tank stays
        // in lava; aircraft overhead are not touched.
        tank.stepLava(inLava = tile.material == TerrainMaterial.LAVA && tank.height < 2)
        if (tank.height > 1) continue

        val subX = (tank.positionX % LEGACY_TILE_SIZE + LEGACY_TILE_SIZE) % LEGACY_TILE_SIZE
        val subY = (tank.positionY % LEGACY_TILE_SIZE + LEGACY_TILE_SIZE) % LEGACY_TILE_SIZE
        if (inPit(tile, subX, subY)) {
            tank.startFalling()
        }
    }
}

/**
 * Legacy `in_pit()` (src/check/pit.c): the centre tile of a pit always swallows a tank, while
 * an edge or corner tile only does so once the tank's centre is past the rim on the side
 * facing the pit. [subX]/[subY] are the tank centre's offset inside its tile.
 */
internal fun Match.inPit(tile: TerrainTile, subX: Int, subY: Int): Boolean {
    val shape = tile.pitShape ?: return false
    val (low, high) = when (tile.material) {
        TerrainMaterial.PIT_BIG -> 8 to 24
        TerrainMaterial.PIT_SMALL -> 14 to 18
        else -> return false
    }
    return when (shape) {
        PitShape.CENTER -> true
        PitShape.TOP -> subY > low
        PitShape.BOTTOM -> subY < high
        PitShape.LEFT -> subX > low
        PitShape.RIGHT -> subX < high
        PitShape.TOP_LEFT -> subX > low && subY > low
        PitShape.TOP_RIGHT -> subX < high && subY > low
        PitShape.BOTTOM_LEFT -> subX > low && subY < high
        PitShape.BOTTOM_RIGHT -> subX < high && subY < high
    }
}

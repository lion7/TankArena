package com.tankarena.sim

import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.protocol.snapshot.TerrainMaterial
import com.tankarena.protocol.snapshot.PitShape
import com.tankarena.protocol.snapshot.TerrainTile
import com.tankarena.protocol.snapshot.Transport
import kotlin.math.roundToInt

// What the ground does to vehicles: speed, water, lava, pits (src/tanks/control.c, src/check/pit.c).

/**
 * Apply terrain effects (lava damage, pit kill) to all tanks, and set the per-tank terrain
 * speed multiplier for the next motion step. Water only slows a tank down (its speed is in
 * the picture table); the legacy game never kills a tank for driving into it.
 */
/**
 * Legacy transportation() (src/world/transp.c), first in a step: everything on or near the ground
 * (below height 2) on a conveyor tile moves 60 px/s along it; on a turntable it turns a full circle
 * a second around the tile, its heading unchanged. A vehicle that would end up in another stays.
 */
internal fun Match.applyTransport() {
    fun carried(x: Float, y: Float): Pair<Float, Float>? {
        val tileX = Math.floorDiv(x.toInt(), LEGACY_TILE_SIZE)
        val tileY = Math.floorDiv(y.toInt(), LEGACY_TILE_SIZE)
        val transport = terrainGrid.at(tileX, tileY).transport ?: return null
        val shift = TRANSPORT_SPEED * STEP_SECONDS
        return when (transport) {
            Transport.LEFT -> (x - shift) to y
            Transport.RIGHT -> (x + shift) to y
            Transport.UP -> x to (y - shift)
            Transport.DOWN -> x to (y + shift)
            Transport.ROTATE_LEFT, Transport.ROTATE_RIGHT -> {
                val pivotX = tileX * LEGACY_TILE_SIZE + 16f
                val pivotY = tileY * LEGACY_TILE_SIZE + 16f
                val a = (if (transport == Transport.ROTATE_LEFT) 2 else -2) * Math.PI * STEP_SECONDS
                val dx = x - pivotX
                val dy = y - pivotY
                val cos = com.tankarena.core.DetMath.cos(a).toFloat()
                val sin = com.tankarena.core.DetMath.sin(a).toFloat()
                (dx * cos + dy * sin + pivotX) to (dy * cos - dx * sin + pivotY)
            }
        }
    }
    for (tank in allTanks) {
        if (tank.height >= 2 || tank.wreckGone) continue
        val (x, y) = tank.exactPosition
        val (nx, ny) = carried(x, y) ?: continue
        val blocked = allTanks.any { other ->
            other !== tank && !other.wreckGone && kotlin.math.abs(other.height - tank.height) < 4 &&
                withinRadius(nx.roundToInt(), ny.roundToInt(), other.positionX, other.positionY, tank.spec.radius + other.spec.radius)
        }
        if (!blocked) tank.carryTo(nx, ny)
    }
    for (soldier in soldiers) {
        if (soldier.removed) continue
        val (x, y) = soldier.exactPosition
        carried(x, y)?.let { (nx, ny) -> soldier.carryTo(nx, ny) }
    }
}

/** Legacy transportation(): 60 px/s along a conveyor. */
private const val TRANSPORT_SPEED = 60f

/** Legacy get_underground()/underground_effects() for every vehicle, after it has moved. */
internal fun Match.applyUndergroundEffects() {
    val desert = mapMetadata.world == com.tankarena.content.TankArenaWorld.DESERT
    for (tank in allTanks) {
        if (tank.armor <= 0) continue
        tank.stepUnderground(terrainGrid.at(Math.floorDiv(tank.positionX, LEGACY_TILE_SIZE), Math.floorDiv(tank.positionY, LEGACY_TILE_SIZE)), desert)
    }
}

internal fun Match.applyTerrainEffects() {
    val tanks = objects.all.filterIsInstance<Tank>()
    for (tank in tanks) {
        if (tank.armor <= 0) continue
        val tileX = tank.positionX / LEGACY_TILE_SIZE
        val tileY = tank.positionY / LEGACY_TILE_SIZE
        val tile = terrainGrid.at(tileX, tileY)

        tank.setTerrainSpeedMultiplier(tile.speedMultiplier)
        tank.setUnderground(runway = tile.material == TerrainMaterial.RUNWAY, water = tile.material == TerrainMaterial.WATER)

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

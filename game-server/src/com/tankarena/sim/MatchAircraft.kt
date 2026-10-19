package com.tankarena.sim

import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.TerrainGridBuilder
import com.tankarena.protocol.snapshot.EffectKind
import com.tankarena.protocol.snapshot.GameEvent

// B52 bombers and the zeppelin (src/objects/b52.c, zeppelin.c).

/** Legacy control_zeppelin(): drift west at 10 px/s and come back in from the east. */
internal fun Match.stepZeppelins() {
    for (zep in objects.all.filterIsInstance<Zeppelin>()) {
        if (!zep.alive) continue
        zep.exactX -= ZEPPELIN_SPEED * 0.01f
        if (zep.exactX - LEGACY_TILE_SIZE / 2 < -4 * LEGACY_TILE_SIZE) {
            zep.exactX = ((mapMetadata.widthTiles + 4) * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2).toFloat()
            zep.positionY = airRandom.nextInt(worldHeightPixels) + LEGACY_TILE_SIZE / 2
        }
        zep.positionX = Math.round(zep.exactX)
        zep.body.position = ScenePoint(
            (zep.positionX - ZEPPELIN_FOOTPRINT / 2).toFloat(),
            (zep.positionY - ZEPPELIN_FOOTPRINT / 2).toFloat(),
        )
        if (--zep.frameCooldownTicks <= 0) {
            zep.frame = zep.frame xor 1
            zep.frameCooldownTicks = 100
        }
    }
}

/** Legacy prepare_b52(): a bomber waits 500..1000 ticks before it starts its take-off run. */
internal fun Match.prepareB52s() {
    b52s = objects.all.filterIsInstance<B52>()
    for (b52 in b52s) {
        b52.moveTo(b52.positionX.toFloat(), b52.positionY.toFloat())
        b52.speedCooldownTicks = 500 + airRandom.nextInt(500)
    }
}

internal fun Match.stepB52s() {
    for (b52 in b52s) {
        if (!b52.alive) continue
        when (b52.phase) {
            B52.Phase.LIFTING -> liftB52(b52)
            B52.Phase.FLYING -> flyB52(b52)
            B52.Phase.CRASHING -> crashB52(b52)
            // Legacy control_b52_wreck(): the wreck burns for 300 ticks.
            B52.Phase.WRECK -> if (b52.explosionCooldownTicks > 0) {
                b52.explosionCooldownTicks -= 1
                pendingEvents += GameEvent.Effect(EffectKind.EXPLOSION, b52.positionX - 20 + airRandom.nextInt(40), b52.positionY - 20 + airRandom.nextInt(40))
            }
        }
    }
}

/** Count a legacy `tinit()` timer down; true when it has run out. */
internal fun Match.ready(ticks: Int): Boolean = ticks <= 0

/** Legacy lift_b52(): speed up by 1 px/s every 80 ticks to 25, and climb every 60 ticks above 20 px/s. */
internal fun Match.liftB52(b52: B52) {
    controlB52(b52)
    if (b52.phase != B52.Phase.LIFTING) return
    b52.speedCooldownTicks -= 1
    b52.heightCooldownTicks -= 1
    if (ready(b52.speedCooldownTicks) && b52.speed < 25) {
        b52.speed += 1
        b52.speedCooldownTicks = 80
    }
    if (ready(b52.heightCooldownTicks) && b52.speed > 20) {
        b52.height += 1
        b52.heightCooldownTicks = 60
    }
    if (b52.height == B52.CRUISE_HEIGHT) b52.phase = B52.Phase.FLYING
}

/**
 * Legacy control_b52(): fly on along the heading; collide with vehicles, trains and other
 * bombers at about the same height (all of them destroyed); low down, a building is fatal.
 */
internal fun Match.controlB52(b52: B52) {
    // Legacy control_b52(): on night maps the navigation lights blink, 10 ticks on, 200..300 off.
    if (mapMetadata.night) {
        b52.lightCooldownTicks -= 1
        if (ready(b52.lightCooldownTicks)) {
            b52.lightOn = !b52.lightOn
            b52.lightCooldownTicks = if (b52.lightOn) 10 else 200 + airRandom.nextInt(100)
        }
    }
    val (fx, fy) = com.tankarena.core.LegacyDirections.unitVector(b52.heading * 4)
    b52.moveTo(b52.exactX + fx * b52.speed * 0.01f, b52.exactY + fy * b52.speed * 0.01f)
    val x = b52.positionX
    val y = b52.positionY
    val r = B52.RADIUS
    fun crash(otherX: Int, otherY: Int) {
        val mx = (x + otherX) / 2
        val my = (y + otherY) / 2
        pendingEvents += GameEvent.Effect(EffectKind.EXPLOSION, mx, my)
        pendingEvents += GameEvent.Sound(com.tankarena.protocol.snapshot.SoundKind.EXPLODE, mx, my)
        hitB52(b52, StructureGrid.INDESTRUCTIBLE)
    }
    for (tank in allTanks) {
        if (kotlin.math.abs(tank.height - b52.height) >= 2) continue
        if (!withinRadius(tank.positionX, tank.positionY, x, y, r + tank.spec.radius - 1)) continue
        tank.queueDestroy()
        crash(tank.positionX, tank.positionY)
    }
    if (kotlin.math.abs(1 - b52.height) < 2) {
        for (car in trains) {
            if (!car.alive || !withinRadius(car.positionX, car.positionY, x, y, r + Train.RADIUS - 1)) continue
            hitTrain(car, StructureGrid.INDESTRUCTIBLE)
            crash(car.positionX, car.positionY)
        }
    }
    for (other in b52s) {
        if (other === b52 || !other.alive || kotlin.math.abs(other.height - b52.height) >= 2) continue
        if (!withinRadius(other.positionX, other.positionY, x, y, 2 * r - 1)) continue
        hitB52(other, StructureGrid.INDESTRUCTIBLE)
        crash(other.positionX, other.positionY)
    }
    if (b52.height <= 1) {
        for (soldier in soldiers) if (!soldier.removed && withinRadius(soldier.x, soldier.y, x, y, r + SOLDIER_RADIUS_PX - 1)) removeSoldier(soldier)
        soldiers.removeAll { it.removed }
        for (mine in objects.all) {
            if (mine is Mine && mine.isActive && withinRadius(mine.positionX, mine.positionY, x, y, r + MINE_RADIUS_PX - 1)) mine.detonateOn(null)
        }
    }
    if (b52.height <= 3) {
        val tileX = Math.floorDiv(x, LEGACY_TILE_SIZE)
        val tileY = Math.floorDiv(y, LEGACY_TILE_SIZE)
        val layer = structures.blastLayerAt(tileX, tileY)
        if (layer != null) {
            pendingEvents += GameEvent.Effect(EffectKind.EXPLOSION, x, y)
            hitStructure(tileX, tileY, layer, HitKind.NORMAL, StructureGrid.INDESTRUCTIBLE, EffectKind.EXPLOSION)
            hitB52(b52, StructureGrid.INDESTRUCTIBLE)
        }
    }
}

/**
 * Legacy fly_b52(): leaving the map, the bomber comes back over a random edge on a random
 * heading; every 650 ticks it opens its bay (four frames of 30 ticks) and drops ten bombs of
 * power 20, which hit a building or a vehicle under them, or else may leave a crater.
 */
internal fun Match.flyB52(b52: B52) {
    val left = b52.positionX - LEGACY_TILE_SIZE / 2
    val top = b52.positionY - LEGACY_TILE_SIZE / 2
    controlB52(b52)
    if (b52.phase != B52.Phase.FLYING) return
    val b = LEGACY_TILE_SIZE
    if (left > (mapMetadata.widthTiles + 3) * b || left < -3 * b || top > (mapMetadata.heightTiles + 3) * b || top < -3 * b) {
        resetB52(b52)
        return
    }
    b52.bombCooldownTicks -= 1
    if (!ready(b52.bombCooldownTicks)) return
    b52.bomb = (b52.bomb + 1) % 5
    b52.bombCooldownTicks = if (b52.bomb == 4) 500 else 30
    if (b52.bomb != 4) return
    b52.bombCount += 1
    for (offset in B52_BOMBS) {
        val (dx, dy) = rotate(b52.heading * 4, offset)
        val x = b52.positionX + dx
        val y = b52.positionY + dy
        if (bombStructure(x, y) || bombObjects(b52, x, y)) continue
        pendingEvents += GameEvent.Effect(EffectKind.EXPLOSION, x, y)
        if (airRandom.nextInt(10) == 0) {
            val tileX = Math.floorDiv(x, LEGACY_TILE_SIZE)
            val tileY = Math.floorDiv(y, LEGACY_TILE_SIZE)
            if (structures.placeCrater(tileX, tileY)) {
                val i = tileX + tileY * mapMetadata.widthTiles
                terrainGrid.update(tileX, tileY, TerrainGridBuilder.tileFor(mapMetadata.world, structures.base[i], structures.solid[i]))
            }
        }
    }
}

/** Legacy check_wall_hit_point(..., HT_BOMB, 20): a bomb on an opaque pixel of a structure hits it. */
internal fun Match.bombStructure(x: Int, y: Int): Boolean {
    val layer = structures.hitLayerAt(x, y) ?: return false
    pendingEvents += GameEvent.Effect(EffectKind.EXPLOSION, x, y)
    pendingEvents += GameEvent.Sound(com.tankarena.protocol.snapshot.SoundKind.EXPLODE, x, y)
    hitStructure(Math.floorDiv(x, LEGACY_TILE_SIZE), Math.floorDiv(y, LEGACY_TILE_SIZE), layer, HitKind.NORMAL, B52_BOMB_POWER, EffectKind.EXPLOSION)
    return true
}

/**
 * Legacy check_obj_hit_point(..., HT_BOMB, 20): a vehicle under the bomb takes it (and stops
 * it); soldiers, bonuses, trains and low bombers within their radius are hit as well.
 */
internal fun Match.bombObjects(bomber: B52, x: Int, y: Int): Boolean {
    fun hitAt(px: Int, py: Int) {
        pendingEvents += GameEvent.Effect(EffectKind.EXPLOSION, px, py)
        pendingEvents += GameEvent.Sound(com.tankarena.protocol.snapshot.SoundKind.EXPLODE, px, py)
    }
    for (tank in allTanks) {
        if (!withinRadius(tank.positionX, tank.positionY, x, y, tank.spec.radius)) continue
        hitAt(x, y)
        tank.queueDamage(B52_BOMB_POWER)
        return true
    }
    for (soldier in soldiers) if (!soldier.removed && withinRadius(soldier.x, soldier.y, x, y, SOLDIER_RADIUS_PX - 1)) {
        hitAt(x, y)
        removeSoldier(soldier)
    }
    for (bonus in bonuses) if (!bonus.removed && withinRadius(bonus.x, bonus.y, x, y, BONUS_RADIUS_PX - 1)) destroyBonus(bonus)
    for (car in trains) if (car.alive && withinRadius(car.positionX, car.positionY, x, y, Train.RADIUS - 1)) {
        hitAt(x, y)
        hitTrain(car, B52_BOMB_POWER)
    }
    for (other in b52s) if (other !== bomber && other.alive && other.height < 8 && withinRadius(other.positionX, other.positionY, x, y, B52.RADIUS - 1)) {
        hitAt(x, y)
        hitB52(other, B52_BOMB_POWER)
    }
    soldiers.removeAll { it.removed }
    bonuses.removeAll { it.removed }
    return false
}

/** Legacy reset_b52(): back in over a random edge on a random heading. */
internal fun Match.resetB52(b52: B52) {
    val b = LEGACY_TILE_SIZE
    b52.heading = airRandom.nextInt(4)
    val (left, top) = when (b52.heading) {
        0 -> airRandom.nextInt(worldWidthPixels) to (mapMetadata.heightTiles + 2) * b
        1 -> -2 * b to airRandom.nextInt(worldHeightPixels)
        2 -> airRandom.nextInt(worldWidthPixels) to -2 * b
        else -> (mapMetadata.widthTiles + 2) * b to airRandom.nextInt(worldHeightPixels)
    }
    b52.moveTo((left + b / 2).toFloat(), (top + b / 2).toFloat())
}

/** Legacy hit_b52() and hit_b52_wreck(). */
internal fun Match.hitB52(b52: B52, power: Int) {
    if (!b52.alive) return
    when (b52.phase) {
        B52.Phase.WRECK -> if (b52.explosionCooldownTicks <= 0) {
            repeat(10) {
                pendingEvents += GameEvent.Effect(EffectKind.EXPLOSION, b52.positionX - 20 + airRandom.nextInt(40), b52.positionY - 20 + airRandom.nextInt(40))
            }
            b52.alive = false
        }
        else -> {
            b52.armor -= power
            if (b52.armor <= 0) {
                b52.armor = 0
                b52.bomb = 4
                b52.phase = B52.Phase.CRASHING
            }
        }
    }
}

/**
 * Legacy crash_b52(): a shot-down bomber flies on, sinking one step every 60 ticks with an
 * engine burning, and explodes the last bit of the way; on the ground it is a burning wreck
 * and its goal share counts.
 */
internal fun Match.crashB52(b52: B52) {
    controlB52(b52)
    b52.heightCooldownTicks -= 1
    if (ready(b52.heightCooldownTicks)) {
        b52.height -= 1
        b52.heightCooldownTicks = 60
    }
    if (b52.height <= 2) {
        pendingEvents += GameEvent.Effect(EffectKind.EXPLOSION, b52.positionX - 20 + airRandom.nextInt(40), b52.positionY - 20 + airRandom.nextInt(40))
    }
    if (b52.height <= 0) {
        b52.height = 0
        b52.phase = B52.Phase.WRECK
        modifyGoalCounter(b52.goalContribution)
        b52.goalContribution = 0
        b52.explosionCooldownTicks = 300
        return
    }
    b52.explosionCooldownTicks -= 1
    if (ready(b52.explosionCooldownTicks)) {
        val (ex, ey) = rotate(b52.heading * 4, B52_ENGINES[airRandom.nextInt(4)])
        pendingEvents += GameEvent.Effect(EffectKind.EXPLOSION, b52.positionX + ex, b52.positionY + ey)
        b52.explosionCooldownTicks = 40
    }
}

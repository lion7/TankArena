package com.tankarena.sim

import com.tankarena.core.DetMath
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.protocol.snapshot.TerrainMaterial
import com.tankarena.protocol.snapshot.EffectKind
import com.tankarena.protocol.snapshot.GameEvent

// Bonuses, soldiers and A-bombs, and vehicles that overlap (src/objects/bonus.c, man.c, abomb.c,
// src/check/overlap.c).

/** A tile of the map's bonus layer (`mapm.b`, a signed char: 255 is -1, no bonus). */
internal fun Match.layerBonus(tileX: Int, tileY: Int): Int {
    val value = layers?.bonusLayer?.getOrNull(tileX + tileY * mapMetadata.widthTiles) ?: return -1
    return if (value > 127) value - 256 else value
}

/** Legacy create_all_bonusses(): without random bonuses, the bonus layer's bonuses lie on every open tile. */
internal fun Match.createAllBonuses() {
    if (mapMetadata.randomBonus) return
    for (tileY in 0 until mapMetadata.heightTiles) {
        for (tileX in 0 until mapMetadata.widthTiles) {
            if (!structures.isStanding(tileX, tileY)) makeBonus(tileX, tileY, layerBonus(tileX, tileY))
        }
    }
}

/** Legacy create_bonus(): a destroyed building leaves a random bonus (12.5 % of the time) or the layer's. */
internal fun Match.createBonus(tileX: Int, tileY: Int) {
    if (!mapMetadata.randomBonus) {
        makeBonus(tileX, tileY, layerBonus(tileX, tileY))
        return
    }
    val roll = objectRandom.nextInt(10000)
    makeBonus(tileX, tileY, RANDOM_BONUS_THRESHOLDS.indexOfFirst { roll < it })
}

/** Legacy make_bonus(): a bonus within 4 px of the tile's centre. */
internal fun Match.makeBonus(tileX: Int, tileY: Int, type: Int) {
    if (type < 0) return
    val x = tileX * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2 - 4 + objectRandom.nextInt(8)
    val y = tileY * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2 - 4 + objectRandom.nextInt(8)
    bonuses += Bonus(nextActorId++, x, y, type)
}

/** Legacy hit_bonus(): anything that hits a bonus blows it up. */
internal fun Match.destroyBonus(bonus: Bonus) {
    if (bonus.removed) return
    bonus.removed = true
    pendingEvents += GameEvent.Effect(EffectKind.EXPLOSION, bonus.x, bonus.y)
}

/** Legacy remove_man(): a soldier who goes leaves blood and a scream, however he goes. */
internal fun Match.removeSoldier(soldier: Soldier) {
    if (soldier.removed) return
    soldier.removed = true
    pendingEvents += GameEvent.Effect(EffectKind.BLOOD, soldier.x, soldier.y)
    pendingEvents += GameEvent.Sound(com.tankarena.protocol.snapshot.SoundKind.SCREAM, soldier.x, soldier.y)
}

/** Legacy nearest_target(): the vehicle closest to ([x], [y]) within 15 px, which a soldier follows. */
internal fun Match.vehicleNear(x: Int, y: Int): Tank? = allTanks
    .filter { withinRadius(it.positionX, it.positionY, x, y, ROCKET_TARGET_RADIUS_PX) }
    .minByOrNull { (it.positionX - x).toLong() * (it.positionX - x) + (it.positionY - y).toLong() * (it.positionY - y) }

internal fun Match.drainSoldierRequests() {
    for (tank in allTanks) {
        for (request in tank.drainSoldierRequests()) {
            soldiers += Soldier(
                id = nextActorId++,
                x = request.x,
                y = request.y,
                targetX = request.targetX,
                targetY = request.targetY,
                target = vehicleNear(request.targetX, request.targetY),
                shotKind = if (request.gunner) WEAPON_CHAIN else WEAPON_FLAME,
                power = request.power,
                owner = tank,
                random = objectRandom,
            )
        }
    }
}

/**
 * Legacy hit_wall(): soldiers of the tile's man layer jump out of a destroyed building, around
 * it, and go for whoever destroyed it (or a random spot near it).
 */
internal fun Match.dropParatroopers(tileX: Int, tileY: Int, by: Tank?) {
    val index = tileX + tileY * mapMetadata.widthTiles
    val count = layers?.manAmountLayer?.getOrNull(index) ?: 0
    if (count <= 0) return
    val hitType = layers?.manTypeLayer?.getOrNull(index) ?: 0
    val left = tileX * LEGACY_TILE_SIZE
    val top = tileY * LEGACY_TILE_SIZE
    repeat(count) {
        val x = left + objectRandom.nextInt(32)
        val y = top + objectRandom.nextInt(32)
        val (targetX, targetY) = if (by == null) {
            left + LEGACY_TILE_SIZE / 2 - 100 + objectRandom.nextInt(200) to top + LEGACY_TILE_SIZE / 2 - 100 + objectRandom.nextInt(200)
        } else {
            by.positionX to by.positionY
        }
        soldiers += Soldier(
            id = nextActorId++,
            x = x,
            y = y,
            targetX = targetX,
            targetY = targetY,
            target = vehicleNear(targetX, targetY),
            shotKind = shotKindForHitType(hitType),
            power = if (hitType == HT_SROCK) 5 else 1,
            owner = null,
            random = objectRandom,
        )
    }
}

/** The projectile a legacy HT_* type stands for, as the man layer stores it. */
internal fun Match.shotKindForHitType(hitType: Int): Int = when (hitType) {
    0 -> WEAPON_MAIN
    2 -> WEAPON_FLAME
    HT_SROCK -> SHOT_SMALL_ROCKET
    else -> WEAPON_CHAIN
}

/**
 * Legacy control_man(): soldiers walk and fire; one who stands on a building (hitting it for
 * 10), in water or in a pit, or who has no rounds left, is gone.
 */
internal fun Match.stepSoldiers() {
    if (soldiers.isEmpty()) return
    for (soldier in soldiers.toList()) {
        if (soldier.removed) continue
        soldier.move()
        val x = soldier.x
        val y = soldier.y
        val tileX = Math.floorDiv(x, LEGACY_TILE_SIZE)
        val tileY = Math.floorDiv(y, LEGACY_TILE_SIZE)
        val onBuilding = structures.solidOpaqueAt(x, y)
        if (onBuilding) hitStructure(tileX, tileY, StructureLayer.SOLID, HitKind.NORMAL, 10, EffectKind.CHAIN)
        val tile = terrainGrid.at(tileX, tileY)
        val sub = { v: Int -> (v % LEGACY_TILE_SIZE + LEGACY_TILE_SIZE) % LEGACY_TILE_SIZE }
        if (soldier.outOfAmmo || onBuilding || tile.material == TerrainMaterial.WATER || inPit(tile, sub(x), sub(y))) {
            removeSoldier(soldier)
            continue
        }
        soldier.fire()?.let { pendingSoldierShots += soldier to it }
    }
    soldiers.removeAll { it.removed }
}

internal fun Match.drainAbombRequests() {
    for (tank in allTanks) {
        val request = tank.drainAbombRequest() ?: continue
        abombs += Abomb(nextActorId++, request.x, request.y, request.countdown, tank)
    }
}

/**
 * Legacy control_abomb(): every blast step wears 4 off the structures (hit_wall_big()) and
 * everything else (check_obj_hit_radius()) within its growing radius, and brightens and then
 * fades the palette.
 */
internal fun Match.stepAbombs() {
    if (abombs.isEmpty()) {
        flash = 0
        return
    }
    for (bomb in abombs.toList()) {
        val blast = bomb.step() ?: continue
        if (blast.first) pendingEvents += GameEvent.Sound(com.tankarena.protocol.snapshot.SoundKind.NUKE, bomb.x, bomb.y)
        flash += blast.flash
        nukeStructures(bomb, blast.radius)
        nukeObjects(bomb, blast.radius)
    }
    abombs.removeAll { it.removed }
    if (abombs.none { it.exploding }) flash = 0
}

internal fun Match.nukeStructures(bomb: Abomb, radius: Int) {
    var y2 = bomb.y - radius
    while (y2 < bomb.y + radius) {
        var x2 = bomb.x - radius
        while (x2 < bomb.x + radius) {
            if (withinRadius(x2, y2, bomb.x, bomb.y, radius + 16)) {
                blastStructureAt(x2, y2, Abomb.POWER, EffectKind.EXPLOSION, silent = true, by = bomb.owner)
            }
            x2 += LEGACY_TILE_SIZE
        }
        y2 += LEGACY_TILE_SIZE
    }
    repeat(5) {
        val angle = 2 * Math.PI * objectRandom.nextInt(360) / 360
        pendingEvents += GameEvent.Effect(
            EffectKind.EXPLOSION,
            bomb.x + (radius * DetMath.cos(angle)).toInt(),
            bomb.y + (radius * DetMath.sin(angle)).toInt(),
        )
    }
}

/** Legacy check_obj_hit_radius(..., HT_ABOMB, 4): vehicles (except in shelters), soldiers and bonuses. */
internal fun Match.nukeObjects(bomb: Abomb, radius: Int) {
    for (tank in allTanks) {
        if (tank.armor <= 0 || tank.height < 1) continue
        if (!withinRadius(tank.positionX, tank.positionY, bomb.x, bomb.y, maxOf(radius, tank.spec.radius))) continue
        if (inShelter(tank)) continue
        tank.queueDamage(Abomb.POWER)
    }
    blastSmallObjects(bomb.x, bomb.y, radius, nuke = true, power = Abomb.POWER)
}

/** Legacy hit_tank(): a vehicle on a SHELTER tile is safe from A-bombs. */
internal fun Match.inShelter(tank: Tank): Boolean {
    val tileX = tank.positionX / LEGACY_TILE_SIZE
    val tileY = tank.positionY / LEGACY_TILE_SIZE
    val picture = structures.solid.getOrNull(tileX + tileY * mapMetadata.widthTiles) ?: return false
    return com.tankarena.content.PictureCatalog.legacyRow(mapMetadata.world, picture)?.type == "SHELTER"
}

/** Legacy check_obj_hit_radius() for the small objects: soldiers, bonuses and (not for nukes) A-bombs. */
internal fun Match.blastSmallObjects(x: Int, y: Int, radius: Int, nuke: Boolean = false, power: Int = 0, rocket: Boolean = false) {
    // Legacy determine_hit(): a bomber is only within reach low down, or of a rocket.
    for (b52 in b52s) {
        if (b52.alive && power > 0 && (rocket || b52.height < 8) &&
            withinRadius(b52.positionX, b52.positionY, x, y, maxOf(radius, B52.RADIUS))
        ) {
            hitB52(b52, power)
        }
    }
    // Train cars (r = 12) take the blast's power; mines in it go off too.
    for (car in trains) {
        if (car.alive && power > 0 && withinRadius(car.positionX, car.positionY, x, y, maxOf(radius, Train.RADIUS))) hitTrain(car, power)
    }
    for (mine in objects.all) {
        if (mine is Mine && !mine.isDead && withinRadius(mine.positionX, mine.positionY, x, y, maxOf(radius, MINE_RADIUS_PX))) {
            mine.detonateOn(null)
        }
    }
    for (soldier in soldiers) {
        if (!soldier.removed && withinRadius(soldier.x, soldier.y, x, y, maxOf(radius, SOLDIER_RADIUS_PX))) removeSoldier(soldier)
    }
    for (bonus in bonuses) {
        if (!bonus.removed && withinRadius(bonus.x, bonus.y, x, y, maxOf(radius, BONUS_RADIUS_PX))) destroyBonus(bonus)
    }
    if (!nuke) {
        for (bomb in abombs) {
            if (!bomb.exploding && withinRadius(bomb.x, bomb.y, x, y, maxOf(radius, ABOMB_RADIUS_PX))) bomb.removed = true
        }
    }
    // Legacy determine_hit(): rockets in flight can always be hit.
    for (rocket in objects.all.filterIsInstance<Rocket>()) {
        if (!rocket.isDead && withinRadius(rocket.positionX.toInt(), rocket.positionY.toInt(), x, y, maxOf(radius, ROCKET_RADIUS_PX))) {
            destroyRocket(rocket)
        }
    }
    soldiers.removeAll { it.removed }
    bonuses.removeAll { it.removed }
    abombs.removeAll { it.removed }
}

/**
 * Legacy line_obj_hit_point()/line_obj_hit_radius() for the small objects: a shot that
 * passes within a soldier's, bonus's or waiting A-bomb's radius (15 px for flames) hits it
 * and stops. Soldiers of one vehicle do not hit each other.
 */
internal fun Match.checkSmallObjectHits() {
    for (projectile in objects.all) {
        if (projectile !is Projectile) continue
        projectile.drainTrail()?.let { (x, y) -> pendingEvents += GameEvent.Effect(EffectKind.EXHAUST, x, y) }
    }
    val rockets = objects.all.filterIsInstance<Rocket>()
    val mines = objects.all.filterIsInstance<Mine>()
    if (soldiers.isEmpty() && bonuses.isEmpty() && abombs.isEmpty() && trains.isEmpty() && b52s.isEmpty() && rockets.isEmpty() && mines.isEmpty()) return
    for (projectile in objects.all) {
        if (projectile !is Projectile || projectile.isDead || !projectile.armed) continue
        val (x1, y1) = projectile.previousPosition
        val x2 = projectile.positionX
        val y2 = projectile.positionY
        val flame = projectile.weaponKind == WEAPON_FLAME
        val shooter = projectile.ownerSoldierRef
        val soldier = soldiers.firstOrNull {
            !it.removed && it !== shooter && !(shooter != null && shooter.owner === it.owner) &&
                nearSegment(x1, y1, x2, y2, it.x, it.y, if (flame) FLAME_HIT_RADIUS_PX else SOLDIER_RADIUS_PX)
        }
        if (soldier != null) {
            removeSoldier(soldier)
            projectile.stopAt(soldier.x, soldier.y)
            continue
        }
        val bonus = bonuses.firstOrNull {
            !it.removed && nearSegment(x1, y1, x2, y2, it.x, it.y, if (flame) FLAME_HIT_RADIUS_PX else BONUS_RADIUS_PX)
        }
        if (bonus != null) {
            destroyBonus(bonus)
            projectile.stopAt(bonus.x, bonus.y)
            continue
        }
        // Legacy determine_hit(): turret rounds pass through trains.
        val car = if (projectile.ownerTurretRef != null) null else trains.firstOrNull {
            it.alive && nearSegment(x1, y1, x2, y2, it.positionX, it.positionY, if (flame) FLAME_HIT_RADIUS_PX else Train.RADIUS)
        }
        if (car != null) {
            hitTrain(car, projectile.damage)
            projectile.stopAt(x2, y2)
            continue
        }
        val bomber = b52s.firstOrNull {
            it.alive && it.height < 8 &&
                nearSegment(x1, y1, x2, y2, it.positionX, it.positionY, if (flame) FLAME_HIT_RADIUS_PX else B52.RADIUS)
        }
        if (bomber != null) {
            hitB52(bomber, projectile.damage)
            projectile.stopAt(x2, y2)
            continue
        }
        val bomb = abombs.firstOrNull {
            !it.removed && !it.exploding &&
                nearSegment(x1, y1, x2, y2, it.x, it.y, if (flame) FLAME_HIT_RADIUS_PX else ABOMB_RADIUS_PX)
        }
        if (bomb != null) {
            bomb.removed = true
            projectile.stopAt(bomb.x, bomb.y)
            continue
        }
        // Legacy line_obj_hit_point() / line_obj_hit_radius(): a rocket in flight or a mine on the
        // path (within its 3 px, or a flame's 15 px) is hit too, whatever its height.
        val rocket = rockets.firstOrNull {
            !it.isDead && nearSegment(x1, y1, x2, y2, it.positionX.toInt(), it.positionY.toInt(), if (flame) FLAME_HIT_RADIUS_PX else ROCKET_RADIUS_PX)
        }
        if (rocket != null) {
            destroyRocket(rocket)
            projectile.stopAt(rocket.positionX.toInt(), rocket.positionY.toInt())
            continue
        }
        val mine = mines.firstOrNull {
            !it.isDead && nearSegment(x1, y1, x2, y2, it.positionX, it.positionY, if (flame) FLAME_HIT_RADIUS_PX else MINE_RADIUS_PX)
        }
        if (mine != null) {
            mine.detonateOn(null)
            projectile.stopAt(mine.positionX, mine.positionY)
        }
    }
    soldiers.removeAll { it.removed }
    bonuses.removeAll { it.removed }
    abombs.removeAll { it.removed }
}

/** True when ([px], [py]) lies within [radius] of the segment from ([x1], [y1]) to ([x2], [y2]). */
internal fun Match.nearSegment(x1: Int, y1: Int, x2: Int, y2: Int, px: Int, py: Int, radius: Int): Boolean {
    val dx = (x2 - x1).toDouble()
    val dy = (y2 - y1).toDouble()
    val lengthSq = dx * dx + dy * dy
    val t = if (lengthSq == 0.0) 0.0 else (((px - x1) * dx + (py - y1) * dy) / lengthSq).coerceIn(0.0, 1.0)
    val cx = x1 + t * dx - px
    val cy = y1 + t * dy - py
    return cx * cx + cy * cy <= radius.toDouble() * radius
}

/**
 * Legacy check_obj_collide() for vehicles on the ground: driving into a soldier kills him,
 * and a tank or car picks up the bonuses it touches.
 */
internal fun Match.touchSmallObjects() {
    if (soldiers.isEmpty() && bonuses.isEmpty()) return
    for (tank in allTanks) {
        if (tank.armor <= 0 || kotlin.math.abs(tank.height) >= 2) continue
        for (soldier in soldiers) {
            if (!soldier.removed && withinRadius(tank.positionX, tank.positionY, soldier.x, soldier.y, tank.spec.radius + SOLDIER_RADIUS_PX)) {
                removeSoldier(soldier)
            }
        }
        if (!tank.picksUpBonuses) continue
        for (bonus in bonuses) {
            if (bonus.removed || !withinRadius(tank.positionX, tank.positionY, bonus.x, bonus.y, tank.spec.radius + BONUS_RADIUS_PX)) continue
            val what = tank.pickUpBonus(bonus.type)
            bonus.removed = true
            pendingEvents += GameEvent.Sound(com.tankarena.protocol.snapshot.SoundKind.BONUS, tank.positionX, tank.positionY)
            tank.showMessage("PICKED UP", what)
        }
    }
    soldiers.removeAll { it.removed }
    bonuses.removeAll { it.removed }
}

/**
 * Legacy check_obj_overlap(): two vehicles at about the same height whose circles overlap
 * (a plane touching down on a tank, a tank spawning on another) are both destroyed, unless
 * one is still shielded by its start or bonus invulnerability.
 */
internal fun Match.checkVehicleOverlaps() {
    for ((i, a) in allTanks.withIndex()) {
        for (b in allTanks.subList(i + 1, allTanks.size)) {
            if (kotlin.math.abs(a.height - b.height) >= 2) continue
            val reach = a.spec.radius + b.spec.radius
            val dx = a.positionX - b.positionX
            val dy = a.positionY - b.positionY
            if (dx * dx + dy * dy >= reach * reach) continue
            // The original tests the first vehicle's armor for both of them.
            if (a.armor <= 0) continue
            if (ruleset.flags.stackedComputerVehiclesSurvive && a.computer && b.computer) continue
            if (a.invulnerableTicks <= 0) a.queueDestroy()
            if (b.invulnerableTicks <= 0) b.queueDestroy()
        }
    }
}

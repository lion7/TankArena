package com.tankarena.sim

import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.protocol.snapshot.EffectKind
import com.tankarena.protocol.snapshot.GameEvent
import com.tankarena.protocol.snapshot.ProjectileOwnerKind
import kotlin.math.roundToInt

// Buildings hit by shots, blasts and crashes, and what falling buildings leave behind
// (src/check/hitwall.c, hitobj.c).

/**
 * Legacy hit_wall_for()/hit_wall(): damage a structure and play out what destroying it does —
 * collapse effects, the tile's goal-counter share, bridges turning into water, burning fuel
 * dumps, and turrets falling with the building under them.
 */
internal fun Match.hitStructure(
    tileX: Int,
    tileY: Int,
    layer: StructureLayer,
    kind: HitKind,
    power: Int,
    explosion: EffectKind,
    /** Legacy HT_ABOMB: buildings an A-bomb flattens fall without the collapse sound and debris. */
    silent: Boolean = false,
    /** The vehicle that did it (legacy `owner`), whom paratroopers from the building go after. */
    by: Tank? = null,
    /** Legacy HT_REMOVE: a lock takes even an indestructible structure down. */
    remove: Boolean = false,
) {
    for (destroyed in structures.hit(tileX, tileY, layer, kind, power, remove)) {
        onStructureDestroyed(destroyed, explosion, silent, by)
    }
}

internal fun Match.onStructureDestroyed(destroyed: DestroyedStructure, hitExplosion: EffectKind, silent: Boolean, by: Tank?) {
    val centreX = destroyed.tileX * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2
    val centreY = destroyed.tileY * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2
    if (!silent && destroyed.legacyType != "BUSH_SIDE") {
        pendingEvents += GameEvent.Sound(com.tankarena.protocol.snapshot.SoundKind.COLLAPSE, centreX, centreY)
        val effect = when (destroyed.explosion) {
            "RUIN" -> if (hitExplosion == EffectKind.CHAIN) EffectKind.RUIN else hitExplosion
            "EXPLOSION" -> EffectKind.EXPLOSION
            "SPLASH" -> EffectKind.SPLASH
            "FLAME" -> EffectKind.FLAME
            "OIL_SPLASH" -> EffectKind.OIL_SPLASH
            else -> EffectKind.RUIN
        }
        if (effect == EffectKind.OIL_SPLASH) {
            pendingEvents += GameEvent.Effect(effect, centreX, centreY)
        } else {
            repeat(5) {
                pendingEvents += GameEvent.Effect(effect, centreX - 13 + structureRandom.nextInt(26), centreY - 13 + structureRandom.nextInt(26))
            }
        }
    }
    if (destroyed.layer != StructureLayer.SOLID) return
    // Legacy hit_wall(): a building that goes up in an explosion throws pieces of wall.
    if (!silent && destroyed.explosion == "EXPLOSION") {
        pendingEvents += GameEvent.Debris(com.tankarena.protocol.snapshot.DebrisKind.PARTS, centreX, centreY)
    }
    modifyGoalCounter(destroyed.goalContribution)
    createBonus(destroyed.tileX, destroyed.tileY)
    dropParatroopers(destroyed.tileX, destroyed.tileY, by)
    when (destroyed.legacyType) {
        "BRIDGE_H", "BRIDGE_V", "BRIDGE_CR" -> terrainGrid.update(
            destroyed.tileX,
            destroyed.tileY,
            com.tankarena.content.PictureCatalog.resolve(mapMetadata.world, structures.base[destroyed.tileX + destroyed.tileY * mapMetadata.widthTiles]),
        )
        // Legacy create_parts(PT_FLAME): a fuel dump throws out ten burning pieces.
        "FUEL_DUMP" -> spawnFlameParts(centreX, centreY)
        // Legacy create_abomb(0, x2 + 16, y2 + 16, 0): an A-bomb box goes off at once. The
        // original passes the tile's centre as the bomb's top-left, so the blast centre lies
        // half a tile right of and below the box.
        "ABOX" -> abombs += Abomb(nextActorId++, centreX + LEGACY_TILE_SIZE / 2, centreY + LEGACY_TILE_SIZE / 2, countdown = 0, owner = null)
    }
    removeFallenTurrets()
}

/**
 * Legacy control_turret(): a map turret stands on a building and is removed (applying its
 * goal contribution) as soon as that building is gone.
 */
internal fun Match.removeFallenTurrets() {
    for (turret in objects.all) {
        if (turret !is Turret || !turret.isAlive || turret in ridingTurrets) continue
        val tileX = turret.positionX / LEGACY_TILE_SIZE
        val tileY = turret.positionY / LEGACY_TILE_SIZE
        if (!structures.isStanding(tileX, tileY)) turret.fall()
    }
}

/**
 * Legacy control_bullets() with line_wall_hit_point(): bullets and flames that cross an opaque
 * pixel of a structure stop there and damage it. A turret's own shots leave its building
 * (they ignore structures within two tiles of it).
 */
internal fun Match.checkProjectileStructureHits() {
    for (projectile in objects.all) {
        // Legacy control_bullets(): a small rocket within its safety distance passes everything.
        if (projectile !is Projectile || projectile.isDead || !projectile.armed) continue
        val turret = projectile.ownerTurretRef
        val (fromX, fromY) = projectile.previousPosition
        val toX = projectile.positionX
        val toY = projectile.positionY
        val steps = maxOf(kotlin.math.abs(toX - fromX), kotlin.math.abs(toY - fromY)).coerceAtLeast(1)
        for (step in 1..steps) {
            val x = fromX + (toX - fromX) * step / steps
            val y = fromY + (toY - fromY) * step / steps
            if (turret != null && turret !in ridingTurrets && withinRadius(turret.positionX, turret.positionY, x, y, 2 * LEGACY_TILE_SIZE)) continue
            val layer = structures.hitLayerAt(x, y) ?: continue
            val kind = if (projectile.weaponKind == WEAPON_FLAME) HitKind.FLAME else HitKind.NORMAL
            val effect = when (projectile.weaponKind) {
                WEAPON_CHAIN -> EffectKind.CHAIN
                WEAPON_FLAME -> EffectKind.FLAME
                else -> EffectKind.EXPLOSION
            }
            val tileX = Math.floorDiv(x, LEGACY_TILE_SIZE)
            val tileY = Math.floorDiv(y, LEGACY_TILE_SIZE)
            hitStructure(tileX, tileY, layer, kind, projectile.damage, effect, by = projectile.ownerRef)
            // Legacy hit_wall(): one chain-gun round in ten ricochets off a wall.
            if (projectile.weaponKind == WEAPON_CHAIN && objectRandom.nextInt(10) == 0) ricochet(x, y)
            // Legacy control_bullets(): a shot (not a flame) that hits a force field still
            // standing bounces off it.
            val bounce = if (projectile.weaponKind != WEAPON_FLAME && structures.isStanding(tileX, tileY)) {
                fieldBounce(tileX, tileY, projectileDirection(projectile))
            } else {
                null
            }
            if (bounce != null) {
                projectile.deflect(x, y, bounce)
            } else {
                projectile.stopAt(x, y)
                shotDebris(projectile, x, y)
            }
            break
        }
    }
}

/**
 * Legacy line_obj_hit_point() (src/check/hitobj.c) for the vehicles: a shot that got past the
 * walls this tick hits the first vehicle, in object order, whose body picture lies on its path.
 * The original walks the path from its start one pixel per step diagonally (both axes move by
 * their sign) for as many steps as the longer axis, plus one. Vehicles sinking into a pit
 * (height below 1) are only hit by rockets, which are not shots here; flames burn by radius
 * (triggerFlameContacts()).
 */
internal fun Match.checkProjectileVehicleHits() {
    for (projectile in objects.all) {
        if (projectile !is Projectile || projectile.isDead || !projectile.armed) continue
        if (projectile.weaponKind == WEAPON_FLAME) continue
        val (fromX, fromY) = projectile.previousPosition
        val dx = projectile.positionX - fromX
        val dy = projectile.positionY - fromY
        val steps = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
        val stepX = Integer.signum(dx)
        val stepY = Integer.signum(dy)
        for (tank in allTanks) {
            if (projectile.ownerKind == ProjectileOwnerKind.TANK && tank === projectile.ownerRef) continue
            if ((tank.armor <= 0 && !tank.isWreck) || tank.height < 1) continue
            var x = fromX
            var y = fromY
            var hit = false
            repeat(steps + 1) {
                if (hit) return@repeat
                x += stepX
                y += stepY
                if (tank.bodyCovers(x, y)) hit = true
            }
            if (hit) {
                // Legacy hit_obj_for(): one chain-gun round in twenty ricochets off a vehicle.
                if (projectile.weaponKind == WEAPON_CHAIN && objectRandom.nextInt(20) == 0) ricochet(tank.positionX, tank.positionY)
                projectile.hitVehicle(tank, x, y)
                shotDebris(projectile, x, y)
                break
            }
        }
    }
}

/** Legacy create_parts(PT_FLAME, x, y): ten pieces from around ([x], [y]), each its own way. */
internal fun Match.spawnFlameParts(x: Int, y: Int) {
    repeat(10) {
        if (flameParts.size >= MAX_PARTS) return
        flameParts += FlamePart(
            id = nextActorId++,
            x = (x - 5 + debrisRandom.nextInt(10)).toFloat(),
            y = (y - 5 + debrisRandom.nextInt(10)).toFloat(),
            angle = 2 * Math.PI * debrisRandom.nextInt(360) / 360,
            speed = (30 + debrisRandom.nextInt(20)).toFloat(),
            distLeft = (50 + debrisRandom.nextInt(20)).toFloat(),
        )
    }
}

/**
 * Legacy control_parts() for PT_FLAME: each piece flies on and stops at the first vehicle on the
 * ground or above on its path (7, with a push, a chain-gun hit picture and a bang) or the first
 * structure (10).
 */
internal fun Match.stepFlameParts() {
    for (part in flameParts.toList()) {
        val (from, to) = part.move() ?: continue
        val steps = maxOf(kotlin.math.abs(to.first - from.first), kotlin.math.abs(to.second - from.second)).coerceAtLeast(1)
        for (step in 1..steps) {
            val x = from.first + (to.first - from.first) * step / steps
            val y = from.second + (to.second - from.second) * step / steps
            val tank = allTanks.firstOrNull { (it.armor > 0 || it.isWreck) && it.height >= 1 && it.bodyCovers(x, y) }
            if (tank != null) {
                tank.queueDamage(FLAME_PART_POWER_TANK)
                val length = kotlin.math.hypot((to.first - from.first).toFloat(), (to.second - from.second).toFloat())
                if (length > 0f) tank.addImpulse((to.first - from.first) / length, (to.second - from.second) / length, FLAME_PART_POWER_TANK)
                pendingEvents += GameEvent.Effect(EffectKind.CHAIN, x, y)
                pendingEvents += GameEvent.Sound(com.tankarena.protocol.snapshot.SoundKind.EXPLODE, x, y)
                part.removed = true
                break
            }
            val layer = structures.hitLayerAt(x, y) ?: continue
            hitStructure(Math.floorDiv(x, LEGACY_TILE_SIZE), Math.floorDiv(y, LEGACY_TILE_SIZE), layer, HitKind.NORMAL, FLAME_PART_POWER_WALL, EffectKind.EXPLOSION)
            part.removed = true
            break
        }
    }
    flameParts.removeAll { it.removed }
}

private const val FLAME_PART_POWER_TANK = 7
private const val FLAME_PART_POWER_WALL = 10
/** Legacy `max_parts`. */
private const val MAX_PARTS = 1000

/** Legacy control_bullets(): a shot that stops scatters pixels, a chain-gun round only one time in five. */
internal fun Match.shotDebris(projectile: Projectile, x: Int, y: Int) {
    if (projectile.weaponKind == WEAPON_FLAME) return
    if (projectile.weaponKind == WEAPON_CHAIN && debrisRandom.nextInt(5) != 0) return
    pendingEvents += GameEvent.Debris(com.tankarena.protocol.snapshot.DebrisKind.PIXEL, x, y)
}

/** Legacy SND_RICOCHETS + rnd(6). */
internal fun Match.ricochet(x: Int, y: Int) {
    val kinds = listOf(
        com.tankarena.protocol.snapshot.SoundKind.RICOCHET1, com.tankarena.protocol.snapshot.SoundKind.RICOCHET2,
        com.tankarena.protocol.snapshot.SoundKind.RICOCHET3, com.tankarena.protocol.snapshot.SoundKind.RICOCHET4,
        com.tankarena.protocol.snapshot.SoundKind.RICOCHET5, com.tankarena.protocol.snapshot.SoundKind.RICOCHET6,
    )
    pendingEvents += GameEvent.Sound(kinds[objectRandom.nextInt(kinds.size)], x, y)
}

/** The 16-step direction a projectile flies in. */
internal fun Match.projectileDirection(projectile: Projectile): Int =
    com.tankarena.core.LegacyDirections.fromAngle((projectile.velocityX * 1000).roundToInt(), (projectile.velocityY * 1000).roundToInt())

/**
 * Legacy control_bullets(): the four force-field pictures mirror a shot — horizontal
 * (`8 - dir`), vertical (`-dir`) and the two diagonals (`4 - dir`, `12 - dir`). Null when the
 * tile's solid picture is no force field.
 */
internal fun Match.fieldBounce(tileX: Int, tileY: Int, direction: Int): Int? {
    val picture = structures.solid.getOrNull(tileX + tileY * mapMetadata.widthTiles) ?: return null
    return when (com.tankarena.content.PictureCatalog.legacyRow(mapMetadata.world, picture)?.type) {
        "FIELD_H" -> (8 - direction).mod(16)
        "FIELD_V" -> (0 - direction).mod(16)
        "FIELD_P" -> (4 - direction).mod(16)
        "FIELD_N" -> (12 - direction).mod(16)
        else -> null
    }
}

/** Legacy check_wall_hit() at a world point: wears down the top or else the solid structure there. */
internal fun Match.blastStructureAt(
    x: Int,
    y: Int,
    power: Int,
    explosion: EffectKind,
    silent: Boolean = false,
    by: Tank? = null,
) {
    val tileX = Math.floorDiv(x, LEGACY_TILE_SIZE)
    val tileY = Math.floorDiv(y, LEGACY_TILE_SIZE)
    val layer = structures.blastLayerAt(tileX, tileY) ?: return
    hitStructure(tileX, tileY, layer, HitKind.NORMAL, power, explosion, silent, by)
}

/**
 * Legacy create_mortar(): the blast grows from 5 to 60 px, and each step (hit_wall_big())
 * wears 5 off every structure within the radius plus 16.
 */
/**
 * Legacy hit_wall_big() (src/check/hitwall.c): one ring of a blast hits the structures on a 32 px
 * grid within [radius] + 16 for [MORTAR_STEP_POWER], with five explosions around the ring.
 */
internal fun Match.blastStructureRing(x: Int, y: Int, radius: Int, by: Tank?) {
    var y2 = y - radius
    while (y2 < y + radius) {
        var x2 = x - radius
        while (x2 < x + radius) {
            if (withinRadius(x2, y2, x, y, radius + 16)) blastStructureAt(x2, y2, MORTAR_STEP_POWER, EffectKind.EXPLOSION, by = by)
            x2 += LEGACY_TILE_SIZE
        }
        y2 += LEGACY_TILE_SIZE
    }
    repeat(5) {
        val angle = objectRandom.nextDouble() * 2 * Math.PI
        pendingEvents += GameEvent.Effect(
            EffectKind.EXPLOSION,
            x + (com.tankarena.core.DetMath.cos(angle) * radius).toInt(),
            y - (com.tankarena.core.DetMath.sin(angle) * radius).toInt(),
        )
    }
}

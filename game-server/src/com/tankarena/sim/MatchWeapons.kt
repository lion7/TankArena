package com.tankarena.sim

import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.protocol.snapshot.EffectKind
import com.tankarena.protocol.snapshot.ExplosionKind
import com.tankarena.protocol.snapshot.GameEvent
import com.tankarena.protocol.snapshot.ProjectileOwnerKind
import kotlin.math.roundToInt

// Turrets and every shot: bullets, flames, rockets, mortar shells and mines, from firing to impact
// (src/objects/turret.c, rocket.c, mortar.c, mine.c, src/world/bullet.c, src/tanks/fire.c).

internal fun Match.stepTurrets() {
    val tanks = objects.all.filterIsInstance<Tank>()
    if (tanks.isEmpty()) return
    for (actor in objects.all) {
        if (actor is Turret) actor.step(tanks)
    }
}

internal fun Match.flushOutOfBoundsProjectiles() {
    for (actor in objects.all) {
        if (actor is Projectile) {
            actor.markOutOfBoundsIfNeeded(worldWidthPixels, worldHeightPixels)
        }
    }
}

internal fun Match.removeDeadProjectiles() {
    val doomed = objects.all
        .filterIsInstance<Projectile>()
        .filter { it.isDead }
    if (doomed.isEmpty()) return
    for (projectile in doomed) {
        val end = projectile.drainExplosion() ?: continue
        hitEffectFor(projectile.weaponKind, end.expired)?.let { pendingEvents += GameEvent.Effect(it, end.x, end.y) }
    }
    objects.remove(doomed)
    for (projectile in doomed) {
        actorIds.remove(projectile)
    }
}

/**
 * Legacy `hit[type].explosion` (src/check/hitwall.c) for a bullet that hits something, and
 * control_bullets() for one that runs out of range: the main gun explodes either way, a chain-gun
 * round sparks or puffs, a flame burns where it hit and simply dies out otherwise.
 */
internal fun Match.hitEffectFor(weaponKind: Int, expired: Boolean): EffectKind? = when (weaponKind) {
    WEAPON_CHAIN -> if (expired) EffectKind.PLUF else EffectKind.CHAIN
    WEAPON_FLAME -> if (expired) null else EffectKind.FLAME
    else -> EffectKind.EXPLOSION
}

/**
 * Legacy line_obj_hit_radius(..., 15, HT_FLAME, ...): a flame burns the first live tank or
 * turret within 15 px of it, other than the tank that fired it.
 */
internal fun Match.triggerFlameContacts() {
    val flames = objects.all.filterIsInstance<Projectile>()
        .filter { it.weaponKind == WEAPON_FLAME && !it.isDead && it.armed }
    if (flames.isEmpty()) return
    for (flame in flames) {
        // Legacy line_obj_hit_radius(..., 15, HT_FLAME, ...): every vehicle off the ground
        // within 15 px (or its own radius, if larger) of the flame's path this tick burns.
        val (fromX, fromY) = flame.previousPosition
        val burnt = allTanks.filter { tank ->
            tank !== flame.ownerRef && (tank.armor > 0 || tank.isWreck) && tank.height > 0 &&
                nearSegment(fromX, fromY, flame.positionX, flame.positionY, tank.positionX, tank.positionY, maxOf(FLAME_HIT_RADIUS_PX, tank.spec.radius))
        }
        for (tank in burnt) tank.queueDamage(flame.damage)
        if (burnt.isNotEmpty()) flame.burn(burnt.first())
    }
}

internal fun Match.flushOutOfBoundsRockets() {
    for (actor in objects.all) {
        if (actor is Rocket) actor.markOutOfBoundsIfNeeded(worldWidthPixels, worldHeightPixels)
    }
}

/**
 * Legacy nearest_target() (src/objects/objects.c): the closest object with a hit radius within
 * 15 px of ([x], [y]) — any vehicle (the shooter's own and wrecks too), soldier, bomber, train car,
 * mine, bonus, A-bomb, flag, product, the zeppelin or another rocket. Turrets, warps and goals have
 * no radius. The rocket follows it until it is gone from the map.
 */
@Suppress("UNUSED_PARAMETER")
internal fun Match.rocketTargetAt(x: Int, y: Int, owner: Tank?): RocketTarget? {
    val candidates = buildList<Triple<Int, Int, RocketTarget>> {
        fun add(ax: Int, ay: Int, target: RocketTarget) = add(Triple(ax, ay, target))
        for (tank in allTanks) if (!tank.wreckGone) add(tank.positionX, tank.positionY) { tank.takeIf { !it.wreckGone && it in allTanks }?.let { it.positionX to it.positionY } }
        for (soldier in soldiers) if (!soldier.removed) add(soldier.x, soldier.y) { soldier.takeIf { !it.removed }?.let { it.x to it.y } }
        for (b52 in b52s) if (b52.alive) add(b52.positionX, b52.positionY) { b52.takeIf { it.alive }?.let { it.positionX to it.positionY } }
        for (car in trains) if (car.alive) add(car.positionX, car.positionY) { car.takeIf { it.alive }?.let { it.positionX to it.positionY } }
        for (bonus in bonuses) if (!bonus.removed) add(bonus.x, bonus.y) { bonus.takeIf { !it.removed }?.let { it.x to it.y } }
        for (bomb in abombs) if (!bomb.removed) add(bomb.x, bomb.y) { bomb.takeIf { !it.removed }?.let { it.x to it.y } }
        for (actor in objects.all) {
            when (actor) {
                is Mine -> if (!actor.isDead) add(actor.positionX, actor.positionY) { actor.takeIf { !it.isDead }?.let { it.positionX to it.positionY } }
                is Rocket -> if (!actor.isDead) add(actor.positionX.toInt(), actor.positionY.toInt()) { actor.takeIf { !it.isDead }?.let { it.positionX.toInt() to it.positionY.toInt() } }
                is Zeppelin -> if (actor.alive) add(actor.positionX, actor.positionY) { actor.takeIf { it.alive }?.let { it.positionX to it.positionY } }
                is Flag, is Product -> {
                    val centre = centreOf(actor) ?: continue
                    if (actor !is Product || !actor.isCollected) add(centre.first, centre.second) { centre.takeIf { actor in objects.all } }
                }
                else -> Unit
            }
        }
    }
    return candidates
        .filter { (ax, ay) -> withinRadius(ax, ay, x, y, ROCKET_TARGET_RADIUS_PX) }
        .minByOrNull { (ax, ay) -> (ax - x).toLong() * (ax - x) + (ay - y).toLong() * (ay - y) }
        ?.third
}

/** Legacy create_rocket(): the vehicle a rocket is launched at hears it coming. */
internal fun Match.warnIncoming(x: Int, y: Int, owner: Tank?) {
    val target = allTanks
        .filter { it !== owner && it.armor > 0 && withinRadius(it.positionX, it.positionY, x, y, ROCKET_TARGET_RADIUS_PX) }
        .minByOrNull { (it.positionX - x).toLong() * (it.positionX - x) + (it.positionY - y).toLong() * (it.positionY - y) }
        ?: return
    target.showMessage("INCOMING", "MISSILE!")
    pendingEvents += GameEvent.Sound(com.tankarena.protocol.snapshot.SoundKind.INCOMING, target.positionX, target.positionY)
}

internal fun Match.drainRocketExplosions() {
    val tanks = objects.all.filterIsInstance<Tank>()
    for (actor in objects.all) {
        if (actor !is Rocket) continue
        actor.drainTrail()?.let { (x, y) -> pendingEvents += GameEvent.Effect(EffectKind.ROCKET_EXHAUST, x, y) }
        val explosion = actor.drainExplosion() ?: continue
        pendingEvents += GameEvent.Explosion(explosion.x, explosion.y, ROCKET_BLAST_RADIUS_PX, ExplosionKind.ROCKET)
        // Legacy control_rocket(): without a target, or out of range, it hits the structure at the
        // target point (the marker).
        if (actor.target?.position() == null || !explosion.onTarget) {
            blastStructureAt(explosion.x, explosion.y, actor.damage, EffectKind.LEXPLOSION, by = actor.ownerRef)
            continue
        }
        // Otherwise everything within 10 px of the target (or its own radius) is hit — the shooter
        // too, and through the shield key (HT_ROCKET) — with a big explosion and a bang each.
        for (tank in tanks) {
            if ((tank.armor <= 0 && !tank.isWreck) || tank.wreckGone) continue
            if (!withinRadius(tank.positionX, tank.positionY, explosion.x, explosion.y, maxOf(ROCKET_BLAST_RADIUS_PX, tank.spec.radius))) continue
            tank.queueUnshieldableDamage(actor.damage)
            pendingEvents += GameEvent.Effect(EffectKind.LEXPLOSION, explosion.x, explosion.y)
            pendingEvents += GameEvent.Sound(com.tankarena.protocol.snapshot.SoundKind.EXPLODE, explosion.x, explosion.y)
        }
        blastSmallObjects(explosion.x, explosion.y, ROCKET_BLAST_RADIUS_PX, power = actor.damage, rocket = true)
        // Legacy hit_rocket(): other rockets in the blast are gone.
        for (other in objects.all.filterIsInstance<Rocket>()) {
            if (other !== actor && !other.isDead &&
                withinRadius(other.positionX.toInt(), other.positionY.toInt(), explosion.x, explosion.y, maxOf(ROCKET_BLAST_RADIUS_PX, ROCKET_RADIUS_PX))
            ) {
                other.removeQuietly()
            }
        }
    }
}

internal fun Match.removeDeadRockets() {
    val doomed = objects.all.filterIsInstance<Rocket>().filter { it.isDead }
    if (doomed.isEmpty()) return
    objects.remove(doomed)
    for (rocket in doomed) actorIds.remove(rocket)
}

internal fun Match.drainRocketRequests() {
    val spawns = mutableListOf<Rocket>()
    for (tank in allTanks) {
        val request = tank.drainRocketRequest() ?: continue
        spawns += rocketFromRequest(tank, request)
    }
    for ((turret, request) in pendingTurretRockets) {
        val (targetX, targetY) = request.rocketTarget ?: continue
        spawns += rocketFromRequest(
            null,
            Tank.RocketRequest(request.originX, request.originY, targetX, targetY, request.damage),
            ownerActorId = actorIds[turret] ?: 0L,
        )
    }
    pendingTurretRockets.clear()
    if (spawns.isEmpty()) return
    objects.add(spawns)
    for (rocket in spawns) actorIds.getOrPut(rocket) { nextActorId++ }
}

internal fun Match.rocketFromRequest(
    owner: Tank?,
    request: Tank.RocketRequest,
    ownerActorId: Long = owner?.let { actorIds[it] } ?: 0L,
): Rocket {
    val rocket = Rocket(
        Rocket.State(
            body = PointBody(
                position = ScenePoint(
                    request.originX.toFloat(),
                    request.originY.toFloat(),
                ),
            ),
            ownerActorId = ownerActorId,
            damage = request.damage,
            targetX = request.targetX,
            targetY = request.targetY,
        ),
    )
    rocket.ownerRef = owner
    rocket.target = rocketTargetAt(request.targetX, request.targetY, owner)
    warnIncoming(request.targetX, request.targetY, owner)

    // Emit sound event for rocket launch
    pendingEvents += GameEvent.Sound(
        kind = com.tankarena.protocol.snapshot.SoundKind.ROCKET,
        x = request.originX,
        y = request.originY,
    )

    return rocket
}

internal fun Match.flushOutOfBoundsMortars() {
    for (actor in objects.all) {
        if (actor is Mortar) actor.markOutOfBoundsIfNeeded(worldWidthPixels, worldHeightPixels)
    }
}

/**
 * Legacy create_mortar() and control_mortar() (src/objects/mortar.c): where a shell or a plane's bomb
 * lands there is a bang (SND_NUKE), then a blast that grows by 5 px a step from 5 to 60 px. Every
 * ring hits whatever is within it for 5 — vehicles above ground (the shooter too), soldiers, mines,
 * low bombers, trains, bonuses and A-bombs — and the structures under it. The shell's own power
 * plays no part.
 */
internal fun Match.drainMortarExplosions() {
    val mortars = objects.all.filterIsInstance<Mortar>()
    if (mortars.isEmpty()) return
    val tanks = objects.all.filterIsInstance<Tank>()
    for (mortar in mortars) {
        mortar.drainExplosion()?.let { impact ->
            pendingEvents += GameEvent.Explosion(impact.x, impact.y, impact.radius, ExplosionKind.MORTAR)
            pendingEvents += GameEvent.Sound(com.tankarena.protocol.snapshot.SoundKind.NUKE, impact.x, impact.y)
        }
        val ring = mortar.nextRing() ?: continue
        val x = mortar.positionX.toInt()
        val y = mortar.positionY.toInt()
        for (tank in tanks) {
            if ((tank.armor <= 0 && !tank.isWreck) || tank.height <= 0) continue
            if (withinRadius(tank.positionX, tank.positionY, x, y, maxOf(ring, tank.spec.radius))) {
                tank.queueDamage(MORTAR_STEP_POWER)
                pendingEvents += GameEvent.Effect(EffectKind.EXPLOSION, x, y)
            }
        }
        blastSmallObjects(x, y, ring, power = MORTAR_STEP_POWER)
        blastStructureRing(x, y, ring, mortar.ownerRef)
    }
}

internal fun Match.removeDeadMortars() {
    val doomed = objects.all.filterIsInstance<Mortar>().filter { it.isDead }
    if (doomed.isEmpty()) return
    objects.remove(doomed)
    for (mortar in doomed) actorIds.remove(mortar)
}

internal fun Match.drainMortarRequests() {
    val spawns = mutableListOf<Mortar>()
    for (tank in allTanks) {
        val request = tank.drainMortarRequest() ?: continue
        spawns += mortarFromRequest(tank, request)
    }
    if (spawns.isEmpty()) return
    objects.add(spawns)
    for (mortar in spawns) actorIds.getOrPut(mortar) { nextActorId++ }
}

internal fun Match.mortarFromRequest(
    owner: Tank,
    request: Tank.MortarRequest,
): Mortar {
    val ownerActorId = actorIds[owner] ?: 0L
    val mortar = Mortar(
        Mortar.State(
            body = PointBody(
                position = ScenePoint(
                    request.originX.toFloat(),
                    request.originY.toFloat(),
                ),
            ),
            ownerActorId = ownerActorId,
            damage = request.damage,
            maxRadius = request.maxRadius,
            velocityX = request.velocityX,
            velocityY = request.velocityY,
            travelTicksRemaining = request.travelTicks,
            dropHeight = request.dropHeight,
        ),
    )
    mortar.ownerRef = owner
    
    // Legacy create_bullet(): a mortar shell bangs, a plane's bomb just drops.
    if (request.dropHeight == 0) {
        pendingEvents += GameEvent.Sound(
            kind = com.tankarena.protocol.snapshot.SoundKind.MORTAR,
            x = request.originX,
            y = request.originY,
        )
    }
    
    return mortar
}

/**
 * Legacy check_obj_collide(): a vehicle on or near the ground (height -1..1) whose radius plus the
 * mine's reach covers an armed mine sets it off — its own layer too.
 */
internal fun Match.triggerMineContacts() {
    val mines = objects.all.filterIsInstance<Mine>().filter { it.isActive && !it.isDead }
    if (mines.isEmpty()) return
    val tanks = objects.all.filterIsInstance<Tank>().filter { it.armor > 0 && it.height in -1..1 }
    for (mine in mines) {
        val victim = tanks.firstOrNull { tank ->
            withinRadius(tank.positionX, tank.positionY, mine.positionX, mine.positionY, tank.spec.radius + mine.triggerReach)
        } ?: continue
        mine.detonateOn(victim)
    }
}

internal fun Match.drainMineDetonations() {
    val tanks = objects.all.filterIsInstance<Tank>()
    for (actor in objects.all) {
        if (actor !is Mine) continue
        val det = actor.drainDetonation() ?: continue
        // Legacy remove_mine(): a big explosion either way.
        pendingEvents += GameEvent.Effect(EffectKind.LEXPLOSION, det.x, det.y)
        if (actor.heavy) {
            fireHeavyMine(det.x, det.y)
            continue
        }
        pendingEvents.addAll(AreaDamageResolver.resolve(
            tanks = tanks,
            x = det.x,
            y = det.y,
            radius = actor.radius,
            damage = actor.damage,
            kind = ExplosionKind.MINE,
            falloff = false,
        ))
        // Legacy blowup_mine(): the mine also hits the structure it lies on.
        blastStructureAt(det.x, det.y, actor.damage, EffectKind.EXPLOSION)
        blastSmallObjects(det.x, det.y, actor.radius, power = actor.damage)
    }
}

/**
 * Legacy blowup_mine() for a heavy mine: eight small rockets, one every second direction, at
 * 200 px/s for 100 px with power 10. They belong to nobody, so they hit anyone.
 */
private fun Match.fireHeavyMine(x: Int, y: Int) {
    val rockets = (0 until 16 step 2).map { direction ->
        val (fx, fy) = com.tankarena.core.LegacyDirections.unitVector(direction)
        Projectile(
            Projectile.State(
                body = PointBody(position = ScenePoint(x.toFloat(), y.toFloat())),
                ownerActorId = 0L,
                damage = HEAVY_MINE_ROCKET_POWER,
                velocityX = fx * HEAVY_MINE_ROCKET_SPEED,
                velocityY = fy * HEAVY_MINE_ROCKET_SPEED,
                ttlTicks = (HEAVY_MINE_ROCKET_RANGE_PX / HEAVY_MINE_ROCKET_SPEED).toInt(),
                weaponKind = SHOT_SMALL_ROCKET,
            ),
        )
    }
    objects.add(rockets)
    for (rocket in rockets) {
        actorIds.getOrPut(rocket) { nextActorId++ }
        pendingEvents += GameEvent.Sound(kind = com.tankarena.protocol.snapshot.SoundKind.SROCKET, x = x, y = y)
    }
}

private const val HEAVY_MINE_ROCKET_POWER = 10
/** 200 px/s, in pixels per tick. */
private const val HEAVY_MINE_ROCKET_SPEED = 2f
private const val HEAVY_MINE_ROCKET_RANGE_PX = 100f

internal fun Match.removeDeadMines() {
    val doomed = objects.all
        .filterIsInstance<Mine>()
        .filter { it.isDead && !it.detonationPending }
    if (doomed.isEmpty()) return
    objects.remove(doomed)
    for (mine in doomed) actorIds.remove(mine)
}

internal fun Match.drainMineRequests() {
    val spawns = mutableListOf<Mine>()
    for (tank in allTanks) {
        val request = tank.drainMineRequest() ?: continue
        spawns += mineFromRequest(tank, request)
    }
    if (spawns.isEmpty()) return
    objects.add(spawns)
    for (mine in spawns) actorIds.getOrPut(mine) { nextActorId++ }
}

internal fun Match.mineFromRequest(
    owner: Tank,
    request: Tank.MineRequest,
): Mine {
    val ownerActorId = actorIds[owner] ?: 0L
    val mine = Mine(
        Mine.State(
            body = PointBody(
                position = ScenePoint(
                    request.originX.toFloat(),
                    request.originY.toFloat(),
                ),
            ),
            ownerActorId = ownerActorId,
            damage = request.damage,
            radius = request.radius,
        ),
    )
    // Legacy mines are dropped silently; SoundKind.MINE is the detonation.
    return mine
}

internal fun Match.drainFireRequests() {
    val spawns = mutableListOf<Projectile>()
    for (tank in allTanks) {
        for (request in tank.drainFireRequests()) spawns += projectileFromRequest(tank, request)
    }
    for (actor in objects.all) {
        if (actor is Turret) {
            val request = actor.drainFireRequest() ?: continue
            if (request.rocketTarget != null) pendingTurretRockets += actor to request
            else spawns += projectileFromTurretRequest(actor, request)
        }
    }
    for ((soldier, shot) in pendingSoldierShots) spawns += projectileFromSoldier(soldier, shot)
    pendingSoldierShots.clear()
    if (spawns.isEmpty()) return
    objects.add(spawns)
    for (projectile in spawns) {
        actorIds.getOrPut(projectile) { nextActorId++ }
    }
}

internal fun Match.projectileFromRequest(
    owner: Tank,
    request: Tank.FireRequest,
): Projectile {
    val ownerActorId = actorIds[owner] ?: 0L
    val projectile = Projectile(
        Projectile.State(
            body = PointBody(
                position = ScenePoint(
                    request.originX.toFloat(),
                    request.originY.toFloat(),
                ),
            ),
            ownerActorId = ownerActorId,
            ownerKind = ProjectileOwnerKind.TANK,
            damage = request.damage,
            velocityX = request.velocityX,
            velocityY = request.velocityY,
            ttlTicks = request.ttlTicks,
            weaponKind = request.weaponKind,
            safetyPx = request.safetyPx,
        ),
    )
    projectile.ownerRef = owner
    
    // Emit sound event for firing
    pendingEvents += GameEvent.Sound(
        kind = soundKindForWeapon(request.weaponKind),
        x = request.originX,
        y = request.originY,
    )
    
    return projectile
}

/**
 * Legacy control_man(): create_bullet(o, m.type, ..., 5 * b_size, 0) — a soldier's round flies
 * at 200 px/s for 5 tiles, his flame like any flame.
 */
internal fun Match.projectileFromSoldier(soldier: Soldier, shot: Soldier.Shot): Projectile {
    val flame = soldier.shotKind == WEAPON_FLAME
    val speed = if (flame) 0.75f else 2f
    val range = if (flame) FLAME_RANGE_PX else 5 * LEGACY_TILE_SIZE
    val (fx, fy) = com.tankarena.core.LegacyDirections.unitVector(shot.direction)
    val projectile = Projectile(
        Projectile.State(
            body = PointBody(position = ScenePoint(shot.x.toFloat(), shot.y.toFloat())),
            ownerActorId = soldier.id,
            ownerKind = ProjectileOwnerKind.MAN,
            damage = soldier.power,
            velocityX = fx * speed,
            velocityY = fy * speed,
            ttlTicks = (range / speed).roundToInt().coerceAtLeast(1),
            weaponKind = soldier.shotKind,
        ),
    )
    projectile.ownerSoldierRef = soldier
    pendingEvents += GameEvent.Sound(kind = soundKindForWeapon(soldier.shotKind), x = shot.x, y = shot.y)
    return projectile
}

internal fun Match.projectileFromTurretRequest(
    owner: Turret,
    request: Turret.FireRequest,
): Projectile {
    val ownerActorId = actorIds[owner] ?: 0L
    val projectile = Projectile(
        Projectile.State(
            body = PointBody(
                position = ScenePoint(
                    request.originX.toFloat(),
                    request.originY.toFloat(),
                ),
            ),
            ownerActorId = ownerActorId,
            ownerKind = ProjectileOwnerKind.TURRET,
            damage = request.damage,
            velocityX = request.velocityX,
            velocityY = request.velocityY,
            ttlTicks = request.ttlTicks,
            weaponKind = if (request.chain) WEAPON_CHAIN else WEAPON_MAIN,
        ),
    )
    projectile.ownerTurretRef = owner

    // Emit sound event for turret firing
    pendingEvents += GameEvent.Sound(
        kind = soundKindForWeapon(if (request.chain) WEAPON_CHAIN else WEAPON_MAIN),
        x = request.originX,
        y = request.originY,
    )
    
    return projectile
}

/**
 * Map weapon kind constants to sound kind enum.
 */
internal fun Match.soundKindForWeapon(weaponKind: Int): com.tankarena.protocol.snapshot.SoundKind {
    return when (weaponKind) {
        WEAPON_MAIN -> com.tankarena.protocol.snapshot.SoundKind.MAIN
        WEAPON_CHAIN -> com.tankarena.protocol.snapshot.SoundKind.CHAIN
        WEAPON_ROCKET -> com.tankarena.protocol.snapshot.SoundKind.ROCKET
        WEAPON_MORTAR -> com.tankarena.protocol.snapshot.SoundKind.MORTAR
        WEAPON_FLAME -> com.tankarena.protocol.snapshot.SoundKind.FLAME
        SHOT_SMALL_ROCKET -> com.tankarena.protocol.snapshot.SoundKind.SROCKET
        else -> com.tankarena.protocol.snapshot.SoundKind.MAIN
    }
}

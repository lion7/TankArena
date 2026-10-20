package nl.tankarena.sim

import nl.tankarena.core.DetMath
import nl.tankarena.content.LEGACY_TILE_SIZE
import nl.tankarena.protocol.snapshot.EffectKind
import nl.tankarena.protocol.snapshot.GameEvent
import nl.tankarena.sim.Match.MissionStatus

// Warps, locks, destroyers and enforcers (src/objects/warp.c, lock.c, destroy.c, enforce.c).

/**
 * Legacy control_warp() (src/objects/warp.c): every vehicle (wrecks too, at any height), soldier,
 * bomber and rocket within 15 px of a warp is put down at its exit, with three sparks and the
 * teleport sound at each end. There is no pause: an exit inside another warp sends it on at once.
 */
internal fun Match.stepWarps() {
    for (warp in objects.all.filterIsInstance<Warp>()) {
        val (wx, wy) = centreOf(warp) ?: continue
        fun warps(x: Int, y: Int) = withinRadius(x, y, wx, wy, WARP_RADIUS_PX)
        fun sparks(x: Int, y: Int) = repeat(3) {
            val kind = when (objectRandom.nextInt(3)) {
                0 -> EffectKind.TELEPORT_SPARK1
                1 -> EffectKind.TELEPORT_SPARK2
                else -> EffectKind.TELEPORT_SPARK3
            }
            pendingEvents += GameEvent.Effect(kind, x - 7 + objectRandom.nextInt(16), y - 7 + objectRandom.nextInt(16))
        }
        fun teleport(fromX: Int, fromY: Int, move: () -> Unit) {
            sparks(fromX, fromY)
            sparks(warp.targetX, warp.targetY)
            move()
            pendingEvents += GameEvent.Sound(nl.tankarena.protocol.snapshot.SoundKind.TELEPORT, wx, wy)
            pendingEvents += GameEvent.Sound(nl.tankarena.protocol.snapshot.SoundKind.TELEPORT, warp.targetX, warp.targetY)
        }
        for (tank in allTanks) {
            if (!tank.wreckGone && warps(tank.positionX, tank.positionY)) {
                teleport(tank.positionX, tank.positionY) { tank.teleportTo(warp.targetX, warp.targetY) }
            }
        }
        for (soldier in soldiers) {
            if (!soldier.removed && warps(soldier.x, soldier.y)) teleport(soldier.x, soldier.y) { soldier.teleportTo(warp.targetX, warp.targetY) }
        }
        for (b52 in b52s) {
            if (b52.alive && warps(b52.positionX, b52.positionY)) {
                teleport(b52.positionX, b52.positionY) { b52.moveTo(warp.targetX.toFloat(), warp.targetY.toFloat()) }
            }
        }
        for (rocket in objects.all.filterIsInstance<Rocket>()) {
            val (rx, ry) = rocket.positionX.toInt() to rocket.positionY.toInt()
            if (!rocket.isDead && warps(rx, ry)) teleport(rx, ry) { rocket.teleportTo(warp.targetX, warp.targetY) }
        }
    }
}

private const val WARP_RADIUS_PX = 15

/** Centre of any scene object, for legacy nearest_object(). */
internal fun Match.centreOf(actor: SceneObject<*>): Pair<Int, Int>? = when (actor) {
    is Tank -> actor.positionX to actor.positionY
    is Train -> actor.positionX to actor.positionY
    is Mine -> actor.positionX to actor.positionY
    is B52 -> actor.positionX to actor.positionY
    is Zeppelin -> actor.positionX to actor.positionY
    is Placed -> {
        val body = actor.body
        if (body is BoxBody) {
            (body.position.x + body.size.width / 2f).toInt() to (body.position.y + body.size.height / 2f).toInt()
        } else {
            body.position.x.toInt() to body.position.y.toInt()
        }
    }
    else -> null
}

/** Legacy nearest_object(): the closest other object within 15 px of ([x], [y]). */
internal fun Match.nearestObject(self: SceneObject<*>, x: Int, y: Int): SceneObject<*>? {
    var best: SceneObject<*>? = null
    var bestDistSq = 15L * 15L
    for (actor in objects.all) {
        if (actor === self) continue
        val (ax, ay) = centreOf(actor) ?: continue
        val distSq = (ax - x).toLong() * (ax - x) + (ay - y).toLong() * (ay - y)
        if (distSq < bestDistSq) {
            best = actor
            bestDistSq = distSq
        }
    }
    return best
}

/**
 * Legacy prepare_lock(): a lock watches the object nearest to it (activation 1) or the
 * building under it (activation 0); it acts on the object nearest to its target point, or on
 * the structure there, which it makes indestructible until the lock blows.
 */
internal fun Match.prepareLocks() {
    locks = objects.all.filterIsInstance<Lock>()
    destroyers = objects.all.filterIsInstance<Destroyer>()
    for (lock in locks) {
        val (x, y) = centreOf(lock) ?: continue
        if (lock.activation == 1) lockOwners[lock] = nearestObject(lock, x, y)
        when (lock.target) {
            0 -> structures.makeIndestructible(Math.floorDiv(lock.lockX, LEGACY_TILE_SIZE), Math.floorDiv(lock.lockY, LEGACY_TILE_SIZE))
            1, 2 -> lockTargets[lock] = nearestObject(lock, lock.lockX, lock.lockY)
        }
    }
}

/** Legacy remove_object() for the objects a lock watches: is this one gone (or a dead vehicle)? */
internal fun Match.isGone(actor: SceneObject<*>?): Boolean = when (actor) {
    null -> true
    is Tank -> actor.armor <= 0
    is Turret -> !actor.isAlive
    is Train -> !actor.alive
    is Destroyer -> actor.isFired
    is Lock -> actor.isFired
    is Goal -> actor.isClaimed
    is Mine -> actor.isDead
    is B52 -> !actor.alive || actor.armor <= 0
    else -> false
}

/** Legacy control_lock(). */
internal fun Match.stepLocks() {
    for (lock in locks) {
        if (lock.isFired) continue
        val (x, y) = centreOf(lock) ?: continue
        val trigger = when (lock.activation) {
            0 -> !structures.isStanding(Math.floorDiv(x, LEGACY_TILE_SIZE), Math.floorDiv(y, LEGACY_TILE_SIZE))
            1 -> isGone(lockOwners[lock])
            else -> false
        }
        if (trigger) blowLock(lock)
    }
}

/**
 * Legacy blow_lock(): take the locked structure down (HT_REMOVE), destroy the target object
 * (HT_DESTROY) or simply remove it; then the lock itself is used up.
 */
internal fun Match.blowLock(lock: Lock) {
    lock.isFired = true
    when (lock.target) {
        0 -> {
            val tileX = Math.floorDiv(lock.lockX, LEGACY_TILE_SIZE)
            val tileY = Math.floorDiv(lock.lockY, LEGACY_TILE_SIZE)
            pendingEvents += GameEvent.Effect(EffectKind.EXPLOSION, lock.lockX, lock.lockY)
            pendingEvents += GameEvent.Sound(nl.tankarena.protocol.snapshot.SoundKind.EXPLODE, lock.lockX, lock.lockY)
            hitStructure(tileX, tileY, StructureLayer.SOLID, HitKind.NORMAL, StructureGrid.INDESTRUCTIBLE, EffectKind.EXPLOSION, remove = true)
        }
        1 -> lockTargets[lock]?.let(::destroyObject)
        2 -> lockTargets[lock]?.let(::removeObject)
    }
    modifyGoalCounter(lock.goalContribution)
}

/** Legacy hit_obj_for(target, ..., HT_DESTROY, 1000). */
internal fun Match.destroyObject(actor: SceneObject<*>) {
    centreOf(actor)?.let { (x, y) ->
        if (actor !is Mine) {
            pendingEvents += GameEvent.Effect(EffectKind.EXPLOSION, x, y)
            pendingEvents += GameEvent.Sound(nl.tankarena.protocol.snapshot.SoundKind.EXPLODE, x, y)
        }
    }
    when (actor) {
        is Tank -> actor.queueDestroy()
        is Train -> hitTrain(actor, StructureGrid.INDESTRUCTIBLE)
        is Destroyer -> fireDestroyer(actor)
        is Mine -> actor.detonateOn(null)
        is B52 -> hitB52(actor, StructureGrid.INDESTRUCTIBLE)
        // Turrets, goals and locks have no hit function in the original.
        else -> Unit
    }
}

/** Legacy remove_object(target): the object is simply gone (and does what it does when removed). */
internal fun Match.removeObject(actor: SceneObject<*>) {
    when (actor) {
        // Vehicles cannot leave the match here; a removed vehicle counts as destroyed.
        is Tank -> actor.queueDestroy()
        is Turret -> actor.fall()
        is Train -> removeTrain(actor)
        is Destroyer -> fireDestroyer(actor)
        is Lock -> if (!actor.isFired) {
            actor.isFired = true
            modifyGoalCounter(actor.goalContribution)
        }
        is Goal -> if (!actor.isClaimed) {
            actor.isClaimed = true
            modifyGoalCounter(actor.contribution)
        }
        is Mine -> actor.detonateOn(null)
        is B52 -> actor.alive = false
        else -> Unit
    }
}

/** Legacy prepare_destroyer(): an immediate destroyer goes off on the first tick. */
internal fun Match.stepDestroyers() {
    for (destroyer in destroyers) {
        if (!destroyer.isFired && destroyer.immediate) fireDestroyer(destroyer)
    }
}

/**
 * Legacy remove_destroyer(): flatten every destructible building (`what & 1`) and destroy
 * every vehicle, soldier and the like (`what & 2`) within the radius — HT_DESTROY, power 1000,
 * through every shield.
 */
internal fun Match.fireDestroyer(destroyer: Destroyer) {
    if (destroyer.isFired) return
    destroyer.isFired = true
    val (x, y) = centreOf(destroyer) ?: return
    val radius = destroyer.radius
    if (destroyer.what and 1 != 0) {
        var y2 = y - radius
        while (y2 < y + radius) {
            var x2 = x - radius
            while (x2 < x + radius) {
                val tileX = Math.floorDiv(x2, LEGACY_TILE_SIZE)
                val tileY = Math.floorDiv(y2, LEGACY_TILE_SIZE)
                if (withinRadius(x2, y2, x, y, radius + 16) && structures.strength(tileX, tileY, StructureLayer.SOLID) != 0) {
                    pendingEvents += GameEvent.Effect(EffectKind.EXPLOSION, x2, y2)
                    pendingEvents += GameEvent.Sound(nl.tankarena.protocol.snapshot.SoundKind.EXPLODE, x2, y2)
                    hitStructure(tileX, tileY, StructureLayer.SOLID, HitKind.NORMAL, StructureGrid.INDESTRUCTIBLE, EffectKind.EXPLOSION)
                }
                x2 += LEGACY_TILE_SIZE
            }
            y2 += LEGACY_TILE_SIZE
        }
        repeat(5) {
            val angle = 2 * Math.PI * objectRandom.nextInt(360) / 360
            pendingEvents += GameEvent.Effect(EffectKind.EXPLOSION, x + (radius * DetMath.cos(angle)).toInt(), y + (radius * DetMath.sin(angle)).toInt())
        }
    }
    if (destroyer.what and 2 != 0) {
        for (tank in allTanks) {
            if (tank.armor > 0 && tank.height > 0 && withinRadius(tank.positionX, tank.positionY, x, y, maxOf(radius, tank.spec.radius))) {
                tank.queueDestroy()
            }
        }
        blastSmallObjects(x, y, radius, power = StructureGrid.INDESTRUCTIBLE)
    }
    modifyGoalCounter(destroyer.goalContribution)
}

/**
 * Legacy control_enforcer(): computer tanks of the enforced side within the radius have the
 * weapon selected, and every `delay` ticks one of them fires it.
 */
internal fun Match.stepEnforcers() {
    for (enforcer in objects.all.filterIsInstance<Enforcer>()) {
        enforcer.cooldownTicks = enforcer.cooldownTicks.afterStep()
        val (ex, ey) = centreOf(enforcer) ?: continue
        for (tank in allTanks) {
            if (!tank.computer || tank.armor <= 0) continue
            if (!(enforcer.good && tank.team == 0) && !(enforcer.bad && tank.team != 0)) continue
            val dx = tank.positionX - ex
            val dy = tank.positionY - ey
            if (dx * dx + dy * dy >= enforcer.radius * enforcer.radius) continue
            val fire = enforcer.fire && enforcer.cooldownTicks == 0
            tank.enforce(enforcer.weapon, fire = fire, keep = enforcer.change)
            if (fire) enforcer.cooldownTicks = enforcer.delay
        }
    }
}

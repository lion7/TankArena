package nl.tankarena.sim

import nl.tankarena.core.DetMath
import nl.tankarena.content.LEGACY_TILE_SIZE
import nl.tankarena.protocol.snapshot.EffectKind
import nl.tankarena.protocol.snapshot.GameEvent

// Trains and wagons on their rails (src/objects/train.c).

/** Legacy prepare_train() with relocate(): tie each car to its neighbours and its turret. */
internal fun Match.linkTrains() {
    trains = objects.all.filterIsInstance<Train>()
    val byRef = trains.filter { it.ref != 0 }.associateBy { it.ref }
    for (car in trains) {
        car.front = byRef[car.frontRef]?.takeIf { it !== car }
        car.rear = byRef[car.rearRef]?.takeIf { it !== car }
        car.syncBody()
    }
    for (turret in objects.all.filterIsInstance<Turret>()) {
        if (turret.ownerRef == 0) continue
        val wagon = byRef[turret.ownerRef] ?: continue
        wagon.turret = turret
        ridingTurrets[turret] = wagon
        turret.moveTo(wagon.positionX, wagon.positionY)
    }
}

internal fun Match.stepTrains() {
    for (car in trains) {
        if (car.alive && car.driving) controlTrain(car)
    }
}

/** The legacy type of the base (layer 0) picture at a tile, for following the rails. */
internal fun Match.railTypeAt(tileX: Int, tileY: Int): String? {
    val index = tileX + tileY * mapMetadata.widthTiles
    return nl.tankarena.content.PictureCatalog.legacyRow(mapMetadata.world, structures.base.getOrElse(index) { -1 })?.type
}

/**
 * Legacy control_train(): the car's place follows from its speed and the time it has been on
 * its rail segment (straight, or a quarter circle of radius 49); a wagon closes up to 31 px
 * behind the car in front; the car crushes vehicles and soldiers, wrecks other trains, ploughs
 * through buildings (losing 20 px/s), and pulls the car behind it.
 */
internal fun Match.controlTrain(car: Train) {
    if (!car.alive) return
    var v = car.speed
    val dir = car.railsDir
    val turnDirection = car.direction
    val now = currentTick
    if (v > 0) {
        val elapsed = (now - car.segmentStartTick).toFloat()
        var a = 0f
        var x = 0f
        var y = 0f
        when (car.railsType) {
            RAILS_HORIZONTAL -> {
                x = v * elapsed / 100f
                car.left = (car.railsX + car.railsStart + dir * x - 16).toInt()
                car.top = car.railsY - 16
                car.direction = if (dir == 1) 4 else 12
            }
            RAILS_VERTICAL -> {
                y = v * elapsed / 100f
                car.left = car.railsX - 16
                car.top = (car.railsY + car.railsStart + dir * y - 16).toInt()
                car.direction = if (dir == 1) 8 else 0
            }
            else -> {
                a = v * elapsed / 100f / RAILS_RADIUS
                val a2 = car.railsStart - dir * a
                car.direction = directionFromRadians(a2 + if (dir == 1) 1.5 * Math.PI else 0.5 * Math.PI)
                car.left = (car.railsX + RAILS_RADIUS * DetMath.cos(a2.toDouble()) - 16).toInt()
                car.top = (car.railsY - RAILS_RADIUS * DetMath.sin(a2.toDouble()) - 16).toInt()
            }
        }
        if (a > 0.51f * Math.PI || x >= 33f || y >= 33f) {
            car.segmentStartTick = now
            setupNewRails(car, car.endX, car.endY)
        }
    }
    val front = car.front
    if (front != null && v > 0) {
        val d = DetMath.sqrt(((car.left - front.left).toDouble().let { it * it }) + (car.top - front.top).toDouble().let { it * it }).toInt() - WAGON_DISTANCE
        val dc = now - car.segmentStartTick
        val cc = (kotlin.math.abs(d).toFloat() * 100f / v / 10f).toLong()
        if (kotlin.math.abs(d) < 100) {
            if (d < -2 && dc > cc) car.segmentStartTick += cc
            else if (d > 2) car.segmentStartTick -= cc
        }
    }
    car.syncBody()
    trainCollisions(car)
    if (!car.alive) return
    val hit = IntArray(2)
    val oblock = nl.tankarena.content.PictureMasks.mask("OBLOCK")
    if (oblock != null && structures.overlap(oblock, car.left, car.top, hit) > 0) {
        if (structures.strength(hit[0], hit[1], StructureLayer.SOLID) == StructureGrid.INDESTRUCTIBLE) {
            removeTrain(car)
            return
        }
        val bx = hit[0] * LEGACY_TILE_SIZE
        val by = hit[1] * LEGACY_TILE_SIZE
        pendingEvents += GameEvent.Effect(EffectKind.EXPLOSION, bx, by)
        pendingEvents += GameEvent.Sound(nl.tankarena.protocol.snapshot.SoundKind.EXPLODE, bx, by)
        hitStructure(hit[0], hit[1], StructureLayer.SOLID, HitKind.NORMAL, StructureGrid.INDESTRUCTIBLE, EffectKind.EXPLOSION)
        v = if (v < 25) 5 else v - 20
        changeSpeed(car, v)
    }
    if (car.smokes) {
        car.smokeCooldownTicks = car.smokeCooldownTicks.afterStep()
        if (car.smokeCooldownTicks == 0) {
            car.smokeCooldownTicks = 20 + trainRandom.nextInt(20)
            val (sx, sy) = rotate(turnDirection, TRAIN_SMOKE_OFFSET)
            pendingEvents += GameEvent.Effect(
                EffectKind.ROOK,
                car.positionX + sx - 2 + trainRandom.nextInt(4),
                car.positionY + sy - 2 + trainRandom.nextInt(4),
            )
        }
    }
    car.turret?.takeIf { it.isAlive }?.moveTo(car.positionX, car.positionY)
    if (front != null) {
        changeSpeed(car, front.speed)
    } else {
        car.speedChangeCooldownTicks = car.speedChangeCooldownTicks.afterStep()
        if (car.speedChangeCooldownTicks == 0) {
            v = if (car.isEngine) minOf(v + 5, TRAIN_MAX_SPEED) else if (v > 5) v - 5 else 0
            changeSpeed(car, v)
            car.speedChangeCooldownTicks = Train.SPEED_CHANGE_TICKS
        }
    }
    car.rear?.let(::controlTrain)
}

/** Legacy change_speed(): keep the car where it is on its segment while its speed changes. */
internal fun Match.changeSpeed(car: Train, newSpeed: Int) {
    val old = car.speed
    car.speed = newSpeed
    if (newSpeed == 0) return
    if (old == 0) car.segmentStartTick = currentTick
    val dc = ((currentTick - car.segmentStartTick).toFloat() * old / newSpeed).toLong()
    car.segmentStartTick = currentTick - dc
}

/** Legacy radtoa16(in2pi(a)). */
internal fun Match.directionFromRadians(angle: Double): Int {
    val a = ((angle % (2 * Math.PI)) + 2 * Math.PI) % (2 * Math.PI)
    return ((Math.PI / 2 - a) / (2 * Math.PI / 16)).let { Math.round(it).toInt() }.mod(16)
}

/**
 * Legacy setup_new_rails(): look at the rail picture on the next tile (wrapping round the map)
 * and set up the straight piece or quarter circle the car runs along next. A car that runs
 * out of rails (or onto a destroyed bridge) stops for good.
 */
internal fun Match.setupNewRails(car: Train, tileX0: Int, tileY0: Int) {
    val tileX = if (tileX0 < 0) mapMetadata.widthTiles - 1 else if (tileX0 >= mapMetadata.widthTiles) 0 else tileX0
    val tileY = if (tileY0 < 0) mapMetadata.heightTiles - 1 else if (tileY0 >= mapMetadata.heightTiles) 0 else tileY0
    car.endX = tileX
    car.endY = tileY
    val b = LEGACY_TILE_SIZE
    fun straight(type: Int) {
        car.railsType = type
        car.railsDir = car.endDir
        car.railsStart = if (car.endDir == 1) -16f else 16f
        car.railsX = tileX * b + 16
        car.railsY = tileY * b + 16
        if (type == RAILS_HORIZONTAL) car.endX += car.railsDir else car.endY += car.railsDir
    }
    fun curve(start: Double, cx: Int, cy: Int, dir: Int, endType: Int, endDir: Int, endX: Int, endY: Int) {
        car.railsType = RAILS_CIRCLE
        car.railsStart = start.toFloat()
        car.railsX = cx
        car.railsY = cy
        car.railsDir = dir
        car.endType = endType
        car.endDir = endDir
        car.endX = endX
        car.endY = endY
    }
    val pi = Math.PI
    val x = tileX * b
    val y = tileY * b
    when (railTypeAt(tileX, tileY)) {
        "RAILS_H", "BRIDGE_H" -> straight(RAILS_HORIZONTAL)
        "RAILS_V", "RAILS_S_B", "RAILS_S_D", "RAILS_W_A", "RAILS_W_C", "BRIDGE_V" -> straight(RAILS_VERTICAL)
        "RAILS_CR" -> straight(car.endType)
        "RAILS_TL_B", "RAILS_W_B" -> curve(0.5 * pi, x + b, y + 2 * b, -1, RAILS_VERTICAL, 1, tileX - 1, tileY + 2)
        "RAILS_TL_C" -> curve(pi, x + 2 * b, y + b, 1, RAILS_HORIZONTAL, 1, tileX + 2, tileY - 1)
        "RAILS_TR_A", "RAILS_S_A" -> curve(0.5 * pi, x, y + 2 * b, 1, RAILS_VERTICAL, 1, tileX + 1, tileY + 2)
        "RAILS_TR_D" -> curve(0.0, x - b, y + b, -1, RAILS_HORIZONTAL, -1, tileX - 2, tileY - 1)
        "RAILS_BL_A" -> curve(pi, x + 2 * b, y, -1, RAILS_HORIZONTAL, 1, tileX + 2, tileY + 1)
        "RAILS_BL_D" -> curve(1.5 * pi, x + b, y - b, 1, RAILS_VERTICAL, -1, tileX - 1, tileY - 2)
        "RAILS_BR_B" -> curve(0.0, x - b, y, 1, RAILS_HORIZONTAL, -1, tileX - 2, tileY + 1)
        "RAILS_BR_C" -> curve(1.5 * pi, x, y - b, -1, RAILS_VERTICAL, -1, tileX + 1, tileY - 2)
        else -> {
            car.driving = false
            car.speed = 0
        }
    }
}

/**
 * Legacy check_obj_collide() for a train car: it sets off mines, kills soldiers, destroys
 * vehicles at about its height and wrecks other trains that are not its neighbours.
 */
internal fun Match.trainCollisions(car: Train) {
    val r = Train.RADIUS
    for (tank in allTanks) {
        if (tank.armor <= 0 || kotlin.math.abs(tank.height - 1) >= 2) continue
        val reach = r + tank.spec.radius
        val dx = tank.positionX - car.positionX
        val dy = tank.positionY - car.positionY
        if (dx * dx + dy * dy >= reach * reach) continue
        val mx = (car.positionX + tank.positionX) / 2
        val my = (car.positionY + tank.positionY) / 2
        pendingEvents += GameEvent.Effect(EffectKind.EXPLOSION, mx, my)
        pendingEvents += GameEvent.Sound(nl.tankarena.protocol.snapshot.SoundKind.EXPLODE, mx, my)
        tank.queueDestroy()
    }
    for (other in trains) {
        if (other === car || !other.alive || other === car.front || other === car.rear) continue
        val dx = other.positionX - car.positionX
        val dy = other.positionY - car.positionY
        if (dx * dx + dy * dy < (2 * r) * (2 * r)) removeTrain(other)
    }
    for (mine in objects.all) {
        if (mine !is Mine || !mine.isActive) continue
        val reach = r + mine.triggerReach
        val dx = mine.positionX - car.positionX
        val dy = mine.positionY - car.positionY
        if (dx * dx + dy * dy < reach * reach) mine.detonateOn(null)
    }
    for (soldier in soldiers) {
        if (!soldier.removed && withinRadius(soldier.x, soldier.y, car.positionX, car.positionY, r + SOLDIER_RADIUS_PX)) removeSoldier(soldier)
    }
    soldiers.removeAll { it.removed }
}

/** Legacy hit_train(). */
internal fun Match.hitTrain(car: Train, power: Int) {
    if (!car.alive) return
    car.armor -= power
    if (car.armor <= 0) removeTrain(car)
}

/**
 * Legacy remove_train(): the car explodes, its turret goes with it, an A-bomb wagon goes off,
 * and the car behind it now leads what is left of the train.
 */
internal fun Match.removeTrain(car: Train) {
    if (!car.alive) return
    car.alive = false
    car.armor = 0
    car.turret?.fall()
    if (car.picture == ABOMB_WAGON) abombs += Abomb(nextActorId++, car.positionX, car.positionY, countdown = 0, owner = null)
    repeat(5) {
        pendingEvents += GameEvent.Effect(EffectKind.EXPLOSION, car.positionX - 8 + trainRandom.nextInt(16), car.positionY - 8 + trainRandom.nextInt(16))
    }
    modifyGoalCounter(car.goalContribution)
    car.goalContribution = 0
    car.front?.let { if (it.rear === car) it.rear = null }
    car.rear?.let {
        it.driving = true
        it.front = null
    }
}

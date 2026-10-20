package com.tankarena.sim

import com.tankarena.protocol.snapshot.GameEvent
import com.tankarena.sim.Match.MissionStatus

// Goals, products, the goal counter, tank losses and control hand-over, and how a mission ends
// (src/objects/goal.c, product.c, objects.c, src/tanks/tank.c, src/game/controls.c).

/**
 * Legacy check_obj_collide() and pickup_product() (src/check/collide.c, src/objects/product.c): a
 * human-driven vehicle on the ground that touches a product buys it. Overspending fails the mission.
 */
internal fun Match.collectProducts() {
    if (missionStatus != MissionStatus.IN_PROGRESS) return
    val tanks = objects.all.filterIsInstance<Tank>()
    for (product in objects.all.filterIsInstance<Product>()) {
        if (product.isCollected) continue
        val size = product.body.size
        val pos = product.body.position
        val pcx = (pos.x + size.width / 2f).toInt()
        val pcy = (pos.y + size.height / 2f).toInt()
        val pickup = tanks.firstOrNull { tank ->
            !tank.computer && tank.height < 2 &&
                withinRadius(tank.positionX, tank.positionY, pcx, pcy, tank.spec.radius + PRODUCT_RADIUS_PX)
        } ?: continue
        product.isCollected = true
        mayhemCash -= product.price
        if (mayhemCash < 0) {
            mayhemCash = 0
            modifyGoalCounter(-100)
        }
        if (mayhemProductsNeeded > 0) mayhemProductsNeeded -= 1
        productCounts[product.productType.coerceIn(0, productCounts.size - 1)] += 1
        pickup.showMessage("PICKED UP", PRODUCT_NAMES.getOrElse(product.productType) { "" })
        pendingEvents += GameEvent.ProductCollected(
            productActorId = actorIds[product] ?: 0L,
            tankActorId = actorIds[pickup]!!,
            price = product.price,
        )
    }
}

/** Legacy load_map() `relocate(&o->target)`: goals with `who` 3 find the vehicle they wait for. */
internal fun Match.linkGoals() {
    val byRef = allTanks.filter { it.ref != 0 }.associateBy { it.ref }
    for (goal in objects.all.filterIsInstance<Goal>()) {
        if (goal.targetRef != 0) goal.target = byRef[goal.targetRef]
    }
}

/**
 * Legacy control_player() and hit_wreck() (src/objects/player.c, src/tanks/control.c): a respawn
 * leaves the old wreck on the map; while the round is running, a wreck that is hit or has burnt for
 * [WRECK_LIFE_TICKS] blows up and is gone.
 */
internal fun Match.manageWrecks() {
    for (tank in allTanks) {
        val wreck = tank.drainLeftWreck() ?: continue
        wreck.configureForMatch(
            maxX = worldWidthPixels - com.tankarena.content.LEGACY_TILE_SIZE,
            maxY = worldHeightPixels - com.tankarena.content.LEGACY_TILE_SIZE,
            dual = mode == MatchMode.DUAL,
            night = mapMetadata.night,
            cheats = cheats,
        )
        wreck.surroundings = tank.surroundings
        objects.add(listOf(wreck))
        allTanks = allTanks + wreck
        actorIds.getOrPut(wreck) { nextActorId++ }
    }
    val roundRunning = if (mode == MatchMode.DUAL) dualRules?.winner == -1 else missionStatus == MissionStatus.IN_PROGRESS
    for (tank in allTanks.filter { it.wreckBlowUp }) {
        tank.wreckBlowUp = false
        if (!roundRunning) continue
        repeat(10) {
            pendingEvents += GameEvent.Effect(
                com.tankarena.protocol.snapshot.EffectKind.EXPLOSION,
                tank.positionX - 8 + objectRandom.nextInt(16),
                tank.positionY - 8 + objectRandom.nextInt(16),
            )
        }
        pendingEvents += GameEvent.Sound(com.tankarena.protocol.snapshot.SoundKind.EXPLODE, tank.positionX, tank.positionY)
        tank.removeWreck()
        // A player's own vehicle waits (unseen) for its respawn in player vs player; elsewhere a
        // player who was looking at the wreck has nothing left to see (static, legacy hit_wreck()).
        val viewers = tanksByPlayerIndex.filterValues { it === tank }.keys
        if (mode == MatchMode.DUAL && viewers.isNotEmpty()) continue
        viewers.forEach { tanksByPlayerIndex.remove(it) }
        if (tank.playerIndex >= 0) tank.playerIndex = -1
        objects.remove(listOf(tank))
        allTanks = allTanks - tank
    }
}

internal fun Match.collectGoals() {
    if (missionStatus != MissionStatus.IN_PROGRESS) return
    val tanks = objects.all.filterIsInstance<Tank>().filter { !it.wreckGone }
    for (actor in objects.all) {
        if (actor !is Goal || actor.isClaimed) continue
        val position = actor.body.position
        val size = actor.body.size
        val cx = (position.x + size.width / 2f).toInt()
        val cy = (position.y + size.height / 2f).toInt()
        // Legacy control_goal(): who=0 is reached by a good vehicle, who=1 by an evil one (wrecks
        // too), who=2 by a train and who=3 by the linked vehicle while it is on the map.
        val reached = when (actor.who) {
            // In supermarket mayhem the way out opens once everything is bought.
            0 -> (!mayhem || mayhemProductsNeeded == 0) && tanks.any { it.team == 0 && withinRadius(it.positionX, it.positionY, cx, cy, actor.radius) }
            1 -> tanks.any { it.team == 1 && withinRadius(it.positionX, it.positionY, cx, cy, actor.radius) }
            2 -> objects.all.filterIsInstance<Train>()
                .any { it.alive && withinRadius(it.positionX, it.positionY, cx, cy, actor.radius) }
            3 -> actor.target?.takeIf { it in tanks }?.let { withinRadius(it.positionX, it.positionY, cx, cy, actor.radius) } == true
            else -> false
        }
        if (!reached) continue
        actor.isClaimed = true
        if (actor.contribution > 0) {
            missionCaptures++
            missionScore += 500
        }
        modifyGoalCounter(actor.contribution)
    }
}

internal fun Match.drainTankLifecycleEvents() {
    for (actor in objects.all) {
        if (actor !is Tank) continue
        val id = actorIds[actor] ?: continue
        val events = actor.drainLifecycleEvents(id)
        if (events.isNotEmpty()) {
            pendingEvents.addAll(events)
            // Track kills: when an enemy tank is destroyed, count it
            for (e in events) {
                if (e is GameEvent.DamageTaken || e is GameEvent.TankDestroyed) announceHit(actor)
                if (e !is GameEvent.TankDestroyed) continue
                // Legacy hit_tank(): every other side's tanks may refill at their base again.
                for (other in allTanks) {
                    if (other.playerIndex != actor.playerIndex) other.allowBaseRefill()
                }
                if (actor.team == 1) {
                    missionKills++
                    missionScore += 1000
                }
                onTankDestroyed(actor)
            }
        }
    }
}

/**
 * Legacy hit_tank(): outside player-vs-player, the players hear when one of their other
 * drivable tanks (those with an F1–F10 id) is hit.
 */
internal fun Match.announceHit(tank: Tank) {
    if (mode == MatchMode.DUAL || tank.controllerId < 0) return
    for (viewed in tanksByPlayerIndex.values) {
        if (viewed !== tank) viewed.showMessage("TANK ${tank.controllerId} HAS", "BEEN HIT!")
    }
}

/**
 * Legacy hit_tank(): a destroyed tank applies its goal contribution, except that losing the
 * last human-drivable tank costs the whole mission (-100).
 */
internal fun Match.onTankDestroyed(tank: Tank) {
    if (tank.lives > 0) return // respawns at its pad; the goal counter only counts final deaths
    val isHumanTank = tank.playerIndex >= 0 || (!tank.computer && tank.team == 0)
    if (isHumanTank) {
        val othersAlive = objects.all.filterIsInstance<Tank>().any { other ->
            other !== tank && other.armor > 0 && (other.playerIndex >= 0 || (!other.computer && other.team == 0))
        }
        if (!othersAlive) {
            lossReason = "ALL_LIVES_LOST"
            modifyGoalCounter(-100)
            return
        }
    }
    modifyGoalCounter(tank.goalContribution)
    tank.goalContribution = 0
}

internal fun Match.drainTurretDestructions() {
    for (turret in objects.all) {
        if (turret !is Turret || !turret.drainDestroyed()) continue
        val id = actorIds[turret] ?: continue
        pendingEvents += GameEvent.TurretDestroyed(id)
        if (turret.team == 1) {
            missionKills++
            missionScore += 500
        }
        modifyGoalCounter(turret.goalContribution)
        turret.goalContribution = 0
    }
}

/** Legacy modify_goal_counter(): positive percentages feed the good counter, negative the bad one. */
/**
 * Legacy write_timeleft() (src/world/status.c): in one player and two players vs computer, the
 * shopping time running out fails the mission.
 */
internal fun Match.checkMayhemTime() {
    if (!mayhem || mode == MatchMode.DUAL || missionStatus != MissionStatus.IN_PROGRESS) return
    if (currentTick >= MAYHEM_TICKS) modifyGoalCounter(-100)
}

/** What the mayhem status display shows (legacy status.c), or null outside supermarket mayhem. */
internal fun Match.mayhemHud(): com.tankarena.protocol.snapshot.MayhemHud? =
    if (!mayhem || mode == MatchMode.DUAL) {
        null
    } else {
        com.tankarena.protocol.snapshot.MayhemHud(
            productsNeeded = mayhemProductsNeeded,
            ticksLeft = (MAYHEM_TICKS - currentTick).coerceAtLeast(0),
            cash = mayhemCash,
            counts = productCounts.toList(),
        )
    }

internal fun Match.modifyGoalCounter(percentage: Int) {
    if (mode == MatchMode.DUAL || missionStatus != MissionStatus.IN_PROGRESS) return
    // Legacy modify_goal_counter() with -NOFAIL: nothing counts against the mission.
    if (cheats.noFail && percentage < 0) return
    when {
        percentage > 0 -> goalGood = (goalGood + percentage).coerceAtMost(100)
        percentage < 0 -> goalBad = (goalBad - percentage).coerceAtMost(100)
    }
}

/**
 * Legacy control.c: the camera follows another living tank of the player's group, and when
 * the whole group is gone for good control moves to another group of human-drivable tanks.
 */
internal fun Match.handOverDeadPlayers() {
    val tanks = allTanks
    for ((playerIndex, primary) in tanksByPlayerIndex.toMap()) {
        if (primary.armor > 0 && primary.playerIndex == playerIndex) continue
        val group = tanks.filter { it.playerIndex == playerIndex }
        group.firstOrNull { it.armor > 0 }?.let {
            tanksByPlayerIndex[playerIndex] = it
            continue
        }
        if (group.any { it.lives > 0 }) continue // a pad tank is waiting to respawn
        if (mode == MatchMode.DUAL || missionStatus != MissionStatus.IN_PROGRESS) continue
        val nextId = tanks
            .filter { it.armor > 0 && it.playerIndex < 0 && !it.computer && it.team == 0 && it.controllerId >= 0 }
            .minOfOrNull { it.controllerId }
            ?: continue
        transferControl(playerIndex, nextId, tanks)
    }
}

/**
 * Legacy read_controls() F1–F10 in one player and two players vs computer: the player looks at and
 * drives the human vehicles with that id (a wreck too). With none, the window shows static. In two
 * players vs computer an id the other player has picked is refused.
 */
internal fun Match.switchToTank(playerIndex: Int, controllerId: Int) {
    if (mode == MatchMode.DUAL || missionStatus != MissionStatus.IN_PROGRESS) return
    if (players.any { it != playerIndex && selectedIds[it] == controllerId }) return
    val current = tanksByPlayerIndex[playerIndex]
    if (current != null && current.controllerId == controllerId && current.armor > 0) return
    selectedIds[playerIndex] = controllerId
    for (tank in allTanks) {
        if (tank.playerIndex == playerIndex) tank.playerIndex = -1
    }
    val group = allTanks.filter { it.controllerId == controllerId && it.playerIndex < 0 && !it.computer && it.team == 0 && !it.wreckGone }
    group.forEach { it.playerIndex = playerIndex }
    val primary = group.firstOrNull { it.armor > 0 } ?: group.lastOrNull()
    if (primary == null) {
        tanksByPlayerIndex.remove(playerIndex)
        return
    }
    tanksByPlayerIndex[playerIndex] = primary
    pendingEvents += GameEvent.ControlSwitched(playerIndex, actorIds[primary] ?: 0L)
}

internal fun Match.transferControl(playerIndex: Int, controllerId: Int, tanks: List<Tank>) {
    for (tank in tanks) {
        if (tank.playerIndex == playerIndex) tank.playerIndex = -1
    }
    val group = tanks.filter { it.controllerId == controllerId && it.playerIndex < 0 && !it.computer && it.team == 0 }
    group.forEach { it.playerIndex = playerIndex }
    val primary = group.firstOrNull { it.armor > 0 } ?: return
    tanksByPlayerIndex[playerIndex] = primary
    selectedIds[playerIndex] = controllerId
    pendingEvents += GameEvent.ControlSwitched(playerIndex, actorIds[primary] ?: 0L)
}

internal fun Match.evaluateMission() {
    if (missionStatus != MissionStatus.IN_PROGRESS) return
    val all = objects.all
    val tanks = all.filterIsInstance<Tank>()

    if (mode == MatchMode.DUAL) {
        val rules = dualRules ?: return
        val flags = all.filterIsInstance<Flag>()
        val decided = rules.step(allTanks, flags) { car ->
            pendingEvents += GameEvent.Sound(com.tankarena.protocol.snapshot.SoundKind.BEEP, car.positionX, car.positionY)
        }
        if (decided) {
            pendingEvents += GameEvent.RoundWon(rules.winner)
            endMission(rules.winner == 0, "ROUND_WON_BY_PLAYER_${rules.winner + 1}")
        }
        return
    }

    // Legacy modify_goal_counter(): 100% good wins, 100% bad loses.
    if (goalGood >= 100) {
        endMission(true, "GOALS_CAPTURED")
        return
    }
    if (goalBad >= 100) {
        endMission(false, lossReason)
        return
    }

    // Maps whose object contributions cannot reach 100% on their own (structure goals are
    // not modelled yet, and hand-built fixtures carry none) fall back to "destroy everything".
    if (!goalCounterReachable) {
        val enemyTanks = tanks.filter { it.team == 1 }
        val enemyTurrets = all.filterIsInstance<Turret>().filter { it.team == 1 }
        val hasEnemies = enemyTanks.isNotEmpty() || enemyTurrets.isNotEmpty()
        if (hasEnemies && enemyTanks.all { it.armor <= 0 && it.lives <= 0 } && enemyTurrets.all { !it.isAlive }) {
            endMission(true, "ALL_ENEMIES_ELIMINATED")
            return
        }
    }

    val humanTanks = tanks.filter { it.playerIndex >= 0 || (!it.computer && it.team == 0) }
    if (humanTanks.isNotEmpty() && humanTanks.all { it.armor <= 0 && it.lives <= 0 }) {
        endMission(false, "ALL_LIVES_LOST")
    }
}

/**
 * End the mission with a win/loss result and emit MissionResult event.
 */
internal fun Match.endMission(won: Boolean, reason: String) {
    missionStatus = if (won) MissionStatus.WON else MissionStatus.LOST
    if (won) {
        pendingEvents += GameEvent.MissionWon(playerId = 0, reason = reason)
    } else {
        pendingEvents += GameEvent.MissionLost(playerId = 0, reason = reason)
    }
    // Emit MissionResult once
    if (!missionResultEmitted) {
        missionResultEmitted = true
        pendingEvents += GameEvent.MissionResult(
            won = won,
            reason = reason,
            score = missionScore,
            kills = missionKills,
            captures = missionCaptures,
            ticks = currentTick,
        )
    }
}

/** Legacy in_radius(): strictly closer than [radius] (`dx*dx + dy*dy < r*r`). */
internal fun Match.withinRadius(ax: Int, ay: Int, bx: Int, by: Int, radius: Int): Boolean {
    val dx = (ax - bx).toLong()
    val dy = (ay - by).toLong()
    val r = radius.toLong()
    return dx * dx + dy * dy < r * r
}

package com.tankarena.sim

import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.WeaponType
import com.tankarena.core.Int2
import com.tankarena.core.wrapCoordinate
import com.tankarena.input.PlayerIntentFrame
import kotlin.math.abs
import kotlin.math.sign

data class SimulationResult(
    val previous: WorldState,
    val current: WorldState,
    val events: List<SimulationEvent>,
)

sealed interface SimulationEvent {
    data class FireProjectile(
        val ownerId: Long,
        val ownerKind: ProjectileOwnerKind,
        val origin: Int2,
        val velocity: Int2,
        val damage: Int,
    ) : SimulationEvent

    data class Explosion(val position: Int2) : SimulationEvent
    data class TankHit(val tankId: Long, val damage: Int) : SimulationEvent
    data class TankDestroyed(val tankId: Long) : SimulationEvent
    data class TankRespawned(val tankId: Long) : SimulationEvent
    data class GoalReached(val goalId: Long, val tankId: Long, val contribution: Int) : SimulationEvent
    data object MissionWon : SimulationEvent
    data object MissionLost : SimulationEvent
}

private const val TANK_HALF = LEGACY_TILE_SIZE / 2 - 2
private const val PRIMARY_COOLDOWN_TICKS = 8
private const val PROJECTILE_TTL_TICKS = 50
private const val PROJECTILE_SPEED = 8
private const val RESPAWN_TICKS = 60
private const val INITIAL_ARMOR = 100
private const val INITIAL_FUEL = 100

class TankArenaSimulation(
    initialState: WorldState,
    private val passability: Passability? = null,
    private val spawnPoints: Map<Long, Int2> = emptyMap(),
) {
    private var state: WorldState = initialState
    private var nextProjectileId: Long = 10_000

    fun currentState(): WorldState = state

    fun tick(playerInputs: Map<Int, PlayerIntentFrame>): SimulationResult {
        val previous = state
        val events = mutableListOf<SimulationEvent>()

        // 1. Update tanks (movement, body/turret facing, primary fire intent).
        val updatedTanks = state.tanks.map { tank ->
            updateTank(tank, playerInputs[tank.playerIndex] ?: PlayerIntentFrame(), events)
        }

        // 2. Update map turrets (rotate toward target, fire when ready).
        val updatedTurrets = state.turrets.map { turret ->
            updateTurret(turret, updatedTanks, events)
        }

        // 3. Spawn projectiles emitted by tanks/turrets this tick.
        val spawnedProjectiles = events.filterIsInstance<SimulationEvent.FireProjectile>().map { event ->
            ProjectileState(
                id = nextProjectileId++,
                ownerId = event.ownerId,
                ownerKind = event.ownerKind,
                position = event.origin,
                velocity = event.velocity,
                ttlTicks = PROJECTILE_TTL_TICKS,
                damage = event.damage,
            )
        }

        // 4. Step projectiles, resolving collisions vs terrain and tanks.
        val (movedProjectiles, tanksAfterHits) = stepProjectiles(
            projectiles = state.projectiles + spawnedProjectiles,
            tanks = updatedTanks,
            events = events,
        )

        // 5. Apply death and respawn lifecycle.
        val tanksAfterLifecycle = tanksAfterHits.map { tank -> applyLifecycle(tank, events) }

        // 6. Resolve goal pickups against the post-movement tanks.
        val (updatedGoals, missionAfterGoals) = collectGoals(
            goals = state.goals,
            tanks = tanksAfterLifecycle,
            mission = state.mission,
            events = events,
        )

        // 7. Evaluate mission win/loss conditions.
        val finalMission = evaluateMission(missionAfterGoals, tanksAfterLifecycle, events)

        state = state.copy(
            tick = state.tick + 1,
            tanks = tanksAfterLifecycle,
            turrets = updatedTurrets,
            projectiles = movedProjectiles,
            goals = updatedGoals,
            mission = finalMission,
        )
        return SimulationResult(previous = previous, current = state, events = events)
    }

    private fun collectGoals(
        goals: List<GoalState>,
        tanks: List<TankState>,
        mission: MissionProgress,
        events: MutableList<SimulationEvent>,
    ): Pair<List<GoalState>, MissionProgress> {
        if (goals.isEmpty()) return goals to mission
        if (mission.status != MissionStatus.IN_PROGRESS) return goals to mission

        var goalGood = mission.goalGood
        var goalBad = mission.goalBad
        val updated = goals.map { goal ->
            if (goal.isClaimed) return@map goal
            // For now act on "good" goals (who == 0); other variants are imported
            // but not yet wired into a behavior, mirroring the legacy modify_goal_counter.
            val claimingTank = tanks.firstOrNull { tank ->
                tank.isAlive && tank.playerIndex >= 0 && withinRadius(tank.position, goal.position, goal.radius)
            }
            if (claimingTank == null) return@map goal
            events += SimulationEvent.GoalReached(
                goalId = goal.id,
                tankId = claimingTank.id,
                contribution = goal.contribution,
            )
            when (goal.who) {
                0 -> goalGood = (goalGood + goal.contribution).coerceIn(0, 100)
                else -> goalBad = (goalBad + goal.contribution).coerceIn(0, 100)
            }
            goal.copy(isClaimed = true)
        }
        return updated to mission.copy(goalGood = goalGood, goalBad = goalBad)
    }

    private fun evaluateMission(
        mission: MissionProgress,
        tanks: List<TankState>,
        events: MutableList<SimulationEvent>,
    ): MissionProgress {
        if (mission.status != MissionStatus.IN_PROGRESS) return mission
        if (mission.goalGood >= 100) {
            events += SimulationEvent.MissionWon
            return mission.copy(status = MissionStatus.WON)
        }
        val playerTanks = tanks.filter { it.playerIndex >= 0 }
        if (playerTanks.isNotEmpty() && playerTanks.all { !it.isAlive && it.lives <= 0 }) {
            events += SimulationEvent.MissionLost
            return mission.copy(status = MissionStatus.LOST)
        }
        return mission
    }

    private fun withinRadius(a: Int2, b: Int2, radius: Int): Boolean {
        val dx = (a.x - b.x).toLong()
        val dy = (a.y - b.y).toLong()
        val r = radius.toLong()
        return dx * dx + dy * dy <= r * r
    }

    private fun updateTank(
        tank: TankState,
        input: PlayerIntentFrame,
        events: MutableList<SimulationEvent>,
    ): TankState {
        if (!tank.isAlive) {
            return tank
        }

        // Body facing follows movement intent only (steer/throttle).
        val bodyFacing = when {
            input.steer != 0 || input.throttle != 0 -> Int2(input.steer.sign, input.throttle.sign)
            else -> tank.facing
        }

        // Turret facing follows aim intent; falls back to previous turret facing when no aim.
        val turretFacing = when {
            input.aimX != 0 || input.aimY != 0 -> Int2(input.aimX.sign, input.aimY.sign)
            else -> tank.turretFacing
        }

        val velocity = Int2(
            x = input.steer.coerceIn(-1, 1) * 3,
            y = input.throttle.coerceIn(-1, 1) * 4,
        )

        val nextPosition = resolveMove(
            from = tank.position,
            delta = velocity,
            halfW = TANK_HALF,
            halfH = TANK_HALF,
        )

        var primaryCooldown = (tank.primaryCooldownTicks - 1).coerceAtLeast(0)
        if (input.firePrimary && primaryCooldown == 0) {
            val (vx, vy) = projectileVelocity(turretFacing)
            // Spawn projectile slightly outside the tank so it does not immediately collide with the firer.
            val origin = Int2(
                x = nextPosition.x + turretFacing.x * (TANK_HALF + 2),
                y = nextPosition.y + turretFacing.y * (TANK_HALF + 2),
            )
            events += SimulationEvent.FireProjectile(
                ownerId = tank.id,
                ownerKind = ProjectileOwnerKind.TANK,
                origin = origin,
                velocity = Int2(vx, vy),
                damage = 25,
            )
            primaryCooldown = PRIMARY_COOLDOWN_TICKS
        }

        return tank.copy(
            position = nextPosition,
            facing = bodyFacing,
            turretFacing = turretFacing,
            velocity = velocity,
            primaryCooldownTicks = primaryCooldown,
        )
    }

    private fun updateTurret(
        turret: TurretState,
        tanks: List<TankState>,
        events: MutableList<SimulationEvent>,
    ): TurretState {
        val target = nearestAliveTankInRange(turret, tanks)
        var direction = turret.direction
        var cooldown = (turret.cooldownTicks - 1).coerceAtLeast(0)

        if (target != null) {
            val desired = directionToTarget(turret.position, target.position)
            direction = stepDirection(direction, desired)
            if (cooldown == 0 && direction == desired) {
                val (vx, vy) = projectileVelocityFromDirection(direction)
                val origin = Int2(
                    x = turret.position.x + vx * 2,
                    y = turret.position.y + vy * 2,
                )
                events += SimulationEvent.FireProjectile(
                    ownerId = turret.id,
                    ownerKind = ProjectileOwnerKind.TURRET,
                    origin = origin,
                    velocity = Int2(vx, vy),
                    damage = turret.damage,
                )
                cooldown = turret.fireDelayTicks
            }
        }

        return turret.copy(direction = direction, cooldownTicks = cooldown)
    }

    private fun nearestAliveTankInRange(turret: TurretState, tanks: List<TankState>): TankState? {
        var best: TankState? = null
        var bestDistSq = Int.MAX_VALUE
        val rangeSq = turret.rangePixels.toLong() * turret.rangePixels.toLong()
        for (tank in tanks) {
            if (!tank.isAlive) continue
            val dx = tank.position.x - turret.position.x
            val dy = tank.position.y - turret.position.y
            val distSq = dx * dx + dy * dy
            if (distSq <= rangeSq && distSq < bestDistSq) {
                best = tank
                bestDistSq = distSq
            }
        }
        return best
    }

    private fun stepProjectiles(
        projectiles: List<ProjectileState>,
        tanks: List<TankState>,
        events: MutableList<SimulationEvent>,
    ): Pair<List<ProjectileState>, List<TankState>> {
        val mutableTanks = tanks.toMutableList()
        val survivors = mutableListOf<ProjectileState>()

        for (projectile in projectiles) {
            val nextX = projectile.position.x + projectile.velocity.x
            val nextY = projectile.position.y + projectile.velocity.y
            val nextPos = if (passability != null) {
                Int2(nextX, nextY)
            } else {
                Int2(
                    x = wrapCoordinate(nextX, state.bounds.widthPixels),
                    y = wrapCoordinate(nextY, state.bounds.heightPixels),
                )
            }

            val hitsWall = passability?.isSolidPixel(nextPos.x, nextPos.y) == true ||
                outOfBounds(nextPos)
            if (hitsWall) {
                events += SimulationEvent.Explosion(nextPos)
                continue
            }

            val tankIdx = findTankAabbHit(nextPos, mutableTanks, ignoreOwnerId = projectile.ownerId, ignoreOwnerKind = projectile.ownerKind)
            if (tankIdx != null) {
                val target = mutableTanks[tankIdx]
                val newArmor = (target.armor - projectile.damage).coerceAtLeast(0)
                mutableTanks[tankIdx] = target.copy(armor = newArmor)
                events += SimulationEvent.TankHit(tankId = target.id, damage = projectile.damage)
                events += SimulationEvent.Explosion(nextPos)
                continue
            }

            val nextTtl = projectile.ttlTicks - 1
            if (nextTtl <= 0) {
                events += SimulationEvent.Explosion(nextPos)
                continue
            }

            survivors += projectile.copy(position = nextPos, ttlTicks = nextTtl)
        }
        return survivors to mutableTanks
    }

    private fun findTankAabbHit(
        position: Int2,
        tanks: List<TankState>,
        ignoreOwnerId: Long,
        ignoreOwnerKind: ProjectileOwnerKind,
    ): Int? {
        for (i in tanks.indices) {
            val tank = tanks[i]
            if (!tank.isAlive) continue
            // Tank-fired projectiles ignore their own tank; turret projectiles can hit any tank.
            if (ignoreOwnerKind == ProjectileOwnerKind.TANK && tank.id == ignoreOwnerId) continue
            if (abs(position.x - tank.position.x) <= TANK_HALF &&
                abs(position.y - tank.position.y) <= TANK_HALF
            ) {
                return i
            }
        }
        return null
    }

    private fun applyLifecycle(tank: TankState, events: MutableList<SimulationEvent>): TankState {
        if (tank.isAlive && tank.armor <= 0) {
            events += SimulationEvent.TankDestroyed(tank.id)
            val newLives = (tank.lives - 1).coerceAtLeast(0)
            return if (newLives > 0) {
                tank.copy(isAlive = false, respawnInTicks = RESPAWN_TICKS, lives = newLives)
            } else {
                // Permanently dead: sentinel respawnInTicks = -1 prevents the
                // respawn countdown from ever firing again.
                tank.copy(isAlive = false, respawnInTicks = -1, lives = 0)
            }
        }
        if (!tank.isAlive) {
            if (tank.respawnInTicks <= 0) return tank
            val remaining = tank.respawnInTicks - 1
            if (remaining <= 0) {
                val spawn = spawnPoints[tank.id] ?: tank.position
                events += SimulationEvent.TankRespawned(tank.id)
                return tank.copy(
                    isAlive = true,
                    armor = INITIAL_ARMOR,
                    fuel = INITIAL_FUEL,
                    position = spawn,
                    velocity = Int2(0, 0),
                    respawnInTicks = 0,
                    primaryCooldownTicks = 0,
                )
            }
            return tank.copy(respawnInTicks = remaining)
        }
        return tank
    }

    private fun resolveMove(from: Int2, delta: Int2, halfW: Int, halfH: Int): Int2 {
        if (delta.x == 0 && delta.y == 0) return from
        if (passability == null) {
            return Int2(
                x = wrapCoordinate(from.x + delta.x, state.bounds.widthPixels),
                y = wrapCoordinate(from.y + delta.y, state.bounds.heightPixels),
            )
        }
        var x = from.x
        var y = from.y

        // Try X axis.
        val targetX = (from.x + delta.x).coerceIn(halfW, passability.widthPixels - halfW - 1)
        if (!passability.isAabbBlocked(targetX, y, halfW, halfH)) {
            x = targetX
        } else {
            // Step toward the wall up to the boundary.
            x = stepAxis(from.x, targetX) { candidate ->
                !passability.isAabbBlocked(candidate, y, halfW, halfH)
            }
        }

        // Try Y axis.
        val targetY = (from.y + delta.y).coerceIn(halfH, passability.heightPixels - halfH - 1)
        if (!passability.isAabbBlocked(x, targetY, halfW, halfH)) {
            y = targetY
        } else {
            y = stepAxis(from.y, targetY) { candidate ->
                !passability.isAabbBlocked(x, candidate, halfW, halfH)
            }
        }
        return Int2(x, y)
    }

    private fun stepAxis(from: Int, target: Int, free: (Int) -> Boolean): Int {
        if (from == target) return from
        val step = if (target > from) 1 else -1
        var current = from
        var probe = from + step
        while (probe != target + step) {
            if (!free(probe)) return current
            current = probe
            probe += step
        }
        return current
    }

    private fun outOfBounds(position: Int2): Boolean {
        val pass = passability ?: return false
        return position.x < 0 || position.y < 0 ||
            position.x >= pass.widthPixels || position.y >= pass.heightPixels
    }

    private fun projectileVelocity(facing: Int2): Pair<Int, Int> {
        return facing.x.sign * PROJECTILE_SPEED to facing.y.sign * PROJECTILE_SPEED
    }

    private fun projectileVelocityFromDirection(direction: Int): Pair<Int, Int> {
        // 16-step compass: 0 = up, 4 = right, 8 = down, 12 = left.
        val (dx, dy) = DIRECTION_VECTORS[((direction % 16) + 16) % 16]
        return dx * PROJECTILE_SPEED to dy * PROJECTILE_SPEED
    }

    private fun directionToTarget(from: Int2, to: Int2): Int {
        val dx = (to.x - from.x).sign
        val dy = (to.y - from.y).sign
        return when {
            dx == 0 && dy < 0 -> 0
            dx > 0 && dy < 0 -> 2
            dx > 0 && dy == 0 -> 4
            dx > 0 && dy > 0 -> 6
            dx == 0 && dy > 0 -> 8
            dx < 0 && dy > 0 -> 10
            dx < 0 && dy == 0 -> 12
            dx < 0 && dy < 0 -> 14
            else -> 0
        }
    }

    private fun stepDirection(current: Int, desired: Int): Int {
        if (current == desired) return current
        val diff = ((desired - current + 16) % 16)
        val step = if (diff <= 8) 1 else -1
        return ((current + step + 16) % 16)
    }

    companion object {
        private val DIRECTION_VECTORS: Array<Pair<Int, Int>> = arrayOf(
            0 to -1,   // 0: up
            1 to -2,   // 1: up-right (more up)
            1 to -1,   // 2: up-right
            2 to -1,   // 3: right (more right)
            1 to 0,    // 4: right
            2 to 1,    // 5: right (more right)
            1 to 1,    // 6: down-right
            1 to 2,    // 7: down (more down)
            0 to 1,    // 8: down
            -1 to 2,   // 9
            -1 to 1,   // 10: down-left
            -2 to 1,   // 11
            -1 to 0,   // 12: left
            -2 to -1,  // 13
            -1 to -1,  // 14: up-left
            -1 to -2,  // 15
        )

        fun firstMilestonePrototype(widthPixels: Int, heightPixels: Int): TankArenaSimulation {
            val initial = WorldState(
                bounds = WorldBounds(widthPixels = widthPixels, heightPixels = heightPixels),
                tanks = listOf(
                    TankState(
                        id = 1,
                        playerIndex = 0,
                        tankType = 0,
                        position = Int2(widthPixels / 2, heightPixels / 2),
                        facing = Int2(0, -1),
                        velocity = Int2(0, 0),
                        armor = INITIAL_ARMOR,
                        fuel = INITIAL_FUEL,
                        selectedWeapon = WeaponType.MAIN_CANNON,
                    ),
                ),
                turrets = listOf(
                    TurretState(
                        id = 2,
                        turretType = 0,
                        direction = 0,
                        position = Int2(widthPixels / 2 + 128, heightPixels / 2),
                        cooldownTicks = 0,
                    ),
                ),
            )
            return TankArenaSimulation(initial)
        }
    }
}

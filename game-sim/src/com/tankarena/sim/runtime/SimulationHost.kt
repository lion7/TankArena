package com.tankarena.sim.runtime

import com.pandulapeter.kubriko.collision.Collidable
import com.pandulapeter.kubriko.collision.CollisionDetector
import com.tankarena.content.CanonicalMapDefinition
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.core.Int2
import com.tankarena.input.PlayerIntentFrame
import com.tankarena.sim.GoalState
import com.tankarena.sim.MissionProgress
import com.tankarena.sim.MissionStatus
import com.tankarena.sim.SimulationEvent
import com.tankarena.sim.SimulationResult
import com.tankarena.sim.TankState
import com.tankarena.sim.TurretState
import com.tankarena.sim.WorldBounds
import com.tankarena.sim.WorldState
import com.tankarena.sim.actor.GoalActor
import com.tankarena.sim.actor.ProjectileActor
import com.tankarena.sim.actor.TankActor
import com.tankarena.sim.actor.TurretActor
import com.tankarena.sim.actor.WallActor

private const val INITIAL_ARMOR = 100
private const val INITIAL_FUEL = 100
private const val RESPAWN_TICKS = 60

/**
 * Authoritative simulation orchestrator. Owns the runtime actor lists and
 * drives a deterministic per-tick update order that mirrors what one would
 * get from sequencing Kubriko Managers (Input ? Actor ? Collision ?
 * Mission). Mutation through the public surface is restricted to
 * [tick]; snapshots returned by [snapshot] are immutable, which is the
 * server-authoritative anti-cheat boundary.
 */
internal class SimulationHost(
    val bounds: WorldBounds,
    initialMode: com.tankarena.sim.MissionMode,
    initialTanks: List<TankState>,
    initialTurrets: List<TurretState>,
    initialGoals: List<GoalState>,
    map: CanonicalMapDefinition?,
    spawnPoints: Map<Long, Int2>,
) {

    private val tanks: MutableList<TankActor> = initialTanks.map { state ->
        TankActor.fromState(state, spawnPoint = spawnPoints[state.id] ?: state.position)
    }.toMutableList()

    private val turrets: List<TurretActor> = initialTurrets.map(TurretActor::fromState)

    private val goals: MutableList<GoalActor> = initialGoals.map(GoalActor::fromState).toMutableList()

    private val projectiles: MutableList<ProjectileActor> = mutableListOf()

    private val walls: List<WallActor> = buildWalls(bounds, map)

    private var mission: MissionProgress = MissionProgress(mode = initialMode)
    private var tick: Long = 0
    private var nextProjectileId: Long = 10_000

    private var cachedSnapshot: WorldState = buildSnapshot()

    fun snapshot(): WorldState = cachedSnapshot

    fun tick(playerInputs: Map<Int, PlayerIntentFrame>): SimulationResult {
        val previous = cachedSnapshot
        val events = mutableListOf<SimulationEvent>()

        // 1. Distribute inputs to tanks.
        for (tank in tanks) {
            val input = playerInputs[tank.playerIndex] ?: PlayerIntentFrame()
            tank.applyIntent(input)
        }

        // 2. Tank movement and primary-fire intent.
        for (tank in tanks) tank.step(events)

        // 3. Turret aim/fire.
        for (turret in turrets) turret.step(tanks, events)

        // 4. Spawn projectiles emitted this tick (matches legacy ordering: same-tick collide).
        val spawnedFireEvents = events.filterIsInstance<SimulationEvent.FireProjectile>()
        for (event in spawnedFireEvents) {
            projectiles += ProjectileActor.fromEvent(id = nextProjectileId++, event = event)
        }

        // 5. Move projectiles (may flag dead via OOB / TTL).
        for (projectile in projectiles) {
            projectile.step(bounds.widthPixels, bounds.heightPixels)
        }
        drainAndEmitDeadProjectileEvents(events)

        // 6. Collision dispatch.
        val activeTanks = tanks.filter { it.isAlive }
        val activeProjectiles = projectiles.filter { !it.isDead }
        val detectors: List<CollisionDetector> = activeTanks + activeProjectiles
        val collidables: List<Collidable> = activeTanks + activeProjectiles + turrets + goals + walls
        CollisionDispatch.dispatch(detectors = detectors, collidables = collidables)

        // 7. Apply resolution + queued damage on tanks.
        for (tank in tanks) {
            tank.applyResolutions(bounds.widthPixels, bounds.heightPixels)
        }

        // 8. Drain projectile-impact events (Explosion / TankHit) for projectiles killed during dispatch.
        drainAndEmitDeadProjectileEvents(events)

        // 9. Remove dead projectiles.
        projectiles.removeAll { it.isDead }

        // 10. Tank lifecycle (death, respawn).
        for (tank in tanks) applyLifecycle(tank, events)

        // 11. Goal collection (post-collision).
        collectGoals(events)

        // 12. Mission evaluation.
        evaluateMission(events)

        // 13. Build snapshot.
        tick += 1
        cachedSnapshot = buildSnapshot()
        return SimulationResult(previous = previous, current = cachedSnapshot, events = events)
    }

    private fun drainAndEmitDeadProjectileEvents(events: MutableList<SimulationEvent>) {
        for (projectile in projectiles) {
            if (projectile.isDead) projectile.drainEvents(events)
        }
    }

    private fun applyLifecycle(tank: TankActor, events: MutableList<SimulationEvent>) {
        if (tank.isAlive && tank.armor <= 0) {
            events += SimulationEvent.TankDestroyed(tank.id)
            val newLives = (tank.lives - 1).coerceAtLeast(0)
            tank.isAlive = false
            tank.lives = newLives
            tank.respawnInTicks = if (newLives > 0) RESPAWN_TICKS else -1
            return
        }
        if (!tank.isAlive) {
            if (tank.respawnInTicks <= 0) return
            val remaining = tank.respawnInTicks - 1
            if (remaining <= 0) {
                events += SimulationEvent.TankRespawned(tank.id)
                tank.isAlive = true
                tank.armor = INITIAL_ARMOR
                tank.fuel = INITIAL_FUEL
                tank.velocity = Int2(0, 0)
                tank.respawnInTicks = 0
                tank.primaryCooldownTicks = 0
                tank.teleportTo(tank.spawnPoint)
            } else {
                tank.respawnInTicks = remaining
            }
        }
    }

    private fun collectGoals(events: MutableList<SimulationEvent>) {
        if (goals.isEmpty()) return
        if (mission.status != MissionStatus.IN_PROGRESS) return
        var goalGood = mission.goalGood
        var goalBad = mission.goalBad
        for (goal in goals) {
            if (goal.isClaimed) continue
            val claimingTank = tanks.firstOrNull { tank ->
                tank.isAlive && tank.playerIndex >= 0 && withinRadius(
                    Int2(tank.positionX, tank.positionY),
                    goal.position,
                    goal.radius,
                )
            } ?: continue
            events += SimulationEvent.GoalReached(
                goalId = goal.id,
                tankId = claimingTank.id,
                contribution = goal.contribution,
            )
            when (goal.who) {
                0 -> goalGood = (goalGood + goal.contribution).coerceIn(0, 100)
                else -> goalBad = (goalBad + goal.contribution).coerceIn(0, 100)
            }
            goal.isClaimed = true
        }
        if (goalGood != mission.goalGood || goalBad != mission.goalBad) {
            mission = mission.copy(goalGood = goalGood, goalBad = goalBad)
        }
    }

    private fun evaluateMission(events: MutableList<SimulationEvent>) {
        if (mission.status != MissionStatus.IN_PROGRESS) return
        if (mission.goalGood >= 100) {
            events += SimulationEvent.MissionWon
            mission = mission.copy(status = MissionStatus.WON)
            return
        }
        val playerTanks = tanks.filter { it.playerIndex >= 0 }
        if (playerTanks.isNotEmpty() && playerTanks.all { !it.isAlive && it.lives <= 0 }) {
            events += SimulationEvent.MissionLost
            mission = mission.copy(status = MissionStatus.LOST)
        }
    }

    private fun withinRadius(a: Int2, b: Int2, radius: Int): Boolean {
        val dx = (a.x - b.x).toLong()
        val dy = (a.y - b.y).toLong()
        val r = radius.toLong()
        return dx * dx + dy * dy <= r * r
    }

    private fun buildSnapshot(): WorldState = WorldState(
        tick = tick,
        bounds = bounds,
        tanks = tanks.map(TankActor::toState),
        turrets = turrets.map(TurretActor::toState),
        projectiles = projectiles.filter { !it.isDead }.map(ProjectileActor::toState),
        goals = goals.map(GoalActor::toState),
        mission = mission,
    )

    private fun buildWalls(bounds: WorldBounds, map: CanonicalMapDefinition?): List<WallActor> {
        val walls = mutableListOf<WallActor>()
        if (map != null) {
            val width = map.metadata.widthTiles
            val height = map.metadata.heightTiles
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val index = x + y * width
                    val solidId = map.layers.solid.getOrElse(index) { -1 }
                    if (solidId < 0) continue
                    walls += WallActor(
                        centerX = x * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                        centerY = y * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                        width = LEGACY_TILE_SIZE,
                        height = LEGACY_TILE_SIZE,
                    )
                }
            }
        }
        // Always add four boundary walls so we don't need a separate
        // "out of bounds" branch in projectile movement.
        if (map != null) {
            val w = bounds.widthPixels
            val h = bounds.heightPixels
            val thickness = LEGACY_TILE_SIZE
            // Top
            walls += WallActor(centerX = w / 2, centerY = -thickness / 2, width = w + 2 * thickness, height = thickness)
            // Bottom
            walls += WallActor(centerX = w / 2, centerY = h + thickness / 2, width = w + 2 * thickness, height = thickness)
            // Left
            walls += WallActor(centerX = -thickness / 2, centerY = h / 2, width = thickness, height = h + 2 * thickness)
            // Right
            walls += WallActor(centerX = w + thickness / 2, centerY = h / 2, width = thickness, height = h + 2 * thickness)
        }
        return walls
    }

    /** For tests: read-only access to the wall actor list. */
    internal fun wallsForDebug(): List<WallActor> = walls
}

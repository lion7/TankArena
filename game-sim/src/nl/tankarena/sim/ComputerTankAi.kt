package nl.tankarena.sim

import nl.tankarena.content.LEGACY_TILE_SIZE
import nl.tankarena.core.LegacyDirections
import nl.tankarena.input.PlayerIntentFrame
import kotlin.math.PI
import kotlin.math.abs
import nl.tankarena.core.DetMath.atan
import nl.tankarena.core.DetMath.cos
import nl.tankarena.core.DetMath.sin
import nl.tankarena.core.DetMath.sqrt
import kotlin.random.Random

/**
 * One object as the computer tanks see it: legacy `objectstruct` position, height `h`, radius
 * `r` and goal value `gc`. [ref] identifies it from tick to tick (the target pointer).
 */
internal class AiObject(
    val ref: Any,
    val x: Int,
    val y: Int,
    val height: Int,
    val radius: Int,
    val tank: Tank? = null,
    val goal: Int = 0,
    /** A goal (`GOAL_TYPE`, who 3) meant for one particular tank only. */
    val privateGoal: Boolean = false,
)

/** What control_computer_tank() asks of the world. Positions are object centres. */
internal interface ComputerTankWorld {
    val tick: Long
    val widthTiles: Int
    val heightTiles: Int

    /** Every object, in the legacy object list order. */
    val objects: List<AiObject>

    /** Legacy `mapm.g` of a standing solid structure (`strength[0][0] != 0`), else 0. */
    fun structureGoal(tileX: Int, tileY: Int): Int

    /** Legacy in_pit() || in_lava() for a vehicle centred on ([x], [y]). */
    fun pitOrLava(x: Int, y: Int): Boolean

    /** Legacy check_terrain(): the ground speed under ([x], [y]) in percent, 100 off the map. */
    fun terrainSpeedPercent(x: Int, y: Int): Int
}

/**
 * Legacy control_computer_tank() (src/tanks/computer.c): the original's fuzzy-logic driver.
 * Every tick a computer tank looks for a new target every 1–2 s (enemy tanks, goal objects and
 * goal buildings, weighed by its preferences), aims and fires at it, and — whenever it may turn
 * — weighs dangers (objects, buildings, pits in front and to the sides) against where it wants
 * to go (the target, its waypoints, faster terrain) to decide whether to steer left or right.
 * It never brakes: it only lets go of the throttle when something is right in front of it.
 */
internal object ComputerTankAi {

    const val WAY_INACTIVE = 0
    const val WAY_ACTIVE = 1
    const val WAY_CIRCULAR = 2

    private const val F_TRUE = 255
    private const val F_HALF = 127
    private const val F_TWOTHIRD = 170
    private const val F_FALSE = 0
    private const val FRONT_CRIT = 20
    private const val SIDE_CRIT = 40

    /** The `computer` fields that change while playing; timers are absolute ticks (`tinit()`). */
    class State {
        var newTargetAt = 0L
        var target: Any? = null
        /** Legacy `target_x`/`target_y` as an object centre; [hasTarget] false for -1. */
        var targetX = 0
        var targetY = 0
        var hasTarget = false
        var lastDir = 0
        var lastDirUntil = 0L
        var crashedUntil = 0L
        var waveUntil = 0L
        var random: Random? = null
    }

    /** Legacy `fuzzy_position`. */
    private class Fuzzy {
        var close = F_FALSE
        var left = F_FALSE
        var front = F_FALSE
        var right = F_FALSE
    }

    /** The legacy file-level statics, for one tank and one tick. */
    private class Run(val tank: Tank, val world: ComputerTankWorld) {
        val state = tank.brain
        val random = state.random ?: Random(tank.tankType * 104_729 + tank.positionX * 31 + tank.positionY).also { state.random = it }
        val x = tank.positionX
        val y = tank.positionY
        val dir = LegacyDirections.normalize(tank.bodyDirection)
        val speed = tank.forwardSpeed.toInt()
        /** The distance the tank travels in 1 s, at least 50 px. */
        val range = if (speed < 50) 50 else speed
        var target: AiObject? = null
        var targetX = 0
        var targetY = 0
        var hasTarget = false
        var wayX = 0
        var wayY = 0

        val objectF = Fuzzy()
        val building = Fuzzy()
        val pit = Fuzzy()
        val danger = Fuzzy()
        val goal = Fuzzy()
        val fast = Fuzzy()
        val slow = Fuzzy()
        val wayp = Fuzzy()

        var up = false
        var left = false
        var right = false
        var aimLeft = false
        var aimRight = false
        var fire = false

        fun rnd(n: Int): Int = random.nextInt(n)
    }

    /** One tick of the computer tank: the controls it presses. */
    fun control(tank: Tank, world: ComputerTankWorld): PlayerIntentFrame {
        val run = Run(tank, world)
        val state = run.state
        val tick = world.tick

        val (wx, wy) = waypoint(tank, tank.wayCurrent)
        run.wayX = wx
        run.wayY = wy
        determineNextWaypoint(run)

        run.target = state.target?.let { ref -> world.objects.firstOrNull { it.ref === ref } }
        run.targetX = state.targetX
        run.targetY = state.targetY
        run.hasTarget = state.hasTarget
        if (tick >= state.newTargetAt) {
            determineNewTarget(run)
            state.newTargetAt = tick + 100 + run.rnd(100)
        } else {
            run.target?.let {
                run.targetX = it.x
                run.targetY = it.y
            }
        }
        state.target = run.target?.ref
        state.targetX = run.targetX
        state.targetY = run.targetY
        state.hasTarget = run.hasTarget
        tank.aiTargetX = if (run.hasTarget) run.targetX else -1
        tank.aiTargetY = if (run.hasTarget) run.targetY else -1

        if (!tank.aiActive) return PlayerIntentFrame()

        val targetDir = LegacyDirections.fromAngle(targetDx(run), targetDy(run))
        ruleAimGun(run, targetDir)
        ruleFire(run, targetDir)

        // Not allowed to turn yet (`motion.ndir`): nothing else to decide.
        if (tank.steeringReady) {
            determineObject(run)
            determineBuilding(run)
            determinePit(run)
            determineGoal(run)
            determineTerrain(run)
            determineWaypoint(run)

            run.danger.left = or(run.objectF.left, run.building.left, run.pit.left)
            run.danger.front = or(run.objectF.front, run.building.front, run.pit.front)
            run.danger.right = or(run.objectF.right, run.building.right, run.pit.right)
            // Crashed into something: fuzzy logic must have failed, panic.
            if (tick < state.crashedUntil) run.danger.front = modify(run.danger.front, F_TWOTHIRD)

            ruleSteering(run)
            if (tank.kind != VehicleKind.PLANE) {
                ruleSpeed(run)
            } else {
                run.up = true
                ruleAttackWave(run)
            }
        }
        return PlayerIntentFrame(
            forward = run.up,
            turnLeft = run.left,
            turnRight = run.right,
            aimLeft = run.aimLeft,
            aimRight = run.aimRight,
            firePrimary = run.fire,
        )
    }

    /**
     * Legacy collision handling sets `computer.crashed` (and `crashed2`): for 4 turns' time the
     * tank treats the way ahead as blocked.
     */
    fun crashed(tank: Tank, tick: Long) {
        tank.brain.crashedUntil = tick + 4L * tank.spec.motion.rotation
    }

    /** The structure tile the tank is heading for, if its target is a building; else null. */
    fun targetTile(tank: Tank): Pair<Int, Int>? {
        val state = tank.brain
        if (!state.hasTarget) return null
        return Math.floorDiv(state.targetX, LEGACY_TILE_SIZE) to Math.floorDiv(state.targetY, LEGACY_TILE_SIZE)
    }

    /** True while an aircraft flies on after a pass (`computer.wave`). */
    fun inAttackWave(tank: Tank, tick: Long): Boolean = tick < tank.brain.waveUntil

    fun targetRef(tank: Tank): Any? = tank.brain.target

    // --------------------------------------------------------------------- targets

    /**
     * Legacy determine_new_target(): the closest enemy tank, goal object or goal building, each
     * distance scaled by how much this tank cares for it (`attack_tanks`, `attack_goals`).
     * Also wakes the tank when one of its activator side comes in range.
     */
    private fun determineNewTarget(run: Run) {
        val tank = run.tank
        run.target = null
        run.hasTarget = false
        var best = Long.MAX_VALUE
        if (!tank.aiStayActive) tank.aiActive = false
        if (tank.aiActivationRadius < 0) tank.aiActive = true
        val good = tank.team == 0

        for (p in run.world.objects) {
            if (p.ref === tank) continue
            val other = p.tank
            if (other != null) {
                val otherGood = other.team == 0
                if (!tank.aiActive && other.armor > 0 &&
                    ((tank.aiActivator == 0 && otherGood) || (tank.aiActivator == 1 && !otherGood)) &&
                    inRadius(run.x - p.x, run.y - p.y, tank.aiActivationRadius)
                ) {
                    tank.aiActive = true
                }
                if (tank.aiAttackTanks > 0 && other.armor > 0 && good != otherGood) {
                    val d = (distance(run.x - p.x, run.y - p.y) * (150 - tank.aiAttackTanks) / 50).toLong()
                    if (d < best) {
                        run.target = p
                        run.targetX = p.x
                        run.targetY = p.y
                        run.hasTarget = true
                        best = d
                    }
                }
            } else {
                if (p.privateGoal) continue
                if (tank.aiAttackGoals > 0 && ((good && p.goal > 0) || (!good && p.goal < 0))) {
                    val d = (distance(run.x - p.x, run.y - p.y) * (150 - tank.aiAttackGoals) / 50).toLong()
                    if (d < best) {
                        run.target = p
                        run.targetX = p.x
                        run.targetY = p.y
                        run.hasTarget = true
                        best = d
                    }
                }
            }
        }

        // Goal buildings within 15 tiles.
        if (tank.aiAttackGoals > 0) {
            val mx = Math.floorDiv(run.x, LEGACY_TILE_SIZE)
            val my = Math.floorDiv(run.y, LEGACY_TILE_SIZE)
            var ty = my - 15
            var cy = 0
            while (cy < 30 && ty < run.world.heightTiles) {
                var tx = mx - 15
                var cx = 0
                while (cx < 30 && tx < run.world.widthTiles) {
                    if (tx >= 0 && ty >= 0) {
                        val g = run.world.structureGoal(tx, ty)
                        if ((good && g > 0) || (!good && g < 0)) {
                            val d = (distance(tx - mx, ty - my) * LEGACY_TILE_SIZE * (150 - tank.aiAttackGoals) / 50).toLong()
                            if (d < best) {
                                run.target = null
                                run.targetX = tx * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2
                                run.targetY = ty * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2
                                run.hasTarget = true
                                best = d
                            }
                        }
                    }
                    tx++
                    cx++
                }
                ty++
                cy++
            }
        }
    }

    /** Legacy waypoints are top-left corners; -1 marks an empty slot. Returns the centre. */
    private fun waypoint(tank: Tank, index: Int): Pair<Int, Int> {
        val i = index.coerceIn(0, Tank.WAYPOINTS - 1)
        return tank.wayPoints[2 * i] + LEGACY_TILE_SIZE / 2 to tank.wayPoints[2 * i + 1] + LEGACY_TILE_SIZE / 2
    }

    /** Legacy determine_next_waypoint(): within 30 px of the waypoint, head for the next one. */
    private fun determineNextWaypoint(run: Run) {
        val tank = run.tank
        if (tank.wayType == WAY_INACTIVE || !inRadius(run.wayX - run.x, run.wayY - run.y, 30)) return
        var c = tank.wayCurrent
        val circular = tank.wayType == WAY_CIRCULAR
        do {
            c = if (circular) (c + 1) % Tank.WAYPOINTS else c + 1
        } while (c < Tank.WAYPOINTS && tank.wayPoints[2 * c] == -1)
        if (c == Tank.WAYPOINTS) tank.wayType = WAY_INACTIVE else tank.wayCurrent = c
    }

    // --------------------------------------------------------------------- non-fuzzy rules

    /** Legacy: without a target `trg_x`, `trg_y` are -1, a top-left corner. */
    private fun targetDx(run: Run) = (if (run.hasTarget) run.targetX else -1 + LEGACY_TILE_SIZE / 2) - run.x
    private fun targetDy(run: Run) = (if (run.hasTarget) run.targetY else -1 + LEGACY_TILE_SIZE / 2) - run.y

    /** Legacy rule_aim_gun(): turn the turret towards the target. */
    private fun ruleAimGun(run: Run, targetDir: Int) {
        when (goLeftOrRight(targetDir, LegacyDirections.normalize(run.tank.turretDirection))) {
            -1 -> run.aimLeft = true
            1 -> run.aimRight = true
        }
    }

    /**
     * Legacy rule_fire(): fire when the gun (the turret, or the hull for hull guns, chain guns
     * and flamers) points at a target within `attack_at`, or at anything within 30 px.
     */
    private fun ruleFire(run: Run, targetDir: Int) {
        if (!run.hasTarget) return
        val tank = run.tank
        val weapon = tank.currentWeapon
        val gunDir = if (tank.turretMode == TurretMode.TURRET && weapon != WEAPON_CHAIN && weapon != WEAPON_FLAME) {
            LegacyDirections.normalize(tank.turretDirection)
        } else {
            run.dir
        }
        val dx = run.x - run.targetX
        val dy = run.y - run.targetY
        if ((targetDir == gunDir && inRadius(dx, dy, tank.aiAttackAt)) || (tank.aiAttackAt > 30 && inRadius(dx, dy, 30))) {
            run.fire = true
        }
    }

    /** Legacy rule_attack_wave(): a plane over its target flies on for `wave_delay` without steering. */
    private fun ruleAttackWave(run: Run) {
        if (inRadius(run.x - (run.x + targetDx(run)), run.y - (run.y + targetDy(run)), 80)) {
            run.state.waveUntil = run.world.tick + run.tank.aiWaveDelay
        }
    }

    /** Legacy rule_control_speed(): let go of the throttle when a collision or a pit is near. */
    private fun ruleSpeed(run: Run) {
        run.up = !((run.speed > 20 && run.danger.front > F_HALF) || (run.speed >= 0 && run.pit.front > F_HALF))
    }

    // --------------------------------------------------------------------- fuzzy inputs

    /** Legacy object_close(): true below [min], false above [max], linear in between. */
    private fun objectClose(d: Long, min: Int, max: Int): Int = when {
        d < min -> F_TRUE
        d > max -> F_FALSE
        else -> (F_TRUE - ((d - min) * F_TRUE) / (max - min)).toInt()
    }

    /** Legacy angle_to_fuzzy_direction(): how far in front, left and right ([dx], [dy]) lies. */
    private fun angleToFuzzy(run: Run, dx: Int, dy: Int, critAngle: Int, f: Fuzzy) {
        var phi = ((a16ToRad(run.dir) - angle(dx, dy)) / PI * 180).toLong()
        if (phi < -180) phi += 360
        if (phi > 180) phi -= 360

        f.front = if (phi >= -FRONT_CRIT && phi <= FRONT_CRIT) ((FRONT_CRIT - abs(phi)) * F_TRUE / FRONT_CRIT).toInt() else F_FALSE

        var t = if (phi < 0) phi + 360 else phi
        f.right = when {
            t >= critAngle -> F_FALSE
            t < SIDE_CRIT -> (t * F_TRUE / SIDE_CRIT).toInt()
            t > critAngle - SIDE_CRIT -> ((critAngle - t) * F_TRUE / SIDE_CRIT).toInt()
            else -> F_TRUE
        }

        t = abs(if (phi > 0) phi - 360 else phi)
        f.left = when {
            t >= critAngle -> F_FALSE
            t < SIDE_CRIT -> (t * F_TRUE / SIDE_CRIT).toInt()
            t > critAngle - SIDE_CRIT -> ((critAngle - t) * F_TRUE / SIDE_CRIT).toInt()
            else -> F_TRUE
        }
    }

    /** Legacy fdetermine_object(): other objects at about the same height that are getting close. */
    private fun determineObject(run: Run) {
        val f = Fuzzy()
        for (q in run.world.objects) {
            if (q.ref === run.tank || q.radius <= 0 || abs(run.tank.height - q.height) >= 2) continue
            if (run.target != null && q.ref === run.target!!.ref) continue
            angleToFuzzy(run, q.x - run.x, q.y - run.y, 90, f)
            f.close = objectClose(distance(q.x - run.x, q.y - run.y).toLong(), 20, run.range)
            run.objectF.left = or(run.objectF.left, and(f.close, f.left))
            run.objectF.front = or(run.objectF.front, and(f.close, f.front))
            run.objectF.right = or(run.objectF.right, and(f.close, f.right))
            run.objectF.close = run.objectF.front
        }
    }

    /**
     * Legacy fdetermine_close(): how close (fuzzy) the first point in direction [dir] within
     * [range] is for which [check] holds, sampled in [steps] steps.
     */
    private inline fun determineClose(run: Run, dir: Int, range: Int, steps: Int, check: (Int, Int, Int) -> Boolean): Int {
        val a = a16ToRad(dir)
        val xm = cos(a).toFloat() * range / F_TRUE
        val ym = sin(a).toFloat() * range / F_TRUE
        val step = F_TRUE / steps
        var i = step
        while (i <= F_TRUE) {
            if (check(dir, (run.x + xm * i).toInt(), (run.y - ym * i).toInt())) return F_TRUE + step - i
            i += step
        }
        return 0
    }

    /** Legacy determine_average(): the average of [value] along direction [dir] within [range]. */
    private inline fun determineAverage(run: Run, dir: Int, range: Int, steps: Int, value: (Int, Int) -> Int): Int {
        val a = a16ToRad(dir)
        val xm = cos(a).toFloat()
        val ym = sin(a).toFloat()
        val step = range / steps
        var v = 0
        var i = step
        while (i <= range) {
            v += value((run.x + xm * i).toInt(), (run.y - ym * i).toInt())
            i += step
        }
        return v / steps
    }

    /** Legacy check_building(): this tank's picture there would overlap a structure (not its target). */
    private fun checkBuilding(run: Run, dir: Int, x: Int, y: Int): Boolean {
        val hit = IntArray(2)
        if (run.tank.overlapAt(dir, x, y, hit) <= 0) return false
        if (!run.hasTarget) return true
        return hit[0] != Math.floorDiv(run.targetX, LEGACY_TILE_SIZE) || hit[1] != Math.floorDiv(run.targetY, LEGACY_TILE_SIZE)
    }

    private fun determineBuilding(run: Run) {
        val d = run.dir
        val check = { dir: Int, x: Int, y: Int -> checkBuilding(run, dir, x, y) }
        run.building.left = or(determineClose(run, (d - 4) and 15, 50, 4, check), determineClose(run, (d - 1) and 15, run.range, 4, check))
        run.building.front = determineClose(run, d, run.range, 8, check)
        run.building.right = or(determineClose(run, (d + 1) and 15, run.range, 4, check), determineClose(run, (d + 4) and 15, 50, 4, check))
        run.building.close = run.building.front
    }

    /** Legacy check_pit(): a pit or lava there, for a vehicle on the ground. */
    private fun determinePit(run: Run) {
        val d = run.dir
        val check = { _: Int, x: Int, y: Int -> run.tank.height == 1 && run.world.pitOrLava(x, y) }
        run.pit.left = or(determineClose(run, (d - 4) and 15, 50, 5, check), determineClose(run, (d - 1) and 15, run.range, 5, check))
        run.pit.front = determineClose(run, d, run.range, 10, check)
        run.pit.right = or(determineClose(run, (d + 1) and 15, run.range, 5, check), determineClose(run, (d + 4) and 15, 50, 5, check))
        run.pit.close = run.pit.front
    }

    /** Legacy fdetermine_goal(): where the target lies. */
    private fun determineGoal(run: Run) {
        if (run.hasTarget) {
            angleToFuzzy(run, run.targetX - run.x, run.targetY - run.y, 220, run.goal)
            run.goal.close = objectClose(distance(run.targetX - run.x, run.targetY - run.y).toLong(), 100, 1000)
        }
    }

    /** Legacy check_terrain() through the world: 100 off the ground. */
    private fun terrain(run: Run, x: Int, y: Int): Int =
        if (run.tank.height != 1) 100 else run.world.terrainSpeedPercent(x, y)

    /** Legacy fdetermine_fast_or_slow(). */
    private fun fastOrSlow(here: Int, value: Int, fast: (Int) -> Unit, slow: (Int) -> Unit) {
        var t = if (here < 100) 100 else here
        slow(
            when {
                value >= t -> F_FALSE
                value > 50 -> (t - value) * F_TRUE / (t - 50)
                else -> F_TRUE
            },
        )
        t = if (here > 100) 100 else here
        fast(
            when {
                value <= t -> F_FALSE
                value < 130 -> (value - t) * F_TRUE / (130 - t)
                else -> F_TRUE
            },
        )
    }

    /** Legacy fdetermine_terrain(): is the ground ahead and to the sides faster or slower than here? */
    private fun determineTerrain(run: Run) {
        val d = run.dir
        val value = { x: Int, y: Int -> terrain(run, x, y) }
        val left = determineAverage(run, (d - 2) and 15, run.range, 5, value)
        val front = determineAverage(run, d, run.range, 5, value)
        val right = determineAverage(run, (d + 2) and 15, run.range, 5, value)
        val here = terrain(run, run.x, run.y)
        fastOrSlow(here, left, { run.fast.left = it }, { run.slow.left = it })
        fastOrSlow(here, front, { run.fast.front = it }, { run.slow.front = it })
        fastOrSlow(here, right, { run.fast.right = it }, { run.slow.right = it })
        run.fast.close = or(run.fast.left, run.fast.front, run.fast.right)
        run.slow.close = run.slow.front
    }

    /** Legacy fdetermine_wayp(): where the current waypoint lies. */
    private fun determineWaypoint(run: Run) {
        if (run.tank.wayType != WAY_INACTIVE) {
            angleToFuzzy(run, run.wayX - run.x, run.wayY - run.y, 220, run.wayp)
            run.wayp.close = objectClose(distance(run.wayX - run.x, run.wayY - run.y).toLong(), 100, 1500)
        }
    }

    // --------------------------------------------------------------------- steering

    /** Legacy rule_control_steering(): the fuzzy rules, a bonus for the last turn and some noise. */
    private fun ruleSteering(run: Run) {
        val followWaypoint: Int
        val followGoal: Int
        if (run.wayp.close >= run.goal.close) {
            followWaypoint = run.wayp.close
            followGoal = degreeOfSupport(40, run.goal.close)
        } else {
            followGoal = run.goal.close
            followWaypoint = degreeOfSupport(40, run.wayp.close)
        }
        val followFast = and(run.fast.close, not(followWaypoint), not(followGoal))

        var wantLeft = and(
            not(run.danger.left),
            or(
                run.danger.front,
                and(run.goal.left, followGoal),
                and(run.wayp.left, followWaypoint),
                and(not(run.fast.front), run.fast.left, followFast),
                and(run.slow.front, not(run.slow.left)),
            ),
        )
        var wantRight = and(
            not(run.danger.right),
            or(
                run.danger.front,
                and(run.goal.right, followGoal),
                and(run.wayp.right, followWaypoint),
                and(not(run.fast.front), run.fast.right, followFast),
                and(run.slow.front, not(run.slow.right)),
            ),
        )
        var steering = or(run.danger.front, wantLeft, wantRight)

        val state = run.state
        val tick = run.world.tick
        if (tick < state.lastDirUntil) {
            when (state.lastDir) {
                -1 -> wantLeft = modify(wantLeft, F_TWOTHIRD)
                1 -> wantRight = modify(wantRight, F_TWOTHIRD)
            }
        }

        steering = modify(steering, run.rnd(64) - 32)
        wantLeft = modify(wantLeft, run.rnd(64) - 32)
        wantRight = modify(wantRight, run.rnd(64) - 32)

        if (tick >= state.waveUntil && (run.tank.kind != VehicleKind.PLANE || run.tank.height > 10) && steering > F_TWOTHIRD) {
            if (wantLeft > wantRight) {
                run.left = true
                state.lastDir = -1
            } else {
                run.right = true
                state.lastDir = 1
            }
            state.lastDirUntil = tick + 50
        }
    }

    // --------------------------------------------------------------------- helpers

    private fun not(x: Int) = F_TRUE - x
    private fun and(vararg x: Int) = x.min()
    private fun or(vararg x: Int) = x.max()
    private fun modify(x: Int, y: Int) = (x + y).coerceIn(F_FALSE, F_TRUE)
    private fun degreeOfSupport(p: Int, v: Int) = p * v / 100

    private fun inRadius(dx: Int, dy: Int, r: Int): Boolean = dx.toLong() * dx + dy.toLong() * dy < r.toLong() * r

    private fun distance(dx: Int, dy: Int): Double = sqrt(dx.toDouble() * dx + dy.toDouble() * dy)

    /** Legacy go_left_or_right(). */
    private fun goLeftOrRight(wanted: Int, actual: Int): Int =
        if (wanted == actual) 0 else if (((actual - wanted) and 15) < 8) -1 else 1

    /** Legacy a16torad(): direction 0 is north, angles count anticlockwise from east. */
    private fun a16ToRad(direction: Int): Double {
        val a = PI / 2 - LegacyDirections.normalize(direction) * PI / 8
        return if (a < 0) a + 2 * PI else a
    }

    /** Legacy angle() (src/check/math.c), with y growing downwards. */
    private fun angle(dx: Int, dy: Int): Double {
        if (dx == 0) return if (dy <= 0) 0.5 * PI else 1.5 * PI
        val a = if (dx > 0) -atan(dy.toDouble() / dx) else PI - atan(dy.toDouble() / dx)
        return if (a < 0) 2 * PI + a else a
    }
}

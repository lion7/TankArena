package com.tankarena.sim

import com.tankarena.core.LegacyDirections
import com.tankarena.core.DetMath.hypot
import kotlin.random.Random

/*
 * Objects that only ever exist while a match runs (the original never stores them in a map):
 * bonuses, soldiers and A-bombs. The match owns them directly; they are not stored in scene files.
 */

/** Legacy create_object(BONUS_TYPE, ..., r = 2): a bullet or blast this close destroys a bonus. */
internal const val BONUS_RADIUS_PX: Int = 2
/** Legacy create_object(MAN_TYPE, ..., r = 5). */
internal const val SOLDIER_RADIUS_PX: Int = 5
/** Legacy create_object(ABOMB_TYPE, ..., r = 3). */
internal const val ABOMB_RADIUS_PX: Int = 3

/** A bonus lying on the map (legacy BONUS_TYPE, src/objects/bonus.c). */
internal class Bonus(val id: Long, val x: Int, val y: Int, val type: Int) {
    var removed: Boolean = false
}

/**
 * A soldier (legacy MAN_TYPE, src/objects/man.c): walks at 5 px/s to within 20 px of his target
 * (a vehicle he follows, or a fixed point) and fires one of his 75 rounds every 40..80 ticks
 * towards it.
 */
internal class Soldier(
    val id: Long,
    x: Int,
    y: Int,
    private var targetX: Int,
    private var targetY: Int,
    /** Legacy `o->target`: the object near the marker, whose position the soldier keeps following. */
    private val target: Tank?,
    /** Legacy `m.type`: the projectile he fires (chain-gun round, flame or small rocket). */
    val shotKind: Int,
    val power: Int,
    /** The vehicle that dropped him; soldiers of the same vehicle do not shoot each other. */
    val owner: Tank?,
    private val random: Random,
) {
    private var exactX: Float = x.toFloat()
    private var exactY: Float = y.toFloat()
    val x: Int get() = Math.round(exactX)
    val y: Int get() = Math.round(exactY)
    var direction: Int = 0
        private set
    /** Legacy `m.select`: 0 or 5, toggled while walking. */
    var frame: Int = 0
        private set
    private var frameCooldownTicks: Int = 0
    private var bullets: Int = SOLDIER_ROUNDS
    private var fireCooldownTicks: Int = 0
    var removed: Boolean = false
    val outOfAmmo: Boolean get() = bullets == 0

    /** One shot: where it leaves, in which direction. */
    data class Shot(val x: Int, val y: Int, val direction: Int)

    /** Legacy control_man(), first half: walk towards the target and face it. */
    fun move() {
        target?.let {
            targetX = it.positionX
            targetY = it.positionY
        }
        val dx = targetX - exactX
        val dy = targetY - exactY
        if (hypot(dx, dy) > SOLDIER_STOP_DISTANCE_PX) {
            val distance = hypot(dx, dy)
            exactX += dx / distance * SOLDIER_SPEED * TICK
            exactY += dy / distance * SOLDIER_SPEED * TICK
            if (frameCooldownTicks > 0) frameCooldownTicks -= 1
            if (frameCooldownTicks == 0) {
                frame = frame xor 5
                frameCooldownTicks = 10 + random.nextInt(10)
            }
        }
        direction = LegacyDirections.fromAngle(targetX - x, targetY - y)
    }

    /** Legacy control_man(), second half: fire a round every 40..80 ticks. */
    fun fire(): Shot? {
        if (fireCooldownTicks > 0) fireCooldownTicks -= 1
        if (fireCooldownTicks > 0 || bullets == 0) return null
        fireCooldownTicks = 40 + random.nextInt(40)
        bullets -= 1
        val (gx, gy) = rotate(direction, SOLDIER_GUN)
        return Shot(x + gx, y + gy, direction)
    }

    private companion object {
        const val SOLDIER_ROUNDS = 75
        const val SOLDIER_SPEED = 5f
        const val SOLDIER_STOP_DISTANCE_PX = 20f
        const val TICK = 0.01f
        val SOLDIER_GUN = 1 to -3
    }
}

/**
 * An A-bomb (legacy ABOMB_TYPE, src/objects/abomb.c): counts down [countdown] × 60 ticks, then
 * goes off in 30 steps of 6 ticks, each wearing 4 off everything within a radius that grows by
 * 7 px per step (up to 210 px).
 */
internal class Abomb(val id: Long, val x: Int, val y: Int, countdown: Int, val owner: Tank?) {
    var countdown: Int = countdown
        private set
    /** Legacy `nuke_count`: 30 until the bomb goes off, then down to 0. */
    var nukeCount: Int = NUKE_STEPS
        private set
    private var nextTicks: Int = if (countdown == 0) 0 else COUNTDOWN_STEP_TICKS
    var removed: Boolean = false
    val exploding: Boolean get() = nukeCount < NUKE_STEPS

    /** One step of the blast. [flash] is the legacy `nuke_total` change. */
    data class Blast(val radius: Int, val flash: Int, val first: Boolean)

    /** Legacy control_abomb(). */
    fun step(): Blast? {
        if (removed) return null
        if (nextTicks > 0) nextTicks -= 1
        if (nextTicks > 0) return null
        if (countdown > 0) {
            countdown -= 1
            nextTicks = COUNTDOWN_STEP_TICKS
            return null
        }
        val first = nukeCount == NUKE_STEPS
        val flash = if (nukeCount > 20) 6 else -3
        nukeCount -= 1
        nextTicks = NUKE_STEP_TICKS
        if (nukeCount == 0) removed = true
        return Blast(radius = (NUKE_STEPS - nukeCount) * NUKE_RADIUS_STEP_PX, flash = flash, first = first)
    }

    companion object {
        const val NUKE_STEPS = 30
        const val COUNTDOWN_STEP_TICKS = 60
        const val NUKE_STEP_TICKS = 6
        const val NUKE_RADIUS_STEP_PX = 7
        /** Legacy `abomb_power`. */
        const val POWER = 4
    }
}

package com.tankarena.core

import kotlin.math.PI
import kotlin.math.roundToInt

object LegacyDirections {
    const val NORTH: Int = 0
    const val EAST: Int = 4
    const val SOUTH: Int = 8
    const val WEST: Int = 12
    const val STEPS: Int = 16

    fun normalize(direction: Int): Int = ((direction % STEPS) + STEPS) % STEPS

    fun stepLeft(direction: Int): Int = normalize(direction - 1)

    fun stepRight(direction: Int): Int = normalize(direction + 1)

    fun stepToward(current: Int, desired: Int): Int {
        val normalizedCurrent = normalize(current)
        val normalizedDesired = normalize(desired)
        if (normalizedCurrent == normalizedDesired) return normalizedCurrent
        val diff = (normalizedDesired - normalizedCurrent + STEPS) % STEPS
        return if (diff <= STEPS / 2) stepRight(normalizedCurrent) else stepLeft(normalizedCurrent)
    }

    fun toFacing(direction: Int): Int2 {
        val (x, y) = unitVector(direction)
        return Int2(
            x = x.roundToInt(),
            y = y.roundToInt(),
        )
    }

    fun unitVector(direction: Int): Pair<Float, Float> {
        val angle = radians(direction)
        return DetMath.cos(angle).toFloat() to -DetMath.sin(angle).toFloat()
    }

    fun toVelocityStep(direction: Int, magnitude: Float): Pair<Float, Float> {
        val (x, y) = unitVector(direction)
        return x * magnitude to y * magnitude
    }

    fun fromFacing(facingX: Int, facingY: Int): Int {
        val sx = facingX.coerceIn(-1, 1)
        val sy = facingY.coerceIn(-1, 1)
        return when {
            sx == 0 && sy < 0 -> 0
            sx > 0 && sy < 0 -> 2
            sx > 0 && sy == 0 -> 4
            sx > 0 && sy > 0 -> 6
            sx == 0 && sy > 0 -> 8
            sx < 0 && sy > 0 -> 10
            sx < 0 && sy == 0 -> 12
            sx < 0 && sy < 0 -> 14
            else -> 0
        }
    }

    /**
     * Legacy radtoa16(angle(dx, dy)) (src/check/math.c): the nearest of the 16 directions from a
     * point towards another one at offset ([dx], [dy]) in screen pixels (y grows downwards).
     */
    fun fromAngle(dx: Int, dy: Int): Int {
        if (dx == 0 && dy == 0) return NORTH
        val angle = DetMath.atan2(-dy.toDouble(), dx.toDouble())
        return normalize(((PI / 2.0 - angle) / (2.0 * PI / STEPS)).roundToInt())
    }

    private fun radians(direction: Int): Double {
        val normalized = normalize(direction)
        // Legacy angles rotate clockwise with north at 0, east at 4.
        return PI / 2.0 - (normalized * 2.0 * PI / STEPS)
    }
}

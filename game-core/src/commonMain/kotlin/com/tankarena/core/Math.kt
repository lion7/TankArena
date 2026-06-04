package com.tankarena.core

import kotlin.math.sqrt

data class Vec2(val x: Float, val y: Float) {
    operator fun plus(other: Vec2): Vec2 = Vec2(x + other.x, y + other.y)
    operator fun times(scalar: Float): Vec2 = Vec2(x * scalar, y * scalar)
    fun length(): Float = sqrt((x * x) + (y * y))
    fun normalized(): Vec2 {
        val len = length()
        return if (len == 0f) ZERO else Vec2(x / len, y / len)
    }

    companion object {
        val ZERO: Vec2 = Vec2(0f, 0f)
    }
}

package com.tankarena.core

import kotlin.math.abs

data class Int2(
    val x: Int,
    val y: Int,
)

fun wrapCoordinate(value: Int, size: Int): Int {
    if (size <= 0) return value
    var wrapped = value % size
    if (wrapped < 0) wrapped += size
    return wrapped
}

fun toroidalDelta(from: Int, to: Int, size: Int): Int {
    if (size <= 0) return to - from
    val direct = to - from
    val wrappedPositive = direct + size
    val wrappedNegative = direct - size
    return listOf(direct, wrappedPositive, wrappedNegative).minBy { abs(it) }
}


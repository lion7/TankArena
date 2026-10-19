package com.tankarena.core

/**
 * The simulation's maths functions. `Math.sin` and friends may use processor-specific
 * implementations whose last bit differs between machines; [StrictMath] gives the same result on
 * every JVM, so the same inputs play out the same everywhere (recordings, network play, other
 * platforms). Every trigonometric or root function in the simulation goes through here.
 */
object DetMath {
    fun sin(x: Double): Double = StrictMath.sin(x)
    fun cos(x: Double): Double = StrictMath.cos(x)
    fun atan(x: Double): Double = StrictMath.atan(x)
    fun atan2(y: Double, x: Double): Double = StrictMath.atan2(y, x)
    fun sqrt(x: Double): Double = StrictMath.sqrt(x)
    fun hypot(x: Double, y: Double): Double = StrictMath.hypot(x, y)

    fun sin(x: Float): Float = StrictMath.sin(x.toDouble()).toFloat()
    fun cos(x: Float): Float = StrictMath.cos(x.toDouble()).toFloat()
    fun atan(x: Float): Float = StrictMath.atan(x.toDouble()).toFloat()
    fun atan2(y: Float, x: Float): Float = StrictMath.atan2(y.toDouble(), x.toDouble()).toFloat()
    fun sqrt(x: Float): Float = StrictMath.sqrt(x.toDouble()).toFloat()
    fun hypot(x: Float, y: Float): Float = StrictMath.hypot(x.toDouble(), y.toDouble()).toFloat()
}

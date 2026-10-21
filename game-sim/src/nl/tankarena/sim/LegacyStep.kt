package nl.tankarena.sim

/**
 * The original runs one game step every third tick of its 100 Hz timer: game_control() waits until
 * the timer passes `np = tinit(2)` (original/src/game/mainloop.c). Every rule runs once per step, movement
 * covers the time of a step, and timers (`tdone(t)`, `timer >= t`) are only looked at on a step.
 */
const val STEP_TICKS: Int = 3

/** The time one step covers. */
internal const val STEP_SECONDS: Float = STEP_TICKS / 100f

/**
 * A count-down timer in ticks after one more step. It stops at 0, so a timer of `n` ticks set on a
 * step runs out on the first step at or after `n` ticks, like a `tinit(n)` checked with `tdone()`:
 * 7 ticks last 9, 10 last 12, 0 runs out on the next step.
 */
internal fun Int.afterStep(): Int = if (this > STEP_TICKS) this - STEP_TICKS else 0

internal fun Long.afterStep(): Long = if (this > STEP_TICKS) this - STEP_TICKS else 0L

/** How long a `tinit(ticks)` set on a step really lasts: up to the next whole step. */
internal fun stepRounded(ticks: Int): Int = (ticks + STEP_TICKS - 1) / STEP_TICKS * STEP_TICKS

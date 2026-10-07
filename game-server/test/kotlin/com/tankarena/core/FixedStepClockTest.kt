package com.tankarena.core

import kotlin.test.Test
import kotlin.test.assertEquals

class FixedStepClockTest {

    @Test
    fun `tick rate is 100 Hz`() {
        assertEquals(100, FixedStepClock.TICKS_PER_SECOND)
    }

    @Test
    fun `tick is 10 ms`() {
        assertEquals(10L, FixedStepClock.MILLIS_PER_TICK)
    }
}

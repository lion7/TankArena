package com.tankarena.sim.kubriko

import com.pandulapeter.kubriko.Kubriko
import com.pandulapeter.kubriko.helpers.TickSource
import com.pandulapeter.kubriko.manager.Manager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HeadlessKubrikoSmokeTest {

    @Test
    fun `manual tick source drives a custom Manager's onUpdate`() {
        val tracker = TickCountingManager()
        val tickSource = TickSource.manual()
        val kubriko = Kubriko.newInstance(
            tracker,
            tickSource = tickSource,
        )
        tickSource.start()
        assertTrue(tracker.wasInitialized, "custom Manager should have received onInitialize")

        repeat(5) { tickSource.tick(33) }

        assertEquals(5, tracker.updateCount, "ManualTickSource.tick should drive Manager.onUpdate")
        assertEquals(listOf(33, 33, 33, 33, 33), tracker.deltas)

        kubriko.dispose()
        assertTrue(tracker.wasDisposed, "custom Manager should have received onDispose")
    }

    @Test
    fun `freshly constructed Kubriko does not tick until initialize is called`() {
        val tracker = TickCountingManager()
        val tickSource = TickSource.manual()
        val kubriko = Kubriko.newInstance(
            tracker,
            tickSource = tickSource,
        )
        assertFalse(tracker.wasInitialized)
        tickSource.start()
        assertTrue(tracker.wasInitialized)
        kubriko.dispose()
    }
}

private class TickCountingManager : Manager() {
    var wasInitialized: Boolean = false
        private set
    var wasDisposed: Boolean = false
        private set
    var updateCount: Int = 0
        private set
    val deltas: MutableList<Int> = mutableListOf()

    override fun onInitialize(kubriko: Kubriko) {
        wasInitialized = true
    }

    override fun onUpdate(deltaTimeInMilliseconds: Int) {
        updateCount += 1
        deltas += deltaTimeInMilliseconds
    }

    override fun onDispose() {
        wasDisposed = true
    }
}

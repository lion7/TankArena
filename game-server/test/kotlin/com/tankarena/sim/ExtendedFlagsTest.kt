package com.tankarena.sim

import com.tankarena.sim.map.ShippedMaps
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Extended's rule flags against Classic's original rules (ADR 0023). */
class ExtendedFlagsTest {
    private fun start(map: String, mode: MatchMode, ruleset: Ruleset): Match {
        val match = Match.fromMap(assertNotNull(ShippedMaps.load(map)).document, mode, ruleset = ruleset)
        match.initialize()
        return match
    }

    @Test
    fun `stacked computer tanks blow up in Classic and survive in Extended`() {
        // SUBURBS starts with two computer tanks on top of each other.
        fun stackedSurvivors(ruleset: Ruleset): Int {
            val match = start("suburbs", MatchMode.DUAL_VS_COMPUTER, ruleset)
            val computers = match.tanksForTest().filter { it.computer }
            val stacked = computers.filter { a ->
                computers.any { b ->
                    a !== b && (a.positionX - b.positionX) * (a.positionX - b.positionX) + (a.positionY - b.positionY) * (a.positionY - b.positionY) <
                        (a.spec.radius + b.spec.radius) * (a.spec.radius + b.spec.radius)
                }
            }
            assertEquals(2, stacked.size)
            match.stepForTest()
            return stacked.count { it.armor > 0 }
        }
        assertEquals(0, stackedSurvivors(Ruleset.CLASSIC))
        assertEquals(2, stackedSurvivors(Ruleset.EXTENDED))
    }
}

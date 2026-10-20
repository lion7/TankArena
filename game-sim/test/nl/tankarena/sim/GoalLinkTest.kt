package nl.tankarena.sim

import nl.tankarena.sim.map.ShippedMaps
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Goals with `who` 3 wait for one linked vehicle (src/objects/goal.c:46-51); WEAPON has five. */
class GoalLinkTest {
    @Test
    fun `a linked goal is reached by its own vehicle only`() {
        val match = Match.fromMap(assertNotNull(ShippedMaps.load("weapon")).document)
        match.initialize()
        val goals = match.objects.all.filterIsInstance<Goal>().filter { it.who == 3 }
        assertEquals(5, goals.size)
        goals.forEach { assertNotNull(it.target, "every WEAPON goal finds its tank") }
        val goal = goals.first()
        val (cx, cy) = goal.body.position.x.toInt() + 8 to goal.body.position.y.toInt() + 8

        val stranger = match.tanksForTest().first { tank -> goals.none { it.target === tank } && tank.playerIndex < 0 }
        stranger.teleportTo(cx, cy)
        match.stepForTest()
        assertEquals(0, match.goalBad, "another vehicle does not count")

        assertNotNull(goal.target).teleportTo(cx, cy)
        match.stepForTest()
        assertEquals(-goal.contribution, match.goalBad)
    }
}

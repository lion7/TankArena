package nl.tankarena.sim

import nl.tankarena.sim.map.ShippedMaps
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Supermarket mayhem on SUPER (src/objects/product.c, src/world/status.c). */
class SupermarketMayhemTest {
    private fun start(mode: MatchMode = MatchMode.SINGLE): Match {
        val match = Match.fromMap(assertNotNull(ShippedMaps.load("super")).document, mode)
        match.initialize()
        return match
    }

    private fun Match.products() = objects.all.filterIsInstance<Product>()

    private fun Product.centre() =
        (body.position.x + body.size.width / 2).toInt() to (body.position.y + body.size.height / 2).toInt()

    @Test
    fun `a map with products is a shopping trip without a start shield or radar`() {
        val match = start()
        assertTrue(match.mayhem)
        val tank = assertNotNull(match.playerTankForTest(0))
        assertEquals(0, tank.invulnerableTicks, "prepare_tank(): shield.icount = 0")
        val hud = match.buildPlayerViews().single().hud
        assertEquals(MayhemExpectation(MAYHEM_PRODUCTS, MAYHEM_CASH), MayhemExpectation(hud.mayhem!!.productsNeeded, hud.mayhem!!.cash))
        assertTrue(hud.radarHidden)
    }

    @Test
    fun `driving over a product buys it`() {
        val match = start()
        val tank = assertNotNull(match.playerTankForTest(0))
        val product = match.products().first()
        val (x, y) = product.centre()
        tank.teleportTo(x, y)
        match.stepForTest()
        assertTrue(product.isCollected)
        assertEquals(MAYHEM_CASH - product.price, match.mayhemCash)
        assertEquals(MAYHEM_PRODUCTS - 1, match.mayhemProductsNeeded)
        assertEquals(1, match.productCounts[product.productType])
        assertEquals("PICKED UP" to PRODUCT_NAMES[product.productType], tank.messageLine1 to tank.messageLine2)
    }

    @Test
    fun `spending more than the cash fails the mission`() {
        val match = start()
        val tank = assertNotNull(match.playerTankForTest(0))
        val product = match.products().first { it.price > 1 }
        match.mayhemCash = 1
        val (x, y) = product.centre()
        tank.teleportTo(x, y)
        match.stepForTest()
        assertEquals(0, match.mayhemCash)
        assertEquals(100, match.goalBad)
        match.stepForTest()
        assertEquals(Match.MissionStatus.LOST, match.missionStatus)
    }

    @Test
    fun `the mission fails when the shopping time runs out`() {
        val match = start()
        match.currentTick = MAYHEM_TICKS - STEP_TICKS
        match.stepForTest()
        assertEquals(0, match.goalBad, "one step left")
        match.stepForTest()
        assertEquals(100, match.goalBad)
    }

    private data class MayhemExpectation(val products: Int, val cash: Int)
}

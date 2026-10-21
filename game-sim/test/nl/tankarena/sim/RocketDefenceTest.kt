package nl.tankarena.sim

import nl.tankarena.content.LEGACY_TILE_SIZE
import nl.tankarena.content.MapMetadata
import nl.tankarena.content.MissionText
import nl.tankarena.content.TileLayers
import nl.tankarena.input.PlayerIntentFrame
import nl.tankarena.protocol.snapshot.EffectKind
import nl.tankarena.protocol.snapshot.GameEvent
import nl.tankarena.sim.legacy.AuthoredObject
import nl.tankarena.sim.legacy.CanonicalMapDefinition
import nl.tankarena.sim.legacy.ObjectKinds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Legacy determine_hit(): a rocket in flight (and a mine) is an object anything can hit — a flame
 * within 15 px, a shot within 3 px, a blast (original/src/check/hitobj.c, original/src/objects/rocket.c:40-46).
 */
class RocketDefenceTest {
    private val t = LEGACY_TILE_SIZE

    @Test
    fun `the flamethrower burns a rocket coming at the tank`() {
        val match = start()
        val tank = match.playerTankForTest(0)!!
        match.selectWeaponForTest(WEAPON_FLAME)
        // A rocket from far east, homing on the tank, which faces east.
        val rocket = launch(match, from = tank.positionX + 120 to tank.positionY, to = tank.positionX to tank.positionY)
        var explosions = 0
        repeat(300) {
            if (rocket.isDead) return@repeat
            val frame = match.stepForTest(mapOf(0 to PlayerIntentFrame(firePrimary = true)))
            explosions += frame.events.count { it is GameEvent.Effect && it.kind == EffectKind.LEXPLOSION }
        }
        assertTrue(rocket.isDead, "the rocket is burnt in flight")
        assertTrue(explosions >= 1)
        repeat(5) { match.stepForTest() }
        assertEquals(tank.maxArmor, tank.armor, "it never got through")
    }

    @Test
    fun `a blast destroys a rocket, a shot sets off a mine`() {
        val match = start()
        val tank = match.playerTankForTest(0)!!
        val rocket = launch(match, from = tank.positionX + 300 to tank.positionY + 60, to = tank.positionX + 300 to tank.positionY + 200)
        match.landMortarForTest(rocket.positionX.toInt(), rocket.positionY.toInt())
        match.stepForTest()
        assertTrue(rocket.isDead, "the mortar's first ring got it")

        val mine = match.injectArmedMineForTest(x = tank.positionX + 120, y = tank.positionY, damage = 7, radius = LIGHT_MINE_RADIUS_PX)
        match.selectWeaponForTest(WEAPON_MAIN)
        repeat(30) { if (!mine.isDead) match.stepForTest(mapOf(0 to PlayerIntentFrame(firePrimary = it == 0))) }
        assertTrue(mine.isDead, "the shell set the mine off")
    }

    private fun launch(match: Match, from: Pair<Int, Int>, to: Pair<Int, Int>): Rocket {
        val rocket = match.rocketFromRequest(null, Tank.RocketRequest(from.first, from.second, to.first, to.second, 30))
        match.objects.add(listOf(rocket))
        match.actorIds.getOrPut(rocket) { match.nextActorId++ }
        return rocket
    }

    private fun start(): Match {
        val width = 20
        val height = 9
        val cells = width * height
        val match = Match.fromCanonicalMap(
            CanonicalMapDefinition(
                metadata = MapMetadata(name = "rockets", widthTiles = width, heightTiles = height, missionCode = "TEST"),
                layers = TileLayers(
                    base = List(cells) { -1 },
                    solid = List(cells) { -1 },
                    top = List(cells) { -1 },
                    goalLayer = List(cells) { 0 },
                    bonusLayer = List(cells) { -1 },
                    manTypeLayer = List(cells) { 0 },
                    manAmountLayer = List(cells) { 0 },
                ),
                missionText = MissionText(),
                objects = listOf(
                    AuthoredObject("p1", ObjectKinds.PLAYER_START, 3 * t + 16, 4 * t + 16, mapOf("direction" to "4", "lives" to "3", "tankType" to "0")),
                ),
            ),
        )
        match.initialize()
        return match
    }
}

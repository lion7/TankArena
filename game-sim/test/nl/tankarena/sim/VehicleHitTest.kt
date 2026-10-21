package nl.tankarena.sim

import nl.tankarena.content.LEGACY_TILE_SIZE
import nl.tankarena.content.MapMetadata
import nl.tankarena.content.MissionText
import nl.tankarena.content.TileLayers
import nl.tankarena.input.PlayerIntentFrame
import nl.tankarena.sim.legacy.AuthoredObject
import nl.tankarena.sim.legacy.CanonicalMapDefinition
import nl.tankarena.sim.legacy.ObjectKinds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Legacy line_obj_hit_point() (original/src/check/hitobj.c): shots hit a vehicle's picture, not a box. */
class VehicleHitTest {

    private val row = 2

    @Test
    fun `a shot through the car's body hits it`() {
        val match = start(carOffsetY = 0)
        assertTrue(fireAndReturnCarArmor(match) < CAR_ARMOR)
    }

    @Test
    fun `a shot passing beside a car facing across it misses, though it is within 29 pixels`() {
        // A car facing east is a narrow picture; 11 px off its middle the shot runs past it.
        val match = start(carOffsetY = 11)
        val car = match.tanksForTest().single { it.tankType == CAR }
        assertTrue(!car.bodyCovers(car.positionX, car.positionY - 11) && !car.bodyCovers(car.positionX, car.positionY + 11))
        assertEquals(CAR_ARMOR, fireAndReturnCarArmor(match))
    }

    private fun fireAndReturnCarArmor(match: Match): Int {
        repeat(150) { match.tick(mapOf(0 to PlayerIntentFrame(firePrimary = it < 5))) }
        return match.tanksForTest().single { it.tankType == CAR }.armor
    }

    private fun centre(tile: Int) = tile * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2

    private fun start(carOffsetY: Int): Match {
        val width = 16
        val height = 5
        val cells = width * height
        val map = CanonicalMapDefinition(
            metadata = MapMetadata(name = "hit-fixture", widthTiles = width, heightTiles = height, missionCode = "TEST"),
            layers = TileLayers(
                base = List(cells) { -1 },
                solid = List(cells) { -1 },
                top = List(cells) { -1 },
                goalLayer = List(cells) { 0 },
                bonusLayer = List(cells) { 255 },
                manTypeLayer = List(cells) { 0 },
                manAmountLayer = List(cells) { 0 },
            ),
            missionText = MissionText(),
            objects = listOf(
                AuthoredObject(
                    id = "p1",
                    kind = ObjectKinds.PLAYER_START,
                    x = centre(2),
                    y = centre(row),
                    properties = mapOf("direction" to "4", "lives" to "1"),
                ),
                AuthoredObject(
                    id = "car",
                    kind = ObjectKinds.AI_TANK,
                    x = centre(7),
                    y = centre(row) + carOffsetY,
                    properties = mapOf(
                        "tankType" to CAR.toString(),
                        "direction" to "4",
                        "armor" to CAR_ARMOR.toString(),
                        "activationRadius" to "1",
                    ),
                ),
            ),
        )
        val match = Match.fromCanonicalMap(map, MatchMode.SINGLE)
        match.initialize()
        return match
    }

    private companion object {
        /** The car of the BEGIN missions. */
        const val CAR = 14
        const val CAR_ARMOR = 10
    }
}

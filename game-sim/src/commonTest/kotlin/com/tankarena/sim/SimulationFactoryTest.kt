package com.tankarena.sim

import com.tankarena.content.AuthoredObject
import com.tankarena.content.CanonicalMapDefinition
import com.tankarena.content.MapMetadata
import com.tankarena.content.MissionText
import com.tankarena.content.ObjectKinds
import com.tankarena.content.TileLayers
import kotlin.test.Test
import kotlin.test.assertEquals

class SimulationFactoryTest {
    @Test
    fun `builds simulation from canonical player starts and turrets`() {
        val map = CanonicalMapDefinition(
            metadata = MapMetadata(
                name = "fixture",
                widthTiles = 20,
                heightTiles = 10,
                missionCode = "BEGIN1",
            ),
            layers = TileLayers(
                base = List(200) { -1 },
                solid = List(200) { -1 },
                top = List(200) { -1 },
                goalLayer = List(200) { 0 },
                bonusLayer = List(200) { -1 },
                manTypeLayer = List(200) { 0 },
                manAmountLayer = List(200) { 0 },
            ),
            missionText = MissionText(),
            objects = listOf(
                AuthoredObject(
                    id = "p1",
                    kind = ObjectKinds.PLAYER_START,
                    x = 66,
                    y = 99,
                    properties = mapOf("direction" to "4"),
                ),
                AuthoredObject(
                    id = "turret-1",
                    kind = ObjectKinds.TURRET,
                    x = 132,
                    y = 165,
                ),
            ),
        )

        val simulation = SimulationFactory.fromCanonicalMap(map)
        val state = simulation.currentState()

        assertEquals(1, state.tanks.size)
        assertEquals(66, state.tanks.single().position.x)
        assertEquals(99, state.tanks.single().position.y)
        assertEquals(1, state.turrets.size)
        assertEquals(132, state.turrets.single().position.x)
        assertEquals(165, state.turrets.single().position.y)
    }
}


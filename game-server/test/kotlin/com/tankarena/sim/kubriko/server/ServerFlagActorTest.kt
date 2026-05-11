package com.tankarena.sim.kubriko.server

import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.SceneOffset
import com.pandulapeter.kubriko.types.SceneSize
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.MapMetadata
import com.tankarena.content.MissionText
import com.tankarena.content.TileLayers
import com.tankarena.protocol.snapshot.GameEvent
import com.tankarena.sim.kubriko.server.legacy.AuthoredObject
import com.tankarena.sim.kubriko.server.legacy.CanonicalMapDefinition
import com.tankarena.sim.kubriko.server.legacy.ObjectKinds
import kotlin.test.*
import kotlin.test.Test

class ServerFlagActorTest {

    private val metadata = MapMetadata(
        name = "test-flag",
        widthTiles = 33,
        heightTiles = 25,
        missionCode = "TEST",
    )

    private fun buildMatchWithFlag(
        flagX: Int = 10 * LEGACY_TILE_SIZE,
        flagY: Int = 10 * LEGACY_TILE_SIZE,
        tankX: Int = 5 * LEGACY_TILE_SIZE,
        tankY: Int = 5 * LEGACY_TILE_SIZE,
        flagType: Int = 0,
        flagNumber: Int = 0,
    ): ServerMatchPrototype {
        val sz = metadata.widthTiles * metadata.heightTiles
        val map = CanonicalMapDefinition(
            metadata = metadata,
            layers = TileLayers(
                base = List(sz) { 0 },
                top = List(sz) { -1 },
                solid = List(sz) { -1 },
                goalLayer = List(sz) { -1 },
                bonusLayer = List(sz) { -1 },
                manTypeLayer = List(sz) { 0 },
                manAmountLayer = List(sz) { 0 },
            ),
            missionText = MissionText(),
            objects = listOf(
                AuthoredObject(
                    id = "tank0",
                    kind = ObjectKinds.PLAYER_START,
                    x = tankX,
                    y = tankY,
                    properties = mapOf("direction" to "0", "lives" to "1"),
                ),
                AuthoredObject(
                    id = "flag0",
                    kind = ObjectKinds.FLAG,
                    x = flagX,
                    y = flagY,
                    properties = mapOf("flagType" to flagType.toString(), "number" to flagNumber.toString()),
                ),
            ),
        )
        val match = ServerMatchPrototype.fromCanonicalMap(map)
        match.initialize()
        return match
    }

    @Test
    fun `flag is created with correct properties`() {
        val match = buildMatchWithFlag(flagType = 1, flagNumber = 2)
        val snapshot = match.tick()
        val flags = snapshot.actors.filterIsInstance<com.tankarena.protocol.snapshot.FlagState>()
        assertEquals(1, flags.size)
        assertEquals(1, flags[0].flagType)
        assertEquals(2, flags[0].number)
        assertFalse(flags[0].isCarried)
    }

    @Test
    fun `flag is not carried initially`() {
        val match = buildMatchWithFlag()
        val snapshot = match.tick()
        val flags = snapshot.actors.filterIsInstance<com.tankarena.protocol.snapshot.FlagState>()
        assertEquals(1, flags.size)
        assertFalse(flags[0].isCarried)
    }

    @Test
    fun `flag pickup emits FlagCaptured event`() {
        val match = buildMatchWithFlag(
            flagX = 5 * LEGACY_TILE_SIZE + 10,
            flagY = 5 * LEGACY_TILE_SIZE + 10,
            tankX = 5 * LEGACY_TILE_SIZE,
            tankY = 5 * LEGACY_TILE_SIZE,
        )
        val snapshot = match.tick()
        val captured = snapshot.events.filterIsInstance<GameEvent.FlagCaptured>()
        assertEquals(1, captured.size)
        assertTrue(captured[0].tankActorId > 0)
        assertTrue(captured[0].flagActorId > 0)
    }

    @Test
    fun `flag follows carrier after pickup`() {
        val match = buildMatchWithFlag(
            flagX = 5 * LEGACY_TILE_SIZE + 10,
            flagY = 5 * LEGACY_TILE_SIZE + 10,
            tankX = 5 * LEGACY_TILE_SIZE,
            tankY = 5 * LEGACY_TILE_SIZE,
        )
        match.tick()
        val snapshot = match.tick()
        val flags = snapshot.actors.filterIsInstance<com.tankarena.protocol.snapshot.FlagState>()
        assertEquals(1, flags.size)
        assertTrue(flags[0].isCarried)
    }

    @Test
    fun `flag returns to home when carrier dies`() {
        val match = buildMatchWithFlag(
            flagX = 5 * LEGACY_TILE_SIZE + 10,
            flagY = 5 * LEGACY_TILE_SIZE + 10,
            tankX = 5 * LEGACY_TILE_SIZE,
            tankY = 5 * LEGACY_TILE_SIZE,
        )
        match.tick() // pickup
        match.queueDamageForTest(0, 999)
        val snapshot = match.tick() // damage drains, flag returns on same tick
        val flags = snapshot.actors.filterIsInstance<com.tankarena.protocol.snapshot.FlagState>()
        assertEquals(1, flags.size)
        assertFalse(flags[0].isCarried)
    }

    @Test
    fun `FlagReturned event emitted when carrier dies`() {
        val match = buildMatchWithFlag(
            flagX = 5 * LEGACY_TILE_SIZE + 10,
            flagY = 5 * LEGACY_TILE_SIZE + 10,
            tankX = 5 * LEGACY_TILE_SIZE,
            tankY = 5 * LEGACY_TILE_SIZE,
        )
        match.tick() // pickup
        match.queueDamageForTest(0, 999)
        val snapshot = match.tick() // damage drains, flag returns on same tick
        val returned = snapshot.events.filterIsInstance<GameEvent.FlagReturned>()
        assertEquals(1, returned.size)
    }

    @Test
    fun `multiple flags are independent`() {
        val sz = metadata.widthTiles * metadata.heightTiles
        val map = CanonicalMapDefinition(
            metadata = metadata,
            layers = TileLayers(
                base = List(sz) { 0 },
                top = List(sz) { -1 },
                solid = List(sz) { -1 },
                goalLayer = List(sz) { -1 },
                bonusLayer = List(sz) { -1 },
                manTypeLayer = List(sz) { 0 },
                manAmountLayer = List(sz) { 0 },
            ),
            missionText = MissionText(),
            objects = listOf(
                AuthoredObject(id = "tank0", kind = ObjectKinds.PLAYER_START, x = 5 * LEGACY_TILE_SIZE, y = 5 * LEGACY_TILE_SIZE, properties = mapOf("direction" to "0", "lives" to "1")),
                AuthoredObject(id = "flag0", kind = ObjectKinds.FLAG, x = 10 * LEGACY_TILE_SIZE, y = 10 * LEGACY_TILE_SIZE, properties = mapOf("flagType" to "0", "number" to "0")),
                AuthoredObject(id = "flag1", kind = ObjectKinds.FLAG, x = 20 * LEGACY_TILE_SIZE, y = 20 * LEGACY_TILE_SIZE, properties = mapOf("flagType" to "1", "number" to "1")),
            ),
        )
        val match = ServerMatchPrototype.fromCanonicalMap(map)
        match.initialize()
        val snapshot = match.tick()
        val flags = snapshot.actors.filterIsInstance<com.tankarena.protocol.snapshot.FlagState>()
        assertEquals(2, flags.size)
        assertEquals(0, flags[0].flagType)
        assertEquals(1, flags[1].flagType)
    }

    @Test
    fun `flag round-trips through serialization`() {
        val state = ServerFlagActor.State(
            body = BoxBody(
                initialPosition = SceneOffset(100f.sceneUnit, 200f.sceneUnit),
                initialSize = SceneSize(16f.sceneUnit, 16f.sceneUnit),
            ),
            flagType = 1,
            number = 3,
            isCarried = false,
            homeX = 100,
            homeY = 200,
        )
        val actor = ServerFlagActor(state)
        val saved = actor.save()
        assertEquals(1, saved.flagType)
        assertEquals(3, saved.number)
        assertEquals(100, saved.homeX)
        assertEquals(200, saved.homeY)
    }
}

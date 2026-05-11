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

class ServerProductActorTest {

    private val metadata = MapMetadata(
        name = "test-product",
        widthTiles = 33,
        heightTiles = 25,
        missionCode = "TEST",
    )

    private fun buildMatchWithProduct(
        productX: Int = 10 * LEGACY_TILE_SIZE,
        productY: Int = 10 * LEGACY_TILE_SIZE,
        tankX: Int = 5 * LEGACY_TILE_SIZE,
        tankY: Int = 5 * LEGACY_TILE_SIZE,
        productType: Int = 0,
        price: Int = 1,
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
                    id = "product0",
                    kind = ObjectKinds.PRODUCT,
                    x = productX,
                    y = productY,
                    properties = mapOf("productType" to productType.toString(), "price" to price.toString()),
                ),
            ),
        )
        val match = ServerMatchPrototype.fromCanonicalMap(map)
        match.initialize()
        return match
    }

    @Test
    fun `product is created with correct properties`() {
        val match = buildMatchWithProduct(productType = 5, price = 3)
        val snapshot = match.tick()
        val products = snapshot.actors.filterIsInstance<com.tankarena.protocol.snapshot.ProductState>()
        assertEquals(1, products.size)
        assertEquals(5, products[0].productType)
        assertEquals(3, products[0].price)
        assertFalse(products[0].isCollected)
    }

    @Test
    fun `product pickup emits ProductCollected event`() {
        val match = buildMatchWithProduct(
            productX = 5 * LEGACY_TILE_SIZE + 10,
            productY = 5 * LEGACY_TILE_SIZE + 10,
            tankX = 5 * LEGACY_TILE_SIZE,
            tankY = 5 * LEGACY_TILE_SIZE,
            price = 4,
        )
        val snapshot = match.tick()
        val collected = snapshot.events.filterIsInstance<GameEvent.ProductCollected>()
        assertEquals(1, collected.size)
        assertEquals(4, collected[0].price)
        assertTrue(collected[0].tankActorId > 0)
        assertTrue(collected[0].productActorId > 0)
    }

    @Test
    fun `product is marked collected after pickup`() {
        val match = buildMatchWithProduct(
            productX = 5 * LEGACY_TILE_SIZE + 10,
            productY = 5 * LEGACY_TILE_SIZE + 10,
            tankX = 5 * LEGACY_TILE_SIZE,
            tankY = 5 * LEGACY_TILE_SIZE,
        )
        match.tick()
        val snapshot = match.tick()
        val products = snapshot.actors.filterIsInstance<com.tankarena.protocol.snapshot.ProductState>()
        assertEquals(1, products.size)
        assertTrue(products[0].isCollected)
    }

    @Test
    fun `collected product is not picked up again`() {
        val match = buildMatchWithProduct(
            productX = 5 * LEGACY_TILE_SIZE + 10,
            productY = 5 * LEGACY_TILE_SIZE + 10,
            tankX = 5 * LEGACY_TILE_SIZE,
            tankY = 5 * LEGACY_TILE_SIZE,
        )
        match.tick() // first pickup
        val snapshot = match.tick() // second tick — no more events
        val collected = snapshot.events.filterIsInstance<GameEvent.ProductCollected>()
        assertEquals(0, collected.size)
    }

    @Test
    fun `multiple products are independent`() {
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
                AuthoredObject(id = "prod0", kind = ObjectKinds.PRODUCT, x = 10 * LEGACY_TILE_SIZE, y = 10 * LEGACY_TILE_SIZE, properties = mapOf("productType" to "0", "price" to "1")),
                AuthoredObject(id = "prod1", kind = ObjectKinds.PRODUCT, x = 20 * LEGACY_TILE_SIZE, y = 20 * LEGACY_TILE_SIZE, properties = mapOf("productType" to "5", "price" to "3")),
            ),
        )
        val match = ServerMatchPrototype.fromCanonicalMap(map)
        match.initialize()
        val snapshot = match.tick()
        val products = snapshot.actors.filterIsInstance<com.tankarena.protocol.snapshot.ProductState>()
        assertEquals(2, products.size)
        assertEquals(0, products[0].productType)
        assertEquals(5, products[1].productType)
    }

    @Test
    fun `product round-trips through serialization`() {
        val state = ServerProductActor.State(
            body = BoxBody(
                initialPosition = SceneOffset(100f.sceneUnit, 200f.sceneUnit),
                initialSize = SceneSize(16f.sceneUnit, 16f.sceneUnit),
            ),
            productType = 7,
            price = 5,
            isCollected = false,
        )
        val actor = ServerProductActor(state)
        val saved = actor.save()
        assertEquals(7, saved.productType)
        assertEquals(5, saved.price)
    }
}

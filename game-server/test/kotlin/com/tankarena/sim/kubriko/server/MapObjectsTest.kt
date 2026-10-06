package com.tankarena.sim.kubriko.server

import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.MapMetadata
import com.tankarena.content.MissionText
import com.tankarena.content.PictureCatalog
import com.tankarena.content.TankArenaWorld
import com.tankarena.content.TileLayers
import com.tankarena.input.PlayerIntentFrame
import com.tankarena.protocol.snapshot.AbombState
import com.tankarena.sim.kubriko.server.legacy.AuthoredObject
import com.tankarena.sim.kubriko.server.legacy.CanonicalMapDefinition
import com.tankarena.sim.kubriko.server.legacy.ObjectKinds
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Trains, destroyers, locks and enforcers (src/objects/train.c, destroy.c, lock.c, enforce.c). */
class MapObjectsTest {

    private val rails = 2
    private val railPicture = picture("RAILS_H")
    private val indestructible = (0 until 600).first {
        PictureCatalog.legacyRow(TankArenaWorld.DESERT, it)?.strength?.first() == StructureGrid.INDESTRUCTIBLE
    }

    @Test
    fun `an engine pulls its wagons along the rails, 31 px apart`() {
        val match = start(objects = train(engineTileX = 5, wagons = 2))
        val (engine, first, second) = match.trainsForTest()
        val startX = engine.positionX
        repeat(1000) { match.tick() }
        assertEquals(50, engine.speed, "legacy max_speed")
        assertTrue(engine.positionX - startX > 150, "the engine runs east along the rails (${engine.positionX - startX} px)")
        assertEquals(rails * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2, engine.positionY, "it stays on the rails")
        for ((front, rear) in listOf(engine to first, first to second)) {
            val gap = hypot((front.positionX - rear.positionX).toDouble(), (front.positionY - rear.positionY).toDouble())
            assertTrue(gap in 28.0..35.0, "wagons keep about 31 px apart (gap $gap)")
        }
        assertEquals(4, engine.direction)
    }

    @Test
    fun `a train crushes the vehicle on its rails`() {
        val match = start(objects = train(engineTileX = 3, wagons = 0) + enemy(tileX = 9, tileY = rails))
        val enemy = match.tanksForTest().single { it.team == 1 }
        repeat(1200) { match.tick() }
        assertEquals(0, enemy.armor)
    }

    @Test
    fun `a train ploughs through buildings but is wrecked by an indestructible one`() {
        val match = start(objects = train(engineTileX = 3, wagons = 0), solid = mapOf(9 to BUILDING, 20 to indestructible))
        val engine = match.trainsForTest().single()
        repeat(1500) { match.tick() }
        assertFalse(match.structures.isStanding(9, rails), "the building in its way is knocked down")
        repeat(2500) { match.tick() }
        assertFalse(engine.alive, "the indestructible one wrecks the train")
    }

    @Test
    fun `when the engine is destroyed the wagon behind it leads, and an A-bomb wagon goes off`() {
        val match = start(objects = train(engineTileX = 5, wagons = 1, wagonPicture = 7))
        val (engine, wagon) = match.trainsForTest()
        match.hitTrainForTest(engine, 60)
        assertFalse(engine.alive)
        assertTrue(wagon.driving, "the wagon now leads")
        assertNull(wagon.front)
        match.hitTrainForTest(wagon, 40)
        var flash = 0
        repeat(60) { flash = maxOf(flash, match.tick().flash) }
        assertTrue(flash > 0, "wagon 7 carries an A-bomb")
    }

    @Test
    fun `an immediate destroyer flattens buildings and vehicles in its radius at once`() {
        val match = start(
            solid = mapOf(7 to BUILDING, 9 to BUILDING, 10 to indestructible),
            objects = enemy(tileX = 8, tileY = 3) + destroyer(tileX = 8, radius = 70, immediate = true),
        )
        val enemy = match.tanksForTest().single { it.team == 1 }
        repeat(2) { match.tick() }
        assertFalse(match.structures.isStanding(7, rails))
        assertFalse(match.structures.isStanding(9, rails))
        assertTrue(match.structures.isStanding(10, rails), "indestructible structures survive")
        assertEquals(0, enemy.armor)
        assertTrue(match.playerTankForTest(0)!!.armor > 0, "the player is out of reach")
    }

    @Test
    fun `a lock brings down its locked structure when the building under it falls`() {
        val match = start(
            solid = mapOf(6 to BUILDING, 9 to BUILDING),
            objects = listOf(lock(tileX = 6, activation = 0, target = 0, targetTileX = 9)),
        )
        assertEquals(StructureGrid.INDESTRUCTIBLE, match.structures.strength(9, rails, StructureLayer.SOLID), "the locked building is indestructible")
        repeat(400) { match.tick(mapOf(0 to PlayerIntentFrame(firePrimary = true))) }
        assertFalse(match.structures.isStanding(6, rails))
        assertFalse(match.structures.isStanding(9, rails), "the lock took the locked building down")
        assertTrue(match.locksForTest().single().isFired)
    }

    @Test
    fun `a lock on a vehicle sets off a destroyer when the vehicle dies`() {
        val match = start(
            solid = mapOf(10 to BUILDING),
            objects = enemy(tileX = 6, tileY = 3) +
                lock(tileX = 6, tileY = 3, activation = 1, target = 1, targetTileX = 10, targetTileY = 3) +
                destroyer(tileX = 10, tileY = 3, radius = 40, immediate = false),
        )
        repeat(50) { match.tick() }
        assertTrue(match.structures.isStanding(10, rails), "the destroyer waits")
        match.queueDamageToEnemyForTest(1000)
        repeat(5) { match.tick() }
        assertFalse(match.structures.isStanding(10, rails), "the dead vehicle's lock set the destroyer off")
    }

    @Test
    fun `an enforcer makes the computer tanks in its radius fire its weapon`() {
        val match = start(objects = enemy(tileX = 8, tileY = 3) + enforcer(tileX = 8, tileY = 3, weapon = WEAPON_ABOMB, delay = 500))
        val enemy = match.tanksForTest().single { it.team == 1 }
        val ownWeapon = enemy.currentWeapon
        repeat(2) { match.tick() }
        assertTrue(match.snapshot().actors.any { it is AbombState }, "the enforced A-bomb was dropped")
        assertEquals(ownWeapon, enemy.currentWeapon, "without `change` the tank goes back to its own weapon")
        repeat(100) { match.tick() }
        assertEquals(1, match.snapshot().actors.count { it is AbombState }, "only once per delay")
    }

    // ---- Fixture ----

    private fun picture(type: String): Int = (0 until 1000).first { PictureCatalog.legacyRow(TankArenaWorld.DESERT, it)?.type == type }

    private fun centre(tile: Int) = tile * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2

    /**
     * An eastbound train as legacy make_train()/train_change() sets one up: each car's top-left at
     * `tile·33 - 16`, on a horizontal rail segment heading for the next tile.
     */
    private fun train(engineTileX: Int, wagons: Int, wagonPicture: Int = 1): List<AuthoredObject> =
        (0..wagons).map { i ->
            val tileX = engineTileX - i
            AuthoredObject(
                id = "car$i",
                kind = if (i == 0) ObjectKinds.TRAIN else ObjectKinds.WAGON,
                x = tileX * LEGACY_TILE_SIZE,
                y = rails * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                properties = buildMap {
                    put("ref", (100 + i).toString())
                    if (i > 0) put("frontRef", (99 + i).toString())
                    if (i < wagons) put("rearRef", (101 + i).toString())
                    put("driving", if (i == 0) "1" else "0")
                    put("direction", "4")
                    put("picture", if (i == 0) "0" else wagonPicture.toString())
                    put("smokes", if (i == 0) "1" else "0")
                    put("armor", if (i == 0) "60" else "40")
                    put("railsStart", "-16.0")
                    put("railsType", RAILS_HORIZONTAL.toString())
                    put("railsDir", "1")
                    put("railsX", centre(tileX).toString())
                    put("railsY", centre(rails).toString())
                    put("endType", RAILS_HORIZONTAL.toString())
                    put("endDir", "1")
                    put("endX", (tileX + 1).toString())
                    put("endY", rails.toString())
                },
            )
        }

    private fun enemy(tileX: Int, tileY: Int) = listOf(
        AuthoredObject(
            id = "enemy",
            kind = ObjectKinds.AI_TANK,
            x = centre(tileX),
            y = centre(tileY),
            properties = mapOf("activationRadius" to "1", "armor" to "30"),
        ),
    )

    private fun destroyer(tileX: Int, tileY: Int = rails, radius: Int, immediate: Boolean) = listOf(
        AuthoredObject(
            id = "destroyer",
            kind = ObjectKinds.DESTROYER,
            x = centre(tileX),
            y = centre(tileY),
            properties = mapOf("radius" to radius.toString(), "immediate" to if (immediate) "-1" else "0", "what" to "3"),
        ),
    )

    private fun lock(tileX: Int, tileY: Int = rails, activation: Int, target: Int, targetTileX: Int, targetTileY: Int = rails) =
        AuthoredObject(
            id = "lock",
            kind = ObjectKinds.LOCK,
            x = centre(tileX),
            y = centre(tileY),
            properties = mapOf(
                "activation" to activation.toString(),
                "target" to target.toString(),
                "lockX" to centre(targetTileX).toString(),
                "lockY" to centre(targetTileY).toString(),
            ),
        )

    private fun enforcer(tileX: Int, tileY: Int, weapon: Int, delay: Int) = listOf(
        AuthoredObject(
            id = "enforcer",
            kind = ObjectKinds.ENFORCER,
            x = centre(tileX),
            y = centre(tileY),
            properties = mapOf(
                "radius" to "30",
                "good" to "0",
                "evil" to "-1",
                "weapon" to weapon.toString(),
                "fire" to "-1",
                "change" to "0",
                "delay" to delay.toString(),
            ),
        ),
    )

    private fun start(
        width: Int = 40,
        solid: Map<Int, Int> = emptyMap(),
        objects: List<AuthoredObject> = emptyList(),
    ): ServerMatchPrototype {
        val height = 5
        val cells = width * height
        val player = AuthoredObject(
            id = "p1",
            kind = ObjectKinds.PLAYER_START,
            x = centre(2),
            y = centre(rails),
            properties = mapOf("direction" to "4", "lives" to "1"),
        )
        // The player sits on row 0 unless a test needs it to shoot along the rails.
        val playerOnRails = objects.none { it.kind == ObjectKinds.TRAIN }
        val map = CanonicalMapDefinition(
            metadata = MapMetadata(name = "objects-fixture", widthTiles = width, heightTiles = height, missionCode = "TEST"),
            layers = TileLayers(
                base = List(cells) { if (it / width == rails) railPicture else -1 },
                solid = List(cells) { index -> if (index / width == rails) solid[index % width] ?: -1 else -1 },
                top = List(cells) { -1 },
                goalLayer = List(cells) { 0 },
                bonusLayer = List(cells) { 255 },
                manTypeLayer = List(cells) { 0 },
                manAmountLayer = List(cells) { 0 },
            ),
            missionText = MissionText(),
            objects = listOf(if (playerOnRails) player else player.copy(y = centre(0))) + objects,
        )
        val match = ServerMatchPrototype.fromCanonicalMap(map, MatchMode.SINGLE)
        match.initialize()
        return match
    }

    private companion object {
        /** Desert picture 8 (@ASEAA): a building of strength 20. */
        const val BUILDING = 8
    }
}

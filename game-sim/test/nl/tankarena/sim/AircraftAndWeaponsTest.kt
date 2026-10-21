package nl.tankarena.sim

import nl.tankarena.content.LEGACY_TILE_SIZE
import nl.tankarena.content.MapMetadata
import nl.tankarena.content.MissionText
import nl.tankarena.content.PictureCatalog
import nl.tankarena.content.TankArenaWorld
import nl.tankarena.content.TileLayers
import nl.tankarena.input.PlayerIntentFrame
import nl.tankarena.protocol.snapshot.AbombState
import nl.tankarena.protocol.snapshot.BonusState
import nl.tankarena.protocol.snapshot.EffectKind
import nl.tankarena.protocol.snapshot.GameEvent
import nl.tankarena.protocol.snapshot.ManState
import nl.tankarena.protocol.snapshot.ProjectileKind
import nl.tankarena.protocol.snapshot.ProjectileOwnerKind
import nl.tankarena.protocol.snapshot.ProjectileState
import nl.tankarena.protocol.snapshot.TankState
import nl.tankarena.sim.legacy.AuthoredObject
import nl.tankarena.sim.legacy.CanonicalMapDefinition
import nl.tankarena.sim.legacy.ObjectKinds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Helicopters and planes, the remaining weapons, bonuses and soldiers (original/src/tanks, original/src/objects). */
class AircraftAndWeaponsTest {

    private val row = 2
    private val east = PlayerIntentFrame(forward = true)

    @Test
    fun `a helicopter hovers at its height and flies over buildings`() {
        val match = start(solid = mapOf(6 to BUILDING), player = Player(tankType = CHOPPER))
        val chopper = assertNotNull(match.playerTankForTest(0))
        assertEquals(7, chopper.height, "legacy make_tank(): a helicopter starts at its props.h")
        repeat(500) { match.tick(mapOf(0 to east)) }
        assertTrue(chopper.positionX > 7 * LEGACY_TILE_SIZE, "it flies over the building (x=${chopper.positionX})")
        assertEquals(7, chopper.height)
        val state = match.snapshot().actors.filterIsInstance<TankState>().single { it.height == 7 }
        assertTrue(state.chopper, "the client draws the rotor's shadow too")
    }

    @Test
    fun `a helicopter that runs out of fuel crashes`() {
        val match = start(player = Player(tankType = CHOPPER, fuel = 30))
        val chopper = assertNotNull(match.playerTankForTest(0))
        repeat(300) { match.tick(mapOf(0 to east)) }
        assertEquals(0, chopper.armor, "flying without fuel is fatal")
        repeat(300) { match.tick() }
        assertEquals(1, chopper.height, "the wreck drops to the ground")
    }

    @Test
    fun `a plane takes off from a runway`() {
        val runway = picture("RUNWAY")
        val match = start(width = 40, base = (0 until 40).associateWith { runway }, player = Player(tankType = PLANE, tileX = 1))
        val plane = assertNotNull(match.playerTankForTest(0))
        assertEquals(1, plane.height, "planes start on the ground")
        var highest = 1
        repeat(400) {
            match.tick(mapOf(0 to east))
            highest = maxOf(highest, plane.height)
        }
        assertTrue(plane.armor > 0, "rolling along the runway is safe")
        assertTrue(highest > 1, "its speed gives it lift (highest $highest)")
    }

    @Test
    fun `a plane rolling on anything but a runway crashes`() {
        val match = start(width = 20, player = Player(tankType = PLANE, tileX = 1))
        val plane = assertNotNull(match.playerTankForTest(0))
        repeat(100) { match.tick(mapOf(0 to east)) }
        assertEquals(0, plane.armor)
    }

    @Test
    fun `aircraft and launchers fire small rockets that trail exhaust`() {
        val match = start(width = 20, player = Player(tankType = CHOPPER))
        var rocket: ProjectileState? = null
        var exhaust = false
        repeat(40) {
            val frame = match.tick(mapOf(0 to PlayerIntentFrame(firePrimary = it == 0)))
            rocket = rocket ?: frame.actors.filterIsInstance<ProjectileState>().firstOrNull()
            exhaust = exhaust || frame.events.any { it is GameEvent.Effect && it.kind == EffectKind.EXHAUST }
        }
        assertEquals(ProjectileKind.SROCK, assertNotNull(rocket).kind)
        assertEquals(4, rocket.direction, "it points east")
        assertTrue(exhaust)
    }

    @Test
    fun `the mortar kicks its vehicle back`() {
        val match = start(width = 20, player = Player(ammo = ammo(WEAPON_MORTAR to 3)))
        match.selectWeaponForTest(WEAPON_MORTAR)
        val tank = assertNotNull(match.playerTankForTest(0))
        match.tick(mapOf(0 to PlayerIntentFrame(firePrimary = true)))
        assertTrue(tank.velocityX < -50f, "legacy tank_add_impulse(…, 50): 100 px/s back (vx=${tank.velocityX})")
    }

    @Test
    fun `an A-bomb counts down, then flattens buildings and hurts vehicles`() {
        val match = start(
            width = 20,
            solid = mapOf(6 to BUILDING),
            player = Player(ammo = ammo(WEAPON_ABOMB to 1)),
            enemyAtTileX = 8,
        )
        match.selectWeaponForTest(WEAPON_ABOMB)
        val enemy = match.tanksForTest().single { it.team == 1 }
        match.tick(mapOf(0 to PlayerIntentFrame(firePrimary = true)))
        // Drive away from the bomb.
        repeat(170) { match.tick(mapOf(0 to PlayerIntentFrame(reverse = true))) }
        assertTrue(match.snapshot().actors.any { it is AbombState && !it.exploding }, "the bomb is still counting down")
        assertEquals(30, enemy.armor)
        var flash = 0
        repeat(300) { flash = maxOf(flash, match.tick().flash) }
        assertTrue(flash > 30, "the screen flashes white (peak $flash)")
        assertFalse(match.structures.isStanding(6, row), "the building is flattened")
        assertTrue(enemy.armor < 30, "the enemy is hurt (${enemy.armor})")
        assertTrue(match.snapshot().actors.none { it is AbombState })
    }

    @Test
    fun `the atomic tank goes off when it is destroyed`() {
        val match = start(width = 20, enemyAtTileX = 8, enemyTankType = Tank.ATOMIC_TANK)
        match.queueDamageToEnemyForTest(1000)
        var flash = 0
        repeat(120) { flash = maxOf(flash, match.tick().flash) }
        assertTrue(flash > 0)
    }

    @Test
    fun `soldiers jump out towards the marker, shoot and die under the tracks`() {
        val match = start(width = 20, player = Player(ammo = ammo(WEAPON_GUNNERS to 5)))
        match.selectWeaponForTest(WEAPON_GUNNERS)
        // Hold fire and steer the marker east, then let go.
        repeat(30) { match.tick(mapOf(0 to PlayerIntentFrame(firePrimary = true, turnRight = true))) }
        match.tick()
        var soldier: ManState? = null
        var soldierShot = false
        repeat(150) {
            val frame = match.tick()
            soldier = soldier ?: frame.actors.filterIsInstance<ManState>().firstOrNull()
            soldierShot = soldierShot || frame.actors.any { it is ProjectileState && it.ownerKind == ProjectileOwnerKind.MAN }
        }
        val dropped = assertNotNull(soldier, "a gunner jumped out")
        assertTrue(dropped.x > match.playerTankForTest(0)!!.positionX, "he went towards the marker")
        assertTrue(soldierShot, "and fired")
        var blood = false
        repeat(300) {
            val frame = match.tick(mapOf(0 to east))
            blood = blood || frame.events.any { it is GameEvent.Effect && it.kind == EffectKind.BLOOD }
        }
        assertTrue(blood, "running a soldier over kills him")
        assertTrue(match.snapshot().actors.none { it is ManState })
    }

    @Test
    fun `bonuses lie on open tiles of maps without random bonuses and are picked up`() {
        val match = start(width = 12, bonus = mapOf(5 to 4), randomBonus = false)
        val tank = assertNotNull(match.playerTankForTest(0))
        val rockets = tank.ammoFor(WEAPON_ROCKET)
        assertEquals(4, match.snapshot().actors.filterIsInstance<BonusState>().single().bonusType)
        repeat(300) { match.tick(mapOf(0 to east)) }
        assertEquals(rockets + 3, tank.ammoFor(WEAPON_ROCKET))
        val hud = match.buildPlayerViews().single().hud
        assertEquals("PICKED UP" to "3 ROCKETS", hud.messageLine1 to hud.messageLine2)
        assertTrue(match.snapshot().actors.none { it is BonusState })
    }

    @Test
    fun `a destroyed building leaves its bonus and its paratroopers`() {
        val match = start(
            width = 12,
            solid = mapOf(6 to BUILDING),
            bonus = mapOf(6 to 13),
            soldiers = mapOf(6 to 3),
            randomBonus = false,
        )
        assertTrue(match.snapshot().actors.none { it is BonusState }, "no bonus while the building stands")
        var ticks = 0
        while (match.structures.isStanding(6, row) && ticks++ < 300) match.tick(mapOf(0 to PlayerIntentFrame(firePrimary = true)))
        assertFalse(match.structures.isStanding(6, row))
        val actors = match.snapshot().actors
        assertEquals(13, actors.filterIsInstance<BonusState>().single().bonusType)
        assertTrue(actors.count { it is ManState } in 1..3, "paratroopers jumped out (${actors.count { it is ManState }})")
    }

    @Test
    fun `an A-bomb box goes off when it is destroyed`() {
        val match = start(width = 20, solid = mapOf(8 to picture("ABOX")))
        var flash = 0
        repeat(1500) { flash = maxOf(flash, match.tick(mapOf(0 to PlayerIntentFrame(firePrimary = true))).flash) }
        assertTrue(flash > 0, "the box went off")
    }

    @Test
    fun `smoke screen, cloak and speed-up`() {
        val match = start(width = 30, player = Player(ammo = ammo(WEAPON_SMOKE to 1, WEAPON_CLOAK to 1, WEAPON_SPEEDUP to 1)))
        val tank = assertNotNull(match.playerTankForTest(0))
        match.selectWeaponForTest(WEAPON_SMOKE)
        var clouds = 0
        repeat(67) {
            val frame = match.stepForTest(mapOf(0 to PlayerIntentFrame(firePrimary = it == 0)))
            clouds += frame.events.count { it is GameEvent.Effect && it.kind == EffectKind.SMOKE_SCREEN }
        }
        assertTrue(clouds > 5, "a smoke screen puffs clouds ($clouds)")

        match.selectWeaponForTest(WEAPON_CLOAK)
        match.stepForTest(mapOf(0 to PlayerIntentFrame(firePrimary = true)))
        assertTrue(match.snapshot().actors.filterIsInstance<TankState>().single().cloaked)
        // Legacy write_radar(): cloaked vehicles are not on the radar, the viewer's own neither.
        assertTrue(match.buildPlayerViews().single().radar.none { it.color == nl.tankarena.protocol.snapshot.RadarColor.SELF })

        val normalTop = topSpeed(match, ticks = 300)
        match.selectWeaponForTest(WEAPON_SPEEDUP)
        match.stepForTest(mapOf(0 to PlayerIntentFrame(firePrimary = true)))
        val boostedTop = topSpeed(match, ticks = 300)
        assertTrue(boostedTop > normalTop * 1.3f, "speed-up: $normalTop → $boostedTop px/s")
        assertTrue(tank.armor > 0)
    }

    @Test
    fun `vehicles that end up on top of each other are both destroyed`() {
        val match = start(width = 20, enemyAtTileX = 8)
        val tank = assertNotNull(match.playerTankForTest(0))
        val enemy = match.tanksForTest().single { it.team == 1 }
        tank.teleportTo(enemy.positionX + 3, enemy.positionY)
        repeat(2 * STEP_TICKS) { match.tick() }
        assertEquals(0, tank.armor)
        assertEquals(0, enemy.armor)
    }

    @Test
    fun `most weapons cannot be fired from the water`() {
        val water = picture("WATER")
        val match = start(width = 12, base = mapOf(2 to water), player = Player(ammo = ammo(WEAPON_MAIN to 10, WEAPON_CHAIN to 100)))
        match.selectWeaponForTest(WEAPON_CHAIN)
        repeat(5) { match.stepForTest(mapOf(0 to PlayerIntentFrame(firePrimary = true))) }
        assertTrue(match.snapshot().actors.none { it is ProjectileState }, "no chain gun from the water")
        match.selectWeaponForTest(WEAPON_MAIN)
        repeat(5) { match.stepForTest(mapOf(0 to PlayerIntentFrame(firePrimary = true))) }
        assertTrue(match.snapshot().actors.any { it is ProjectileState }, "a turret tank's main gun still fires")
    }

    private fun topSpeed(match: Match, ticks: Int): Float {
        val tank = match.playerTankForTest(0)!!
        var top = 0f
        // About a second forward, then a second in reverse.
        repeat(ticks / STEP_TICKS) {
            match.stepForTest(mapOf(0 to PlayerIntentFrame(reverse = it % 66 >= 33, forward = it % 66 < 33)))
            top = maxOf(top, tank.speed)
        }
        return top
    }

    private data class Player(
        val tankType: Int = 0,
        val tileX: Int = 2,
        val fuel: Int = LEGACY_MAX_FUEL,
        val ammo: List<Int>? = null,
    )

    private fun ammo(vararg counts: Pair<Int, Int>): List<Int> = List(WEAPON_COUNT) { weapon -> counts.toMap()[weapon] ?: 0 }

    private fun picture(type: String): Int =
        (0 until 1000).first { PictureCatalog.legacyRow(TankArenaWorld.DESERT, it)?.type == type }

    private fun start(
        width: Int = 12,
        base: Map<Int, Int> = emptyMap(),
        solid: Map<Int, Int> = emptyMap(),
        bonus: Map<Int, Int> = emptyMap(),
        soldiers: Map<Int, Int> = emptyMap(),
        randomBonus: Boolean = true,
        player: Player = Player(),
        enemyAtTileX: Int? = null,
        enemyTankType: Int = 0,
    ): Match {
        val height = 5
        val cells = width * height
        fun onRow(values: Map<Int, Int>, default: Int) = List(cells) { index -> if (index / width == row) values[index % width] ?: default else default }
        val objects = mutableListOf(
            AuthoredObject(
                id = "p1",
                kind = ObjectKinds.PLAYER_START,
                x = player.tileX * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                y = row * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                properties = buildMap {
                    put("direction", "4")
                    put("lives", "1")
                    put("tankType", player.tankType.toString())
                    put("fuel", player.fuel.toString())
                    player.ammo?.let { put("ammo", it.joinToString(",")) }
                },
            ),
        )
        if (enemyAtTileX != null) {
            objects += AuthoredObject(
                id = "e1",
                kind = ObjectKinds.AI_TANK,
                x = enemyAtTileX * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                y = row * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                // A dormant computer tank: it neither moves nor shoots.
                properties = mapOf("tankType" to enemyTankType.toString(), "activationRadius" to "1", "armor" to "30"),
            )
        }
        val map = CanonicalMapDefinition(
            metadata = MapMetadata(
                name = "weapons-fixture",
                widthTiles = width,
                heightTiles = height,
                missionCode = "TEST",
                randomBonus = randomBonus,
            ),
            layers = TileLayers(
                base = onRow(base, -1),
                solid = onRow(solid, -1),
                top = List(cells) { -1 },
                goalLayer = List(cells) { 0 },
                bonusLayer = onRow(bonus, 255),
                manTypeLayer = onRow(soldiers.mapValues { 1 }, 0),
                manAmountLayer = onRow(soldiers, 0),
            ),
            missionText = MissionText(),
            objects = objects,
        )
        val match = Match.fromCanonicalMap(map, MatchMode.SINGLE)
        match.initialize()
        return match
    }

    private companion object {
        /** Desert picture 8 (@ASEAA): a building of strength 20. */
        const val BUILDING = 8
        const val CHOPPER = 5
        const val PLANE = 4
    }
}

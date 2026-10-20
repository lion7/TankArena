package com.tankarena.sim

import com.tankarena.sim.legacy.AuthoredObject
import com.tankarena.sim.legacy.CanonicalMapDefinition
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.MapMetadata
import com.tankarena.content.MissionText
import com.tankarena.sim.legacy.ObjectKinds
import com.tankarena.content.TileLayers
import com.tankarena.input.PlayerIntentFrame
import com.tankarena.protocol.snapshot.ExplosionKind
import com.tankarena.protocol.snapshot.GameEvent
import com.tankarena.protocol.snapshot.LockMarkerState
import com.tankarena.protocol.snapshot.TankState
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Legacy rocket (src/tanks/fire.c tank_marker_control(), src/objects/rocket.c): fire puts a
 * lock-on marker on the tank, the drive keys steer it while fire is held, and releasing fire
 * launches a rocket that homes on whatever is under the marker.
 */
class RocketTest {

    @Test
    fun `holding fire with rockets selected steers a marker instead of the tank`() {
        val prototype = Match.fromCanonicalMap(twoTankMap())
        prototype.initialize()
        prototype.selectWeaponForTest(WEAPON_ROCKET)
        val shooter = prototype.playerTankForTest(0)!!
        assertEquals(WEAPON_ROCKET, shooter.currentWeapon)
        val startX = shooter.positionX
        val startY = shooter.positionY

        prototype.stepForTest(mapOf(0 to PlayerIntentFrame(firePrimary = true)))
        assertTrue(shooter.lockActive, "fire should bring out the marker")
        repeat(30) { prototype.stepForTest(mapOf(0 to PlayerIntentFrame(firePrimary = true, reverse = true))) }

        assertEquals(startX, shooter.positionX, "the tank must not drive while the marker is out")
        assertEquals(startY, shooter.positionY, "the tank must not drive while the marker is out")
        assertTrue(shooter.lockY > startY + 50, "reverse should move the marker down — at ${shooter.lockY}")
        assertTrue(prototype.snapshotRockets().isEmpty(), "no rocket before fire is released")
        val view = prototype.buildPlayerViews().single { it.playerId == 0 }
        assertEquals(shooter.lockX, view.cameraCenterX, "the camera follows the marker")
        assertEquals(shooter.lockY, view.cameraCenterY, "the camera follows the marker")
        assertTrue(view.lockMarker != null)
        prototype.dispose()
    }

    @Test
    fun `releasing fire launches a rocket that homes on the tank under the marker`() {
        val prototype = Match.fromCanonicalMap(twoTankMap())
        prototype.initialize()
        prototype.selectWeaponForTest(WEAPON_ROCKET)
        val victimBefore = prototype.snapshot().enemyTank().armor
        val victim = prototype.tanksForTest().single { it.playerIndex < 0 }

        // The victim drives around, so keep chasing its current position.
        steerMarkerTo(prototype, { victim.positionX }, { victim.positionY })
        assertEquals(LockMarkerState.ON_TARGET, prototype.buildPlayerViews().single().lockMarker?.state)
        prototype.stepForTest(mapOf(0 to PlayerIntentFrame()))
        assertEquals(1, prototype.snapshotRockets().size, "releasing fire launches the rocket")

        var exploded = false
        repeat(1000) {
            if (exploded) return@repeat
            val frame = prototype.stepForTest()
            exploded = frame.events.any { it is GameEvent.Explosion && it.kind == ExplosionKind.ROCKET }
        }
        prototype.stepForTest()
        assertTrue(exploded, "rocket should reach its target")
        val victimAfter = prototype.snapshot().enemyTank().armor
        assertTrue(victimAfter < victimBefore, "victim armor should drop — was $victimBefore, now $victimAfter")
        prototype.dispose()
    }

    @Test
    fun `the marker stays within 450 px of the tank`() {
        val prototype = Match.fromCanonicalMap(soloTankMap(direction = 4))
        prototype.initialize()
        prototype.selectWeaponForTest(WEAPON_ROCKET)
        val shooter = prototype.playerTankForTest(0)!!
        prototype.stepForTest(mapOf(0 to PlayerIntentFrame(firePrimary = true)))
        repeat(400) { prototype.stepForTest(mapOf(0 to PlayerIntentFrame(firePrimary = true, turnRight = true))) }
        val distance = shooter.lockX - shooter.positionX
        assertTrue(distance in 400 until LOCK_MAX_RANGE_PX, "marker should stop just short of 450 px, got $distance")
        prototype.dispose()
    }

    @Test
    fun `a rocket without a target flies to the marker and explodes there`() {
        val prototype = Match.fromCanonicalMap(soloTankMap(direction = 4))
        prototype.initialize()
        prototype.selectWeaponForTest(WEAPON_ROCKET)
        val shooter = prototype.playerTankForTest(0)!!
        val goalX = shooter.positionX + 200
        val goalY = shooter.positionY
        steerMarkerTo(prototype, { goalX }, { goalY })
        val targetX = shooter.lockX
        prototype.stepForTest(mapOf(0 to PlayerIntentFrame()))

        var explosion: GameEvent.Explosion? = null
        repeat(1000) {
            if (explosion != null) return@repeat
            explosion = prototype.stepForTest().events.filterIsInstance<GameEvent.Explosion>().firstOrNull()
        }
        val blast = assertNotNull(explosion, "rocket should arrive")
        assertTrue(abs(blast.x - targetX) <= ROCKET_ARRIVAL_PX, "blast at ${blast.x}, marker at $targetX")
        assertTrue(prototype.snapshotRockets().isEmpty())
        prototype.dispose()
    }

    /** Hold fire and steer the marker until it is within a few pixels of ([x], [y]), which may move. */
    @Test
    fun `a guided rocket leaves a puff of exhaust 11 px behind it, every 10 to 29 ticks`() {
        val prototype = Match.fromCanonicalMap(twoTankMap())
        prototype.initialize()
        prototype.selectWeaponForTest(WEAPON_ROCKET)
        val victim = prototype.tanksForTest().single { it.playerIndex < 0 }
        steerMarkerTo(prototype, { victim.positionX }, { victim.positionY })
        prototype.stepForTest(mapOf(0 to PlayerIntentFrame()))
        val puffs = mutableListOf<Long>()
        repeat(60) { step ->
            val frame = prototype.stepForTest()
            if (frame.events.any { it is GameEvent.Effect && it.kind == com.tankarena.protocol.snapshot.EffectKind.ROCKET_EXHAUST }) puffs += step.toLong()
        }
        assertTrue(puffs.size >= 3, "puffs at steps $puffs")
        assertTrue(puffs.zipWithNext { a, b -> (b - a) * STEP_TICKS }.all { it in 12..30 }, "puffs at steps $puffs")
    }

    private fun steerMarkerTo(prototype: Match, x: () -> Int, y: () -> Int) {
        val shooter = prototype.playerTankForTest(0)!!
        prototype.stepForTest(mapOf(0 to PlayerIntentFrame(firePrimary = true)))
        repeat(600) {
            val dx = x() - shooter.lockX
            val dy = y() - shooter.lockY
            if (abs(dx) <= 4 && abs(dy) <= 4) return
            prototype.stepForTest(
                mapOf(
                    0 to PlayerIntentFrame(
                        firePrimary = true,
                        turnLeft = dx < -4,
                        turnRight = dx > 4,
                        forward = dy < -4,
                        reverse = dy > 4,
                    ),
                ),
            )
        }
    }

    private fun com.tankarena.protocol.snapshot.WorldSnapshot.enemyTank(): TankState =
        actors.filterIsInstance<TankState>().single { !it.controlled }

    private fun soloTankMap(direction: Int): CanonicalMapDefinition {
        val widthTiles = 110
        val heightTiles = 5
        return canonicalMap(
            widthTiles,
            heightTiles,
            solid = MutableList(widthTiles * heightTiles) { -1 },
            players = listOf(
                AuthoredObject(
                    id = "p1",
                    kind = ObjectKinds.PLAYER_START,
                    x = 4 * LEGACY_TILE_SIZE,
                    y = 2 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    properties = mapOf("direction" to direction.toString(), "lives" to "3"),
                ),
            ),
        )
    }

    private fun twoTankMap(): CanonicalMapDefinition {
        val widthTiles = 12
        val heightTiles = 8
        return canonicalMap(
            widthTiles,
            heightTiles,
            solid = MutableList(widthTiles * heightTiles) { -1 },
            players = listOf(
                AuthoredObject(
                    id = "shooter",
                    kind = ObjectKinds.PLAYER_START,
                    x = 2 * LEGACY_TILE_SIZE,
                    y = 2 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    properties = mapOf("direction" to "4", "lives" to "3"),
                ),
                AuthoredObject(
                    id = "victim",
                    kind = ObjectKinds.AI_TANK,
                    x = 9 * LEGACY_TILE_SIZE,
                    y = 5 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    properties = mapOf("direction" to "12", "lives" to "3"),
                ),
            ),
        )
    }

    private fun canonicalMap(
        widthTiles: Int,
        heightTiles: Int,
        solid: List<Int>,
        players: List<AuthoredObject>,
    ): CanonicalMapDefinition {
        val cellCount = widthTiles * heightTiles
        return CanonicalMapDefinition(
            metadata = MapMetadata(
                name = "rocket-fixture",
                widthTiles = widthTiles,
                heightTiles = heightTiles,
                missionCode = "TEST",
            ),
            layers = TileLayers(
                base = List(cellCount) { -1 },
                solid = solid,
                top = List(cellCount) { -1 },
                goalLayer = List(cellCount) { 0 },
                bonusLayer = List(cellCount) { -1 },
                manTypeLayer = List(cellCount) { 0 },
                manAmountLayer = List(cellCount) { 0 },
            ),
            missionText = MissionText(),
            objects = players,
        )
    }
}

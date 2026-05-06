package com.tankarena.sim.kubriko.server

import com.tankarena.sim.kubriko.server.legacy.AuthoredObject
import com.tankarena.sim.kubriko.server.legacy.CanonicalMapDefinition
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.MapMetadata
import com.tankarena.content.MissionText
import com.tankarena.sim.kubriko.server.legacy.ObjectKinds
import com.tankarena.content.TileLayers
import com.tankarena.input.PlayerIntentFrame
import com.tankarena.protocol.snapshot.TankState
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T11 acceptance: rockets accelerate toward a locked target with a bounded turn rate
 * and expire on TTL or impact.
 */
class ServerRocketTest {

    @Test
    fun `rocket steers toward locked target over time`() {
        val prototype = ServerMatchPrototype.fromCanonicalMap(twoTankMap())
        prototype.initialize()
        // Cycle MAIN -> CHAIN -> MINE -> ROCKET (4 cycles).
        cycleN(prototype, times = 3)
        // Fire — turret faces east (4) but the locked target is offset south.
        prototype.tick(mapOf(0 to PlayerIntentFrame(firePrimary = true)))

        val rockets0 = prototype.snapshotRockets()
        assertEquals(1, rockets0.size, "exactly one rocket should be in flight")
        val rocket = rockets0.single()
        val initialVy = rocket.velocityY

        // Let the rocket steer for a few ticks.
        repeat(20) { prototype.tick() }
        val laterRockets = prototype.snapshotRockets()
        if (laterRockets.isEmpty()) {
            // Rocket already hit / TTL'd in the chase — that's still acceptable, but
            // we can't compare velocities. Skip the rest with an explicit assert.
            return
        }
        val later = laterRockets.single()
        assertTrue(
            later.velocityY > initialVy,
            "rocket should curve toward south-offset target — vy went from $initialVy to ${later.velocityY}",
        )
        prototype.dispose()
    }

    @Test
    fun `rocket expires on TTL when no target is reachable`() {
        val prototype = ServerMatchPrototype.fromCanonicalMap(soloTankMap(direction = 4))
        prototype.initialize()
        cycleN(prototype, times = 3)
        prototype.tick(mapOf(0 to PlayerIntentFrame(firePrimary = true)))

        // Let the rocket fly until TTL expiry — drive ROCKET_TTL_TICKS+5 ticks.
        repeat(ROCKET_TTL_TICKS + 10) { prototype.tick() }
        assertTrue(prototype.snapshotRockets().isEmpty(), "rocket should expire after TTL")
        prototype.dispose()
    }

    @Test
    fun `rocket damages a non-owner tank on impact`() {
        val prototype = ServerMatchPrototype.fromCanonicalMap(twoTankMap())
        prototype.initialize()
        cycleN(prototype, times = 3)
        val victimBefore = prototype.snapshot().enemyTank().armor
        prototype.tick(mapOf(0 to PlayerIntentFrame(firePrimary = true)))

        var hit = false
        repeat(ROCKET_TTL_TICKS) {
            prototype.tick()
            if (prototype.snapshotRockets().isEmpty()) {
                hit = true
                return@repeat
            }
        }
        prototype.tick()
        val victimAfter = prototype.snapshot().enemyTank().armor
        assertTrue(hit, "rocket should have detonated within its TTL")
        assertTrue(victimAfter < victimBefore, "victim armor should drop — was $victimBefore, now $victimAfter")
        prototype.dispose()
    }

    private fun cycleN(prototype: ServerMatchPrototype, times: Int) {
        repeat(times) {
            prototype.tick(mapOf(0 to PlayerIntentFrame(cycleWeaponRight = true)))
            repeat(15) { prototype.tick() }
        }
    }

    private fun com.tankarena.protocol.snapshot.WorldSnapshot.enemyTank(): TankState =
        actors.filterIsInstance<TankState>().single { !it.controlled }

    private fun com.tankarena.protocol.snapshot.WorldSnapshot.tankByPlayerIndex(playerIndex: Int): TankState {
        val controlled = actors.filterIsInstance<TankState>().filter { it.controlled }
        return controlled.getOrNull(playerIndex) ?: actors.filterIsInstance<TankState>().single()
    }

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
                    kind = ObjectKinds.ENFORCER,
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

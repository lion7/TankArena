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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T14 acceptance: shield absorbs damage before armor; invulnerability blocks damage
 * entirely while active and ticks down each tick; running out of fuel disables movement.
 */
class ServerTankDamageModelTest {

    @Test
    fun `shield absorbs damage before armor and depletes first`() {
        val prototype = ServerMatchPrototype.fromCanonicalMap(soloTankMap())
        prototype.initialize()
        val tank = prototype.playerTankForTest(0)!!
        tank.grantShield(20)
        val armorBefore = tank.armor

        prototype.queueDamageForTest(playerIndex = 0, amount = 30)
        prototype.tick()
        prototype.tick()

        assertEquals(0, tank.shield, "shield should absorb the first 20 of 30 damage")
        assertEquals(armorBefore - 10, tank.armor, "armor should take only the 10 that bled through")
        prototype.dispose()
    }

    @Test
    fun `invulnerability blocks all damage and ticks down each tick`() {
        val prototype = ServerMatchPrototype.fromCanonicalMap(soloTankMap())
        prototype.initialize()
        val tank = prototype.playerTankForTest(0)!!
        tank.grantInvulnerability(ticks = 10)
        val armorBefore = tank.armor

        prototype.queueDamageForTest(playerIndex = 0, amount = 50)
        prototype.tick()
        prototype.tick()

        assertEquals(armorBefore, tank.armor, "armor must not change while invulnerable")
        assertTrue(tank.invulnerableTicks in 0..9, "invuln should have ticked down — was ${tank.invulnerableTicks}")
        prototype.dispose()
    }

    @Test
    fun `invulnerability expiry restores damage taking`() {
        val prototype = ServerMatchPrototype.fromCanonicalMap(soloTankMap())
        prototype.initialize()
        val tank = prototype.playerTankForTest(0)!!
        tank.grantInvulnerability(ticks = 3)
        repeat(5) { prototype.tick() }
        val armorBefore = tank.armor

        prototype.queueDamageForTest(playerIndex = 0, amount = 25)
        prototype.tick()
        prototype.tick()

        assertEquals(armorBefore - 25, tank.armor, "post-expiry damage should land normally")
        prototype.dispose()
    }

    @Test
    fun `tank with zero fuel cannot accelerate forward`() {
        val prototype = ServerMatchPrototype.fromCanonicalMap(soloTankMap(direction = 4))
        prototype.initialize()
        val tank = prototype.playerTankForTest(0)!!
        tank.fuel = 0
        val xBefore = tank.positionX

        repeat(40) { prototype.tick(mapOf(0 to PlayerIntentFrame(forward = true))) }

        assertEquals(0f, tank.velocityX, 0.01f, "vx must stay zero with no fuel")
        assertEquals(xBefore, tank.positionX, "position must not advance with no fuel")
        prototype.dispose()
    }

    @Test
    fun `respawn refills armor and fuel and clears shield and invulnerability`() {
        val prototype = ServerMatchPrototype.fromCanonicalMap(soloTankMap(direction = 4, armor = 10, lives = 2))
        prototype.initialize()
        val tank = prototype.playerTankForTest(0)!!
        tank.grantShield(50)
        tank.grantInvulnerability(ticks = 0)

        // Punch a hole through invuln=0 to kill the tank.
        prototype.queueDamageForTest(playerIndex = 0, amount = 999)
        prototype.tick()
        prototype.tick()
        repeat(RESPAWN_DELAY_TICKS + 5) { prototype.tick() }

        assertTrue(tank.armor > 0, "tank should have respawned with armor — got ${tank.armor}")
        assertEquals(0, tank.shield, "shield should be cleared on respawn")
        assertEquals(0, tank.invulnerableTicks, "invulnerability should be cleared on respawn")
        prototype.dispose()
    }

    private fun soloTankMap(direction: Int = 4, armor: Int = 100, lives: Int = 3): CanonicalMapDefinition {
        val widthTiles = 16
        val heightTiles = 5
        val cellCount = widthTiles * heightTiles
        return CanonicalMapDefinition(
            metadata = MapMetadata(
                name = "damage-model-fixture",
                widthTiles = widthTiles,
                heightTiles = heightTiles,
                missionCode = "TEST",
            ),
            layers = TileLayers(
                base = List(cellCount) { -1 },
                solid = MutableList(cellCount) { -1 },
                top = List(cellCount) { -1 },
                goalLayer = List(cellCount) { 0 },
                bonusLayer = List(cellCount) { -1 },
                manTypeLayer = List(cellCount) { 0 },
                manAmountLayer = List(cellCount) { 0 },
            ),
            missionText = MissionText(),
            objects = listOf(
                AuthoredObject(
                    id = "p1",
                    kind = ObjectKinds.PLAYER_START,
                    x = 4 * LEGACY_TILE_SIZE,
                    y = 2 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    properties = mapOf(
                        "direction" to direction.toString(),
                        "armor" to armor.toString(),
                        "lives" to lives.toString(),
                    ),
                ),
            ),
        )
    }

    @Suppress("unused")
    private fun com.tankarena.protocol.snapshot.WorldSnapshot.tankByPlayerIndex(playerIndex: Int): TankState {
        val controlled = actors.filterIsInstance<TankState>().filter { it.controlled }
        return controlled.getOrNull(playerIndex) ?: actors.filterIsInstance<TankState>().single()
    }
}

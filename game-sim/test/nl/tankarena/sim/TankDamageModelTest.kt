package nl.tankarena.sim

import nl.tankarena.sim.legacy.AuthoredObject
import nl.tankarena.sim.legacy.CanonicalMapDefinition
import nl.tankarena.content.LEGACY_TILE_SIZE
import nl.tankarena.content.MapMetadata
import nl.tankarena.content.MissionText
import nl.tankarena.sim.legacy.ObjectKinds
import nl.tankarena.content.TileLayers
import nl.tankarena.input.PlayerIntentFrame
import nl.tankarena.protocol.snapshot.TankState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T14 acceptance: shield absorbs damage before armor; invulnerability blocks damage
 * entirely while active and ticks down each tick; running out of fuel disables movement.
 */
class TankDamageModelTest {

    @Test
    fun `holding the shield key blocks hits but not rockets while the pool lasts`() {
        val prototype = Match.fromCanonicalMap(soloTankMap())
        prototype.initialize()
        val tank = prototype.playerTankForTest(0)!!
        val armorBefore = tank.armor
        val shielded = mapOf(0 to PlayerIntentFrame(shield = true))

        // Legacy hit_tank(): `contr.shield && shield.power` stops everything except HT_ROCKET.
        prototype.stepForTest(shielded)
        prototype.queueDamageForTest(playerIndex = 0, amount = 20)
        tank.queueUnshieldableDamage(5)
        prototype.stepForTest(shielded)
        assertEquals(armorBefore - 5, tank.armor, "only the rocket should get through the shield")

        // Legacy shield_effects(): the pool drains 60 per tick that has passed while held and
        // recharges 10 per tick: 180 and 30 per step.
        val poolWhileHeld = tank.shield
        repeat(10) { prototype.stepForTest(shielded) }
        assertEquals(poolWhileHeld - 1800, tank.shield)
        repeat(10) { prototype.stepForTest() }
        assertEquals(poolWhileHeld - 1500, tank.shield)
        prototype.dispose()
    }

    @Test
    fun `an empty shield pool lets hits through`() {
        val prototype = Match.fromCanonicalMap(soloTankMap())
        prototype.initialize()
        val tank = prototype.playerTankForTest(0)!!
        val shielded = mapOf(0 to PlayerIntentFrame(shield = true))
        repeat(SHIELD_START_POWER / 180 + 2) { prototype.stepForTest(shielded) }
        assertEquals(0, tank.shield)
        val armorBefore = tank.armor

        prototype.queueDamageForTest(playerIndex = 0, amount = 7)
        prototype.stepForTest(shielded)
        assertEquals(armorBefore - 7, tank.armor)
        prototype.dispose()
    }

    @Test
    fun `invulnerability blocks all damage and counts down by steps`() {
        val prototype = Match.fromCanonicalMap(soloTankMap())
        prototype.initialize()
        val tank = prototype.playerTankForTest(0)!!
        tank.grantInvulnerability(holdTicks = 10)
        val before = tank.invulnerableTicks
        val armorBefore = tank.armor

        prototype.queueDamageForTest(playerIndex = 0, amount = 50)
        prototype.tick()
        prototype.tick()

        assertEquals(armorBefore, tank.armor, "armor must not change while invulnerable")
        assertEquals(before - STEP_TICKS, tank.invulnerableTicks, "one step has passed")
        prototype.dispose()
    }

    @Test
    fun `invulnerability ends after the hold and 15 blinks of 5 ticks, rounded to steps`() {
        val prototype = Match.fromCanonicalMap(soloTankMap())
        prototype.initialize()
        val tank = prototype.playerTankForTest(0)!!
        tank.grantInvulnerability(holdTicks = 3)
        // The hold runs out on the first step (blink 15 → 14); each further blink is a tinit(5),
        // which lasts two steps: the last one ends on step 28.
        repeat(28) { prototype.stepForTest() }
        assertTrue(tank.invulnerableTicks > 0, "still blinking")
        prototype.stepForTest()
        assertEquals(0, tank.invulnerableTicks)
        val armorBefore = tank.armor

        prototype.queueDamageForTest(playerIndex = 0, amount = 25)
        prototype.tick()
        prototype.tick()

        assertEquals(armorBefore - 25, tank.armor, "post-expiry damage should land normally")
        prototype.dispose()
    }

    @Test
    fun `tank with zero fuel cannot accelerate forward`() {
        val prototype = Match.fromCanonicalMap(soloTankMap(direction = 4))
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
    fun `respawn refills armor and the shield pool and starts a new start shield`() {
        val prototype = Match.fromCanonicalMap(soloTankMap(direction = 4, armor = 10, lives = 2))
        prototype.initialize()
        val tank = prototype.playerTankForTest(0)!!
        tank.grantShield(50)
        tank.grantInvulnerability(holdTicks = 0)

        // Punch a hole through invuln=0 to kill the tank.
        prototype.queueDamageForTest(playerIndex = 0, amount = 999)
        prototype.tick()
        prototype.tick()
        repeat(RESPAWN_DELAY_TICKS + 5) { prototype.tick() }

        assertTrue(tank.armor > 0, "tank should have respawned with armor — got ${tank.armor}")
        // Legacy make_tank(): `shield.power = 3000` and a blinking start shield for human tanks.
        assertTrue(tank.shield in SHIELD_START_POWER..SHIELD_START_POWER + 100, "a new tank starts with a 3000 shield pool, recharging since")
        assertTrue(tank.invulnerableTicks in 1..START_SHIELD_TICKS, "a new tank starts with the start shield")
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
    private fun nl.tankarena.protocol.snapshot.WorldSnapshot.tankByPlayerIndex(playerIndex: Int): TankState {
        val controlled = actors.filterIsInstance<TankState>().filter { it.controlled }
        return controlled.getOrNull(playerIndex) ?: actors.filterIsInstance<TankState>().single()
    }
}

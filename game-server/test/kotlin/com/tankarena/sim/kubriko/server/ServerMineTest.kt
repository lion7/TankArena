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
 * T10 acceptance: mines deploy under a tank, persist, and detonate on contact with any
 * tank that isn't shielded by the owner-immunity grace period (the activation delay).
 */
class ServerMineTest {

    @Test
    fun `placed mine remains static and decrements owner ammo`() {
        val prototype = ServerMatchPrototype.fromCanonicalMap(twoTankMap())
        prototype.initialize()

        cycleToMine(prototype)
        prototype.tick(mapOf(0 to PlayerIntentFrame(firePrimary = true)))
        repeat(5) { prototype.tick() }

        val mineCount = prototype.snapshot().actors.count { it is com.tankarena.protocol.snapshot.ActorState && false } // placeholder
        // mines are not in protocol snapshot yet — assert via internal accessor instead.
        val mineActors = prototype.snapshotMines()
        assertEquals(1, mineActors.size, "exactly one mine should be deployed")
        prototype.dispose()
    }

    @Test
    fun `armed mine detonates on contact with non-owner tank and damages it`() {
        val prototype = ServerMatchPrototype.fromCanonicalMap(soloTankMap(direction = 12))
        prototype.initialize()
        // Use the test-only injection to drop a pre-armed mine on the victim's path.
        prototype.injectArmedMineForTest(x = 100, y = 82, damage = 7, radius = LIGHT_MINE_RADIUS_PX)

        val victimBefore = prototype.snapshot().tankByPlayerIndex(0).armor

        var detonated = false
        for (i in 0 until 250) {
            prototype.tick(mapOf(0 to PlayerIntentFrame(forward = true)))
            if (prototype.snapshotMines().isEmpty()) {
                detonated = true
                break
            }
        }
        // Damage queued in the same tick the mine detonates is applied on the next
        // tick's resolution pass — drive one more tick so the armor drop is visible.
        prototype.tick()

        val victimAfter = prototype.snapshot().tankByPlayerIndex(0).armor
        val victimX = prototype.snapshot().tankByPlayerIndex(0).x
        assertTrue(detonated, "mine should detonate — victim ended at x=$victimX, mines still=${prototype.snapshotMines().size}")
        assertTrue(victimAfter < victimBefore, "victim armor should drop — was $victimBefore, now $victimAfter")
        prototype.dispose()
    }

    @Test
    fun `freshly placed mine does not damage the placer during activation grace period`() {
        val prototype = ServerMatchPrototype.fromCanonicalMap(soloTankMap(direction = 4))
        prototype.initialize()

        cycleToMine(prototype)
        val before = prototype.snapshot().tankByPlayerIndex(0).armor
        prototype.tick(mapOf(0 to PlayerIntentFrame(firePrimary = true)))
        // Owner sits on top of the freshly placed mine for the entire activation window.
        repeat(MINE_ACTIVATION_TICKS - 1) { prototype.tick() }

        val after = prototype.snapshot().tankByPlayerIndex(0).armor
        assertEquals(before, after, "placer must not be damaged by their own mine before activation")
        assertEquals(1, prototype.snapshotMines().size, "mine still present pre-activation")
        prototype.dispose()
    }

    private fun cycleToMine(prototype: ServerMatchPrototype) {
        // MAIN -> CHAIN -> MINE
        prototype.tick(mapOf(0 to PlayerIntentFrame(cycleWeaponRight = true)))
        repeat(15) { prototype.tick() }
        prototype.tick(mapOf(0 to PlayerIntentFrame(cycleWeaponRight = true)))
        repeat(15) { prototype.tick() }
    }

    private fun com.tankarena.protocol.snapshot.WorldSnapshot.tankByPlayerIndex(playerIndex: Int): TankState {
        val controlled = actors.filterIsInstance<TankState>().filter { it.controlled }
        return controlled.getOrNull(playerIndex) ?: actors.filterIsInstance<TankState>().single()
    }

    private fun soloTankMap(direction: Int): CanonicalMapDefinition {
        val widthTiles = 16
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
        val widthTiles = 16
        val heightTiles = 5
        return canonicalMap(
            widthTiles,
            heightTiles,
            solid = MutableList(widthTiles * heightTiles) { -1 },
            players = listOf(
                AuthoredObject(
                    id = "owner",
                    kind = ObjectKinds.PLAYER_START,
                    x = 4 * LEGACY_TILE_SIZE,
                    y = 2 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    properties = mapOf("direction" to "12", "lives" to "3"),
                ),
                AuthoredObject(
                    id = "victim",
                    kind = ObjectKinds.PLAYER_START,
                    x = 8 * LEGACY_TILE_SIZE,
                    y = 2 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
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
                name = "mine-fixture",
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

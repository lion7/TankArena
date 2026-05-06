package com.tankarena.sim.kubriko.server

import com.tankarena.sim.kubriko.server.legacy.AuthoredObject
import com.tankarena.sim.kubriko.server.legacy.CanonicalMapDefinition
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.MapMetadata
import com.tankarena.content.MissionText
import com.tankarena.sim.kubriko.server.legacy.ObjectKinds
import com.tankarena.content.TileLayers
import com.tankarena.input.PlayerIntentFrame
import com.tankarena.protocol.snapshot.GameEvent
import com.tankarena.protocol.snapshot.TankState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ServerTankLifecycleTest {

    @Test
    fun `fatal damage emits TankDestroyed and tank respawns after delay with full armor`() {
        val prototype = ServerMatchPrototype.fromCanonicalMap(
            twoTanksDuel(
                shooterArmor = 100,
                victimArmor = 25, // one hit kill
                victimLives = 2,
            ),
        )
        prototype.initialize()

        val victimBefore = prototype.snapshot().controlledTank(playerIndex = 1)
        val sawDestroyed = AnyEvent()
        val sawSpawned = AnyEvent()

        prototype.tick(mapOf(0 to PlayerIntentFrame(firePrimary = true)))
        var ticks = 0
        while (ticks < 400 && !(sawDestroyed.seen && sawSpawned.seen)) {
            val frame = prototype.tick()
            for (event in frame.events) {
                if (event is GameEvent.TankDestroyed && event.actorId == victimBefore.actorId) {
                    sawDestroyed.seen = true
                }
                if (event is GameEvent.TankSpawned && event.actorId == victimBefore.actorId) {
                    sawSpawned.seen = true
                }
            }
            ticks += 1
        }

        assertTrue(sawDestroyed.seen, "TankDestroyed should have fired for the victim")
        assertTrue(sawSpawned.seen, "TankSpawned should have fired after respawn delay")
        val victimAfter = prototype.snapshot().controlledTank(playerIndex = 1)
        assertEquals(victimBefore.armor, victimAfter.armor, "respawn should refill to original armor")
        prototype.dispose()
    }

    @Test
    fun `non-lethal damage emits DamageTaken without TankDestroyed`() {
        val prototype = ServerMatchPrototype.fromCanonicalMap(
            twoTanksDuel(shooterArmor = 100, victimArmor = 100, victimLives = 1),
        )
        prototype.initialize()
        val victimId = prototype.snapshot().controlledTank(playerIndex = 1).actorId

        prototype.tick(mapOf(0 to PlayerIntentFrame(firePrimary = true)))
        var damageAmount = 0
        var destroyed = false
        repeat(30) {
            val frame = prototype.tick()
            for (event in frame.events) {
                if (event is GameEvent.DamageTaken && event.actorId == victimId) {
                    damageAmount += event.amount
                }
                if (event is GameEvent.TankDestroyed && event.actorId == victimId) {
                    destroyed = true
                }
            }
        }

        assertTrue(damageAmount > 0, "expected a DamageTaken event from the hit")
        assertTrue(!destroyed, "100 armor victim must not be destroyed by a 25 damage hit")
        prototype.dispose()
    }

    @Test
    fun `destroying a single-life enemy tank with no other enemies wins the mission`() {
        val prototype = ServerMatchPrototype.fromCanonicalMap(
            playerVsEnemy(shooterArmor = 100, enemyArmor = 25, enemyLives = 1),
        )
        prototype.initialize()

        var sawWin = false
        prototype.tick(mapOf(0 to PlayerIntentFrame(firePrimary = true)))
        repeat(40) {
            val frame = prototype.tick()
            if (frame.events.any { it is GameEvent.MissionWon }) sawWin = true
        }

        assertTrue(sawWin, "MissionWon should fire when all enemies are dead")
        assertEquals(ServerMatchPrototype.MissionStatus.WON, prototype.mission)
        prototype.dispose()
    }

    private fun playerVsEnemy(shooterArmor: Int, enemyArmor: Int, enemyLives: Int): CanonicalMapDefinition {
        val widthTiles = 10
        val heightTiles = 3
        val cellCount = widthTiles * heightTiles
        return CanonicalMapDefinition(
            metadata = MapMetadata(
                name = "lifecycle-enemy-fixture",
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
                    id = "player",
                    kind = ObjectKinds.PLAYER_START,
                    x = 2 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    y = 1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    properties = mapOf(
                        "direction" to "4",
                        "armor" to shooterArmor.toString(),
                        "lives" to "3",
                    ),
                ),
                AuthoredObject(
                    id = "enemy",
                    kind = ObjectKinds.ENFORCER,
                    x = 6 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    y = 1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    properties = mapOf(
                        "direction" to "12",
                        "armor" to enemyArmor.toString(),
                        "lives" to enemyLives.toString(),
                    ),
                ),
            ),
        )
    }

    private class AnyEvent { var seen: Boolean = false }

    private fun com.tankarena.protocol.snapshot.WorldSnapshot.controlledTank(playerIndex: Int): TankState {
        val controlled = actors.filterIsInstance<TankState>().filter { it.controlled }
        return controlled[playerIndex]
    }

    // shooter (player 0, team 0) fires east at victim (team 1, non-controlled enemy).
    // shooter is the only player, victim is the only enemy.
    private fun twoTanksDuel(shooterArmor: Int, victimArmor: Int, victimLives: Int): CanonicalMapDefinition {
        val widthTiles = 10
        val heightTiles = 3
        val cellCount = widthTiles * heightTiles
        return CanonicalMapDefinition(
            metadata = MapMetadata(
                name = "lifecycle-fixture",
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
                    id = "shooter",
                    kind = ObjectKinds.PLAYER_START,
                    x = 2 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    y = 1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    properties = mapOf(
                        "direction" to "4",
                        "armor" to shooterArmor.toString(),
                        "lives" to "3",
                    ),
                ),
                AuthoredObject(
                    id = "victim",
                    kind = ObjectKinds.PLAYER_START,
                    x = 6 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    y = 1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    properties = mapOf(
                        "direction" to "12",
                        "armor" to victimArmor.toString(),
                        "lives" to victimLives.toString(),
                    ),
                ),
            ),
        )
    }
}

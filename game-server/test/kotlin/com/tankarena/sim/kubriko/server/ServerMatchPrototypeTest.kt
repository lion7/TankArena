package com.tankarena.sim.kubriko.server

import com.tankarena.sim.kubriko.server.legacy.AuthoredObject
import com.tankarena.sim.kubriko.server.legacy.CanonicalMapDefinition
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.MapMetadata
import com.tankarena.content.MissionText
import com.tankarena.sim.kubriko.server.legacy.ObjectKinds
import com.tankarena.content.TileLayers
import com.tankarena.protocol.Team
import com.tankarena.protocol.snapshot.GoalState
import com.tankarena.protocol.snapshot.TankState
import com.tankarena.protocol.snapshot.TurretState
import com.tankarena.protocol.snapshot.WallState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ServerMatchPrototypeTest {

    @Test
    fun `bootstraps a canonical map into the expected mix of authoritative actors`() {
        val map = begin1Like()

        val prototype = ServerMatchPrototype.fromCanonicalMap(map)
        prototype.initialize()
        val snapshot = prototype.snapshot()

        val walls = snapshot.actors.filterIsInstance<WallState>()
        val tanks = snapshot.actors.filterIsInstance<TankState>()
        val turrets = snapshot.actors.filterIsInstance<TurretState>()
        val goals = snapshot.actors.filterIsInstance<GoalState>()

        val solidTileCount = map.layers.solid.count { it >= 0 }
        assertEquals(solidTileCount + 4, walls.size, "walls should cover every solid tile plus four boundary walls")
        assertEquals(2, tanks.size)
        assertEquals(1, turrets.size)
        assertEquals(1, goals.size)

        val playerTank = tanks.single { it.controlled }
        assertEquals(Team.PLAYER, playerTank.team)
        val enemyTank = tanks.single { !it.controlled }
        assertEquals(Team.ENEMY, enemyTank.team)

        val allIds = snapshot.actors.map { it.actorId }
        assertEquals(allIds.size, allIds.toSet().size, "actorIds must be unique")
        assertTrue(allIds.all { it > 0 })

        prototype.dispose()
    }

    @Test
    fun `ticks 300 times without crashing and preserves actor identities`() {
        val map = begin1Like()
        val prototype = ServerMatchPrototype.fromCanonicalMap(map)
        prototype.initialize()
        val baselineIds = prototype.snapshot().actors.map { it.actorId }.toSet()

        var last = prototype.snapshot()
        repeat(300) { last = prototype.tick() }

        assertEquals(300L, last.tick)
        val postIds = last.actors.map { it.actorId }.toSet()
        assertEquals(baselineIds, postIds, "actorIds must be stable across ticks")
        assertTrue(last.actors.all { it.hasFinitePosition() }, "no actor should report NaN/infinite positions")

        prototype.dispose()
    }

    @Test
    fun `dispose leaves tickSource inert in accordance with the fork's guard`() {
        val map = begin1Like()
        val prototype = ServerMatchPrototype.fromCanonicalMap(map)
        prototype.initialize()
        prototype.tick()
        prototype.dispose()
        prototype.tick()
        val snapshot = prototype.snapshot()
        assertNotNull(snapshot)
    }

    private fun begin1Like(): CanonicalMapDefinition {
        val widthTiles = 12
        val heightTiles = 10
        val cellCount = widthTiles * heightTiles
        val solid = MutableList(cellCount) { idx ->
            val x = idx % widthTiles
            val y = idx / widthTiles
            val onEdge = x == 0 || y == 0 || x == widthTiles - 1 || y == heightTiles - 1
            val interiorBlock = x in 4..5 && y in 4..5
            if (onEdge || interiorBlock) 0 else -1
        }
        return CanonicalMapDefinition(
            metadata = MapMetadata(
                name = "begin1-like",
                widthTiles = widthTiles,
                heightTiles = heightTiles,
                missionCode = "TESTBEGIN",
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
            objects = listOf(
                AuthoredObject(
                    id = "p1",
                    kind = ObjectKinds.PLAYER_START,
                    x = 2 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    y = 8 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    properties = mapOf("direction" to "0", "lives" to "3"),
                ),
                AuthoredObject(
                    id = "e1",
                    kind = ObjectKinds.ENFORCER,
                    x = 9 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    y = 2 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    properties = mapOf("direction" to "8"),
                ),
                AuthoredObject(
                    id = "t1",
                    kind = ObjectKinds.TURRET,
                    x = 9 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    y = 8 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    properties = mapOf("direction" to "12"),
                ),
                AuthoredObject(
                    id = "g1",
                    kind = ObjectKinds.GOAL,
                    x = 6 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    y = 1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                ),
            ),
        )
    }

    private fun com.tankarena.protocol.snapshot.ActorState.hasFinitePosition(): Boolean {
        return when (this) {
            is WallState -> x.isFinite() && y.isFinite()
            is TankState -> x.isFinite() && y.isFinite()
            is TurretState -> x.isFinite() && y.isFinite()
            is GoalState -> x.isFinite() && y.isFinite()
            is com.tankarena.protocol.snapshot.ProjectileState -> x.isFinite() && y.isFinite()
        }
    }

    private fun Int.isFinite(): Boolean = this > Int.MIN_VALUE && this < Int.MAX_VALUE
}

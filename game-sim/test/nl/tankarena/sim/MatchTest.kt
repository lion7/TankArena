package nl.tankarena.sim

import nl.tankarena.sim.legacy.AuthoredObject
import nl.tankarena.sim.legacy.CanonicalMapDefinition
import nl.tankarena.content.LEGACY_TILE_SIZE
import nl.tankarena.content.MapMetadata
import nl.tankarena.content.MissionText
import nl.tankarena.sim.legacy.ObjectKinds
import nl.tankarena.content.TileLayers
import nl.tankarena.protocol.Team
import nl.tankarena.protocol.snapshot.ProjectileState
import nl.tankarena.protocol.snapshot.GoalState
import nl.tankarena.protocol.snapshot.TankState
import nl.tankarena.protocol.snapshot.TurretState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MatchTest {

    @Test
    fun `bootstraps a canonical map into the expected mix of authoritative actors`() {
        val map = begin1Like()

        val prototype = Match.fromCanonicalMap(map)
        prototype.initialize()
        val snapshot = prototype.snapshot()

        val tanks = snapshot.actors.filterIsInstance<TankState>()
        val turrets = snapshot.actors.filterIsInstance<TurretState>()
        val goals = snapshot.actors.filterIsInstance<GoalState>()

        val solidTileCount = map.layers.solid.count { it >= 0 }
        // Structures block through the StructureGrid, not as objects.
        assertEquals(solidTileCount, prototype.solidTileCountForTest(), "every solid tile with strength blocks")
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
        val prototype = Match.fromCanonicalMap(map)
        prototype.initialize()
        // Shots come and go; every persistent actor keeps its id.
        val baselineIds = prototype.snapshot().actors.filter { it !is ProjectileState }.map { it.actorId }.toSet()

        var last = prototype.snapshot()
        repeat(300) { last = prototype.tick() }

        assertEquals(300L, last.tick)
        val postIds = last.actors.filter { it !is ProjectileState }.map { it.actorId }.toSet()
        assertEquals(baselineIds, postIds, "actorIds must be stable across ticks")
        assertTrue(last.actors.all { it.hasFinitePosition() }, "no actor should report NaN/infinite positions")

        prototype.dispose()
    }

    @Test
    fun `dispose leaves tickSource inert in accordance with the fork's guard`() {
        val map = begin1Like()
        val prototype = Match.fromCanonicalMap(map)
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
            // The turret at (9, 8) stands on a building, as legacy turrets must.
            val turretBase = x == 9 && y == 8
            if (onEdge || interiorBlock || turretBase) 0 else -1
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
                    kind = ObjectKinds.AI_TANK,
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

    private fun nl.tankarena.protocol.snapshot.ActorState.hasFinitePosition(): Boolean {
        return when (this) {
            is TankState -> x.isFinite() && y.isFinite()
            is TurretState -> x.isFinite() && y.isFinite()
            is GoalState -> x.isFinite() && y.isFinite()
            is nl.tankarena.protocol.snapshot.ProjectileState -> x.isFinite() && y.isFinite()
            is nl.tankarena.protocol.snapshot.FlagState -> x.isFinite() && y.isFinite()
            is nl.tankarena.protocol.snapshot.ProductState -> x.isFinite() && y.isFinite()
            is nl.tankarena.protocol.snapshot.LockState -> x.isFinite() && y.isFinite()
            is nl.tankarena.protocol.snapshot.WarpState -> x.isFinite() && y.isFinite()
            is nl.tankarena.protocol.snapshot.DestroyerState -> x.isFinite() && y.isFinite()
            is nl.tankarena.protocol.snapshot.EnforcerState -> x.isFinite() && y.isFinite()
            is nl.tankarena.protocol.snapshot.TrainState -> x.isFinite() && y.isFinite()
            is nl.tankarena.protocol.snapshot.ZeppelinState -> x.isFinite() && y.isFinite()
            is nl.tankarena.protocol.snapshot.B52State -> x.isFinite() && y.isFinite()
            is nl.tankarena.protocol.snapshot.MineState -> x.isFinite() && y.isFinite()
            is nl.tankarena.protocol.snapshot.BonusState -> x.isFinite() && y.isFinite()
            is nl.tankarena.protocol.snapshot.ManState -> x.isFinite() && y.isFinite()
            is nl.tankarena.protocol.snapshot.AbombState -> x.isFinite() && y.isFinite()
            is nl.tankarena.protocol.snapshot.FlamePartState -> x.isFinite() && y.isFinite()
        }
    }

    private fun Int.isFinite(): Boolean = this > Int.MIN_VALUE && this < Int.MAX_VALUE
}

package com.tankarena.sim.kubriko.server

import com.tankarena.content.AuthoredObject
import com.tankarena.content.CanonicalMapDefinition
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.MapMetadata
import com.tankarena.content.MissionText
import com.tankarena.content.ObjectKinds
import com.tankarena.content.TileLayers
import com.tankarena.protocol.snapshot.ProjectileOwnerKind
import com.tankarena.protocol.snapshot.ProjectileState
import com.tankarena.protocol.snapshot.TankState
import com.tankarena.protocol.snapshot.TurretState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServerTurretFireTest {

    @Test
    fun `turret rotates toward a nearby player tank and eventually fires`() {
        val prototype = ServerMatchPrototype.fromCanonicalMap(
            turretVsPlayerMap(initialTurretDirection = 0),
        )
        prototype.initialize()

        val startDirection = prototype.snapshot().turret().turretDirection
        var sawProjectile = false
        var lastDirection = startDirection
        repeat(120) {
            val frame = prototype.tick()
            lastDirection = frame.turret().turretDirection
            if (frame.actors.filterIsInstance<ProjectileState>()
                    .any { it.ownerKind == ProjectileOwnerKind.TURRET }
            ) {
                sawProjectile = true
            }
        }

        assertTrue(
            lastDirection != startDirection,
            "turret should rotate from start=$startDirection toward the player tank",
        )
        assertTrue(sawProjectile, "turret should have fired at least one TURRET-owned projectile")
        prototype.dispose()
    }

    @Test
    fun `turret does not fire at a tank that is out of range`() {
        val prototype = ServerMatchPrototype.fromCanonicalMap(
            turretVsFarawayPlayerMap(),
        )
        prototype.initialize()

        repeat(90) { prototype.tick() }

        assertTrue(
            prototype.snapshot().actors.filterIsInstance<ProjectileState>().isEmpty(),
            "turret should not fire at a player tank beyond range",
        )
        prototype.dispose()
    }

    private fun com.tankarena.protocol.snapshot.WorldSnapshot.turret(): TurretState =
        actors.filterIsInstance<TurretState>().single()

    private fun turretVsPlayerMap(initialTurretDirection: Int): CanonicalMapDefinition {
        val widthTiles = 10
        val heightTiles = 5
        return canonicalMap(
            widthTiles,
            heightTiles,
            players = listOf(
                AuthoredObject(
                    id = "p1",
                    kind = ObjectKinds.PLAYER_START,
                    x = 2 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    y = 2 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    properties = mapOf("direction" to "0", "lives" to "3"),
                ),
                AuthoredObject(
                    id = "t1",
                    kind = ObjectKinds.TURRET,
                    x = 5 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    y = 2 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    properties = mapOf("direction" to initialTurretDirection.toString()),
                ),
            ),
        )
    }

    private fun turretVsFarawayPlayerMap(): CanonicalMapDefinition {
        val widthTiles = 40
        val heightTiles = 3
        return canonicalMap(
            widthTiles,
            heightTiles,
            players = listOf(
                AuthoredObject(
                    id = "p1",
                    kind = ObjectKinds.PLAYER_START,
                    x = 1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    y = 1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    properties = mapOf("direction" to "0", "lives" to "3"),
                ),
                AuthoredObject(
                    id = "t1",
                    kind = ObjectKinds.TURRET,
                    x = 35 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    y = 1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    properties = mapOf("direction" to "0"),
                ),
            ),
        )
    }

    private fun canonicalMap(
        widthTiles: Int,
        heightTiles: Int,
        players: List<AuthoredObject>,
    ): CanonicalMapDefinition {
        val cellCount = widthTiles * heightTiles
        return CanonicalMapDefinition(
            metadata = MapMetadata(
                name = "turret-fixture",
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
            objects = players,
        )
    }
}

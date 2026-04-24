package com.tankarena.sim.kubriko.server

import com.tankarena.content.AuthoredObject
import com.tankarena.content.CanonicalMapDefinition
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.MapMetadata
import com.tankarena.content.MissionText
import com.tankarena.content.ObjectKinds
import com.tankarena.content.TileLayers
import com.tankarena.protocol.Team
import com.tankarena.protocol.snapshot.ProjectileOwnerKind
import com.tankarena.protocol.snapshot.ProjectileState
import com.tankarena.protocol.snapshot.TankState
import kotlin.test.Test
import kotlin.test.assertTrue

class ServerAiTankTest {

    @Test
    fun `enemy tank moves toward a nearby player tank`() {
        val prototype = ServerMatchPrototype.fromCanonicalMap(
            playerWithEnemyAtDistanceTiles(enemyTileX = 12),
        )
        prototype.initialize()

        val startX = prototype.snapshot().enemy().x
        repeat(60) { prototype.tick() }
        val endX = prototype.snapshot().enemy().x

        assertTrue(
            endX < startX,
            "enemy tank to the east of a player should close the distance — startX=$startX, endX=$endX",
        )
        prototype.dispose()
    }

    @Test
    fun `enemy tank fires at a player in range`() {
        val prototype = ServerMatchPrototype.fromCanonicalMap(
            playerWithEnemyAtDistanceTiles(enemyTileX = 6),
        )
        prototype.initialize()

        var sawEnemyShot = false
        repeat(120) {
            val frame = prototype.tick()
            if (frame.actors.filterIsInstance<ProjectileState>()
                    .any { it.ownerKind == ProjectileOwnerKind.TANK && it.ownerId == prototype.snapshot().enemy().actorId }
            ) {
                sawEnemyShot = true
            }
        }
        assertTrue(sawEnemyShot, "AI enemy should have fired at least one projectile at the player")
        prototype.dispose()
    }

    private fun com.tankarena.protocol.snapshot.WorldSnapshot.enemy(): TankState =
        actors.filterIsInstance<TankState>().single { it.team == Team.ENEMY }

    private fun playerWithEnemyAtDistanceTiles(enemyTileX: Int): CanonicalMapDefinition {
        val widthTiles = 20
        val heightTiles = 3
        val cellCount = widthTiles * heightTiles
        return CanonicalMapDefinition(
            metadata = MapMetadata(
                name = "ai-fixture",
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
                    properties = mapOf("direction" to "4", "lives" to "3", "armor" to "200"),
                ),
                AuthoredObject(
                    id = "enemy",
                    kind = ObjectKinds.ENFORCER,
                    x = enemyTileX * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    y = 1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    properties = mapOf("direction" to "12", "lives" to "3", "armor" to "200"),
                ),
            ),
        )
    }
}

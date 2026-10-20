package nl.tankarena.sim

import nl.tankarena.sim.legacy.AuthoredObject
import nl.tankarena.sim.legacy.CanonicalMapDefinition
import nl.tankarena.content.LEGACY_TILE_SIZE
import nl.tankarena.content.MapMetadata
import nl.tankarena.content.MissionText
import nl.tankarena.sim.legacy.ObjectKinds
import nl.tankarena.content.TileLayers
import nl.tankarena.protocol.snapshot.ProjectileKind
import nl.tankarena.protocol.snapshot.ProjectileOwnerKind
import nl.tankarena.protocol.snapshot.ProjectileState
import nl.tankarena.protocol.snapshot.TankState
import nl.tankarena.protocol.snapshot.TurretState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TurretFireTest {

    @Test
    fun `turret rotates toward a nearby player tank and eventually fires`() {
        val prototype = Match.fromCanonicalMap(
            turretVsPlayerMap(initialTurretDirection = 0),
        )
        prototype.initialize()

        val startDirection = prototype.snapshot().turret().turretDirection
        var sawProjectile = false
        var lastDirection = startDirection
        repeat(600) {
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
        val prototype = Match.fromCanonicalMap(
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

    @Test
    fun `a rocket turret launches rockets at the player`() {
        val prototype = Match.fromCanonicalMap(turretMap(turret = mapOf("turretType" to "1", "direction" to "12")))
        prototype.initialize()
        var kinds = emptySet<ProjectileKind>()
        repeat(400) {
            kinds = kinds + prototype.tick().actors.filterIsInstance<ProjectileState>().map { it.kind }
        }
        assertEquals(setOf(ProjectileKind.ROCKET), kinds, "type 1 (legacy ti[1], HT_ROCKET) fires only rockets")
        assertEquals(1, prototype.snapshot().turret().turretType)
        prototype.dispose()
    }

    @Test
    fun `a bullet leaves from the end of the barrel`() {
        // Legacy ti[0]: the gun is 15 px north of the centre; aimed west it fires from 15 px west.
        val prototype = Match.fromCanonicalMap(turretMap(turret = mapOf("direction" to "12")))
        prototype.initialize()
        val turret = prototype.snapshot().turret()
        var first: ProjectileState? = null
        repeat(200) {
            if (first == null) first = prototype.tick().actors.filterIsInstance<ProjectileState>().firstOrNull()
        }
        val shot = first ?: error("the turret never fired")
        assertEquals(turret.y, shot.y)
        assertTrue(shot.x in turret.x - 17..turret.x - 13, "fired from x=${shot.x}, turret at ${turret.x}")
        prototype.dispose()
    }

    @Test
    fun `the gun turns to any of the 16 directions`() {
        // The player sits two tiles left and one tile up: radtoa16 gives west-north-west (13).
        val prototype = Match.fromCanonicalMap(turretMap(turret = mapOf("direction" to "12"), turretTileY = 3))
        prototype.initialize()
        repeat(100) { prototype.tick() }
        assertEquals(13, prototype.snapshot().turret().turretDirection)
        prototype.dispose()
    }

    @Test
    fun `a turret set to shoot evil vehicles leaves the player alone`() {
        val prototype = Match.fromCanonicalMap(turretMap(turret = mapOf("direction" to "12", "shootAt" to "2")))
        prototype.initialize()
        repeat(400) { prototype.tick() }
        assertTrue(prototype.snapshot().actors.filterIsInstance<ProjectileState>().isEmpty())
        assertEquals(12, prototype.snapshot().turret().turretDirection, "it does not even turn")
        prototype.dispose()
    }

    @Test
    fun `gun offsets turn clockwise like legacy loc_x and loc_y`() {
        assertEquals(0 to -6, rotateLegacyOffset(0, 0, -6))
        assertEquals(6 to 0, rotateLegacyOffset(4, 0, -6))
        assertEquals(0 to 6, rotateLegacyOffset(8, 0, -6))
        assertEquals(-6 to 0, rotateLegacyOffset(12, 0, -6))
        assertEquals(6 to -2, rotateLegacyOffset(4, -2, -6))
    }

    /** The player two tiles west of a turret on a 10×6 map, both on row 2 unless [turretTileY] says otherwise. */
    private fun turretMap(turret: Map<String, String>, turretTileY: Int = 2): CanonicalMapDefinition =
        canonicalMap(
            10,
            6,
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
                    x = 4 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    y = turretTileY * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    properties = turret,
                ),
            ),
        )

    private fun nl.tankarena.protocol.snapshot.WorldSnapshot.turret(): TurretState =
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
        // Legacy turrets stand on a building (desert picture 1, BASE1A) or fall at once.
        val solid = MutableList(cellCount) { -1 }
        for (turret in players.filter { it.kind == ObjectKinds.TURRET }) {
            solid[turret.x / LEGACY_TILE_SIZE + (turret.y / LEGACY_TILE_SIZE) * widthTiles] = 1
        }
        return CanonicalMapDefinition(
            metadata = MapMetadata(
                name = "turret-fixture",
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

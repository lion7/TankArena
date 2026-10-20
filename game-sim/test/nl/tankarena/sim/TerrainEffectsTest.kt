package nl.tankarena.sim

import nl.tankarena.content.LEGACY_TILE_SIZE
import nl.tankarena.content.MapMetadata
import nl.tankarena.content.MissionText
import nl.tankarena.content.PictureCatalog
import nl.tankarena.content.TankArenaWorld
import nl.tankarena.content.TileLayers
import nl.tankarena.protocol.snapshot.EffectKind
import nl.tankarena.protocol.snapshot.GameEvent
import nl.tankarena.protocol.snapshot.SoundKind
import nl.tankarena.sim.legacy.AuthoredObject
import nl.tankarena.sim.legacy.CanonicalMapDefinition
import nl.tankarena.sim.legacy.ObjectKinds
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** transportation() and underground_effects() (src/world/transp.c, src/tanks/control.c). */
class TerrainEffectsTest {
    private val t = LEGACY_TILE_SIZE

    @Test
    fun `a conveyor carries a standing vehicle 60 px a second`() {
        val (world, picture) = pictureOfType("T_RIGHT")
        val match = start(world, base = mapOf(4 to picture))
        val tank = match.playerTankForTest(0)!!
        tank.teleportTo(4 * t + 10, 2 * t + 16)
        val x = tank.exactPosition.first
        repeat(2) { match.stepForTest() }
        assertEquals(x + 2 * 60 * STEP_SECONDS, tank.exactPosition.first, 0.01f)
        assertEquals(4, tank.bodyDirection, "the heading does not change")
    }

    @Test
    fun `a turntable turns a vehicle around the tile, a full circle a second`() {
        val (world, picture) = pictureOfType("ROTATE_L")
        val match = start(world, base = mapOf(4 to picture))
        val tank = match.playerTankForTest(0)!!
        val pivotX = 4 * t + 16f
        val pivotY = 2 * t + 16f
        tank.teleportTo(pivotX.toInt() + 8, pivotY.toInt())
        match.stepForTest()
        val a = 2 * Math.PI * STEP_SECONDS
        val (x, y) = tank.exactPosition
        assertTrue(abs(x - (pivotX + 8 * cos(a).toFloat())) < 0.01f && abs(y - (pivotY - 8 * sin(a).toFloat())) < 0.01f, "at ($x, $y)")
    }

    @Test
    fun `driving into water splashes five times with a splash sound`() {
        val (world, water) = pictureOfType("WATER")
        val match = start(world, base = (5..8).associateWith { water })
        val tank = match.playerTankForTest(0)!!
        var splashes = 0
        var sounds = 0
        repeat(80) {
            if (sounds > 0) return@repeat
            val events = match.stepForTest(mapOf(0 to nl.tankarena.input.PlayerIntentFrame(forward = true))).events
            splashes += events.count { it is GameEvent.Effect && it.kind == EffectKind.SPLASH }
            sounds += events.count { it is GameEvent.Sound && it.kind == SoundKind.SPLASH }
        }
        assertEquals(1, sounds)
        assertTrue(splashes >= 5)
    }

    private fun pictureOfType(type: String): Pair<TankArenaWorld, Int> = TankArenaWorld.entries.firstNotNullOf { world ->
        (0 until PictureCatalog.pictureCount(world)).firstOrNull { PictureCatalog.legacyRow(world, it)?.type == type }?.let { world to it }
    }

    private fun start(world: TankArenaWorld, base: Map<Int, Int>): Match {
        val width = 12
        val height = 5
        val cells = width * height
        val match = Match.fromCanonicalMap(
            CanonicalMapDefinition(
                metadata = MapMetadata(name = "terrain", widthTiles = width, heightTiles = height, missionCode = "TEST", world = world),
                layers = TileLayers(
                    base = List(cells) { if (it / width == 2) base[it % width] ?: -1 else -1 },
                    solid = List(cells) { -1 },
                    top = List(cells) { -1 },
                    goalLayer = List(cells) { 0 },
                    bonusLayer = List(cells) { -1 },
                    manTypeLayer = List(cells) { 0 },
                    manAmountLayer = List(cells) { 0 },
                ),
                missionText = MissionText(),
                objects = listOf(AuthoredObject("p1", ObjectKinds.PLAYER_START, 2 * t + 16, 2 * t + 16, mapOf("direction" to "4", "lives" to "3"))),
            ),
        )
        match.initialize()
        return match
    }

    private fun assertEquals(expected: Float, actual: Float, tolerance: Float) =
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected, got $actual")
}

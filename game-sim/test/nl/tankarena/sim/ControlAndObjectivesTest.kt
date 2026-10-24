package nl.tankarena.sim

import nl.tankarena.content.LEGACY_TILE_SIZE
import nl.tankarena.content.MapMetadata
import nl.tankarena.content.MissionText
import nl.tankarena.content.TileLayers
import nl.tankarena.input.PlayerIntentFrame
import nl.tankarena.protocol.snapshot.GameEvent
import nl.tankarena.sim.legacy.AuthoredObject
import nl.tankarena.sim.legacy.CanonicalMapDefinition
import nl.tankarena.sim.legacy.ObjectKinds
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Tank hand-over, F1–F10 switching, destructible turrets and the legacy fuel model. */
class ControlAndObjectivesTest {

    private var prototype: Match? = null

    @AfterTest
    fun tearDown() {
        prototype?.dispose()
        prototype = null
    }

    private fun start(map: CanonicalMapDefinition, mode: MatchMode = MatchMode.SINGLE): Match =
        Match.fromCanonicalMap(map, mode).also {
            it.initialize()
            prototype = it
        }

    @Test
    fun `losing the controlled tank hands control to the next friendly tank`() {
        val match = start(map(humanTank(id = 1, tileX = 1), humanTank(id = 2, tileX = 4), enemyTank(tileX = 12)))
        assertEquals(1, match.playerTankForTest(0)?.controllerId)

        match.queueDamageForTest(0, 999)
        val events = (0 until 3).flatMap { match.tick().events }

        assertEquals(2, match.playerTankForTest(0)?.controllerId)
        assertTrue(events.any { it is GameEvent.ControlSwitched && it.playerId == 0 })
        assertEquals(Match.MissionStatus.IN_PROGRESS, match.mission)
        assertEquals(0, match.goalProgressBad, "a surviving friendly tank means no -100 penalty")
    }

    @Test
    fun `losing the last friendly tank loses the mission`() {
        val match = start(map(humanTank(id = 1, tileX = 1), humanTank(id = 2, tileX = 4), enemyTank(tileX = 12)))
        match.queueDamageForTest(0, 999)
        repeat(3) { match.tick() }
        match.queueDamageForTest(0, 999)
        repeat(3) { match.tick() }
        assertEquals(Match.MissionStatus.LOST, match.mission)
    }

    @Test
    fun `selectTankId switches control like F1 to F10`() {
        val match = start(map(humanTank(id = 1, tileX = 1), humanTank(id = 2, tileX = 4), enemyTank(tileX = 12)))
        match.tick(mapOf(0 to PlayerIntentFrame(selectTankId = 2)))
        assertEquals(2, match.playerTankForTest(0)?.controllerId)
        val idle = match.tanksForTest().single { it.controllerId == 1 }
        assertEquals(-1, idle.playerIndex)
    }

    @Test
    fun `nextTank takes the next friendly tank and wraps round`() {
        val match = start(map(humanTank(id = 1, tileX = 1), humanTank(id = 2, tileX = 4), humanTank(id = 3, tileX = 7), enemyTank(tileX = 12)))
        val seen = (1..4).map {
            match.tick(mapOf(0 to PlayerIntentFrame(nextTank = true)))
            repeat(2) { match.tick() }
            match.playerTankForTest(0)?.controllerId
        }
        assertEquals(listOf(2, 3, 1, 2), seen)
    }

    @Test
    fun `shooting down the building under a turret brings the turret down and its goal contribution wins`() {
        val match = start(
            map(
                humanTank(id = 1, tileX = 1, direction = 4, armor = 5000),
                turret(tileX = 5, goalContribution = 100),
            ),
        )
        var destroyed = false
        for (i in 0 until 1200) {
            val frame = match.tick(mapOf(0 to PlayerIntentFrame(firePrimary = true)))
            if (frame.events.any { it is GameEvent.TurretDestroyed }) destroyed = true
            if (match.mission != Match.MissionStatus.IN_PROGRESS) break
        }
        // Legacy control_turret(): the turret itself cannot be hit; it falls with its building.
        assertTrue(destroyed, "turret should fall when the building under it is shot down")
        assertTrue(match.turretsForTest().none { it.isAlive })
        assertEquals(Match.MissionStatus.WON, match.mission)
    }

    @Test
    fun `fuel drains by distance driven for player tanks only`() {
        val match = start(map(humanTank(id = 1, tileX = 1, direction = 4), enemyTank(tileX = 12, direction = 12)))
        val player = assertNotNull(match.playerTankForTest(0))
        val enemy = match.tanksForTest().single { it.team == 1 }
        val startX = player.positionX
        repeat(30) { match.tick(mapOf(0 to PlayerIntentFrame(forward = true))) }
        val driven = player.positionX - startX
        assertTrue(driven > 0)
        val burned = LEGACY_MAX_FUEL - player.fuel
        assertTrue(burned in (driven - 5)..(driven + 5), "burned $burned fuel for $driven px")
        assertEquals(LEGACY_MAX_FUEL, enemy.fuel, "computer tanks do not use fuel")
    }

    private fun humanTank(id: Int, tileX: Int, direction: Int = 0, armor: Int = 100) = AuthoredObject(
        id = "human-$id",
        kind = ObjectKinds.AI_TANK,
        x = tileX * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
        y = 3 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
        properties = mapOf(
            "controllerId" to id.toString(),
            "computer" to "0",
            "good" to "1",
            "direction" to direction.toString(),
            "armor" to armor.toString(),
        ),
    )

    private fun enemyTank(tileX: Int, direction: Int = 0) = AuthoredObject(
        id = "enemy-$tileX",
        kind = ObjectKinds.AI_TANK,
        x = tileX * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
        y = 8 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
        properties = mapOf("computer" to "1", "good" to "0", "direction" to direction.toString(), "armor" to "5000"),
    )

    private fun turret(tileX: Int, goalContribution: Int) = AuthoredObject(
        id = "turret-$tileX",
        kind = ObjectKinds.TURRET,
        x = tileX * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
        y = 3 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
        properties = mapOf("goalContribution" to goalContribution.toString()),
    )

    private fun map(vararg objects: AuthoredObject): CanonicalMapDefinition {
        val width = 16
        val height = 10
        val cells = width * height
        // Turrets stand on buildings (desert picture 8, @ASEAA: strength 20).
        val turretTiles = objects.filter { it.kind == ObjectKinds.TURRET }
            .map { it.x / LEGACY_TILE_SIZE + (it.y / LEGACY_TILE_SIZE) * width }
            .toSet()
        return CanonicalMapDefinition(
            metadata = MapMetadata(name = "fixture", widthTiles = width, heightTiles = height, missionCode = "FIXTURE"),
            layers = TileLayers(
                base = List(cells) { -1 },
                solid = List(cells) { if (it in turretTiles) BUILDING_PICTURE else -1 },
                top = List(cells) { -1 },
                goalLayer = List(cells) { 0 },
                bonusLayer = List(cells) { -1 },
                manTypeLayer = List(cells) { 0 },
                manAmountLayer = List(cells) { 0 },
            ),
            missionText = MissionText(),
            objects = objects.toList(),
        )
    }

    private companion object {
        const val BUILDING_PICTURE = 8
    }
}

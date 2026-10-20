package nl.tankarena.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TankArenaSceneRoundTripTest {

    @Test
    fun `hand-written scene JSON deserializes into one actor per registered type`() {
        val sceneJson = """
            [
              {"typeId":"tankArenaTank","state":"{\"body\":{\"position\":{\"x\":66.0,\"y\":297.0},\"size\":{\"width\":29.0,\"height\":29.0},\"pivot\":{\"x\":14.5,\"y\":14.5},\"scale\":{\"horizontal\":1.0,\"vertical\":1.0},\"rotation\":0.0},\"bodyDirection\":0,\"turretDirection\":0,\"playerIndex\":0,\"tankType\":1,\"armor\":100,\"fuel\":100,\"lives\":3,\"team\":0}"},
              {"typeId":"tankArenaTurret","state":"{\"body\":{\"position\":{\"x\":330.0,\"y\":66.0},\"size\":{\"width\":33.0,\"height\":33.0},\"pivot\":{\"x\":16.5,\"y\":16.5},\"scale\":{\"horizontal\":1.0,\"vertical\":1.0},\"rotation\":0.0},\"turretDirection\":8,\"team\":1,\"armor\":100}"},
              {"typeId":"tankArenaGoal","state":"{\"body\":{\"position\":{\"x\":264.0,\"y\":264.0},\"size\":{\"width\":16.0,\"height\":16.0},\"pivot\":{\"x\":8.0,\"y\":8.0},\"scale\":{\"horizontal\":1.0,\"vertical\":1.0},\"rotation\":0.0},\"who\":0,\"contribution\":100,\"radius\":16,\"isClaimed\":false}"}
            ]
        """.trimIndent()

        val actors = SceneFile.read(sceneJson)

        assertEquals(3, actors.size)
        val tank = actors.filterIsInstance<Tank>().single()
        val turret = actors.filterIsInstance<Turret>().single()
        val goal = actors.filterIsInstance<Goal>().single()

        assertEquals(0, tank.playerIndex)
        assertEquals(3, tank.lives)
        assertEquals(100, tank.armor)
        assertEquals(66f, tank.body.position.x)

        assertEquals(8, turret.turretDirection)
        assertEquals(1, turret.team)

        assertEquals(100, goal.contribution)
        assertEquals(16, goal.radius)
        assertTrue(!goal.isClaimed)
    }

    @Test
    fun `unknown typeIds are silently skipped so scenes remain forward-compatible`() {
        val sceneJson = """
            [
              {"typeId":"tankArenaGoal","state":"{\"body\":{\"position\":{\"x\":0.0,\"y\":0.0},\"size\":{\"width\":16.0,\"height\":16.0},\"pivot\":{\"x\":8.0,\"y\":8.0},\"scale\":{\"horizontal\":1.0,\"vertical\":1.0},\"rotation\":0.0}}"},
              {"typeId":"futureMine","state":"{\"anything\":true}"}
            ]
        """.trimIndent()

        val actors = SceneFile.read(sceneJson)

        assertEquals(1, actors.size)
        assertTrue(actors.single() is Goal)
    }

    @Test
    fun `constructed actors roundtrip through serializeActors plus deserializeActors`() {
        val original = listOf(
            Tank(
                Tank.State(
                    body = box(x = 66f, y = 297f, size = 29f),
                    bodyDirection = 4,
                    turretDirection = 12,
                    playerIndex = 0,
                    tankType = 2,
                    armor = 75,
                    fuel = 82,
                    lives = 2,
                    team = 0,
                ),
            ),
            Turret(
                Turret.State(
                    body = box(x = 330f, y = 66f, size = 33f),
                    turretDirection = 8,
                    team = 1,
                    armor = 90,
                ),
            ),
            Goal(
                Goal.State(
                    body = box(x = 264f, y = 264f, size = 16f),
                    who = 0,
                    contribution = 25,
                    radius = 12,
                    isClaimed = true,
                ),
            ),
        )

        val json = SceneFile.write(original)
        val restored = SceneFile.read(json)

        assertEquals(3, restored.size)
        val restoredTank = restored.filterIsInstance<Tank>().single()
        assertEquals(4, restoredTank.bodyDirection)
        assertEquals(12, restoredTank.turretDirection)
        assertEquals(75, restoredTank.armor)
        assertEquals(82, restoredTank.fuel)
        assertEquals(2, restoredTank.lives)
        assertEquals(2, restoredTank.tankType)

        val restoredTurret = restored.filterIsInstance<Turret>().single()
        assertEquals(8, restoredTurret.turretDirection)
        assertEquals(90, restoredTurret.armor)

        val restoredGoal = restored.filterIsInstance<Goal>().single()
        assertEquals(25, restoredGoal.contribution)
        assertEquals(12, restoredGoal.radius)
        assertTrue(restoredGoal.isClaimed)
    }

    private fun box(x: Float, y: Float, size: Float): BoxBody = BoxBody(
        position = ScenePoint(x, y),
        size = SceneExtent(size, size),
    )
}

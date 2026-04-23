package com.tankarena.sim.kubriko.server

import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.serialization.SerializableMetadata
import com.pandulapeter.kubriko.types.SceneOffset
import com.pandulapeter.kubriko.types.SceneSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TankArenaSceneRoundTripTest {

    @Test
    fun `hand-written scene JSON deserializes into one actor per registered type`() {
        val sceneJson = """
            [
              {"typeId":"tankArenaWall","state":"{\"body\":{\"position\":{\"x\":99.0,\"y\":33.0},\"size\":{\"width\":33.0,\"height\":33.0},\"pivot\":{\"x\":16.5,\"y\":16.5},\"scale\":{\"horizontal\":1.0,\"vertical\":1.0},\"rotation\":0.0}}"},
              {"typeId":"tankArenaTank","state":"{\"body\":{\"position\":{\"x\":66.0,\"y\":297.0},\"size\":{\"width\":29.0,\"height\":29.0},\"pivot\":{\"x\":14.5,\"y\":14.5},\"scale\":{\"horizontal\":1.0,\"vertical\":1.0},\"rotation\":0.0},\"bodyDirection\":0,\"turretDirection\":0,\"playerIndex\":0,\"tankType\":1,\"armor\":100,\"fuel\":100,\"lives\":3,\"team\":0}"},
              {"typeId":"tankArenaTurret","state":"{\"body\":{\"position\":{\"x\":330.0,\"y\":66.0},\"size\":{\"width\":33.0,\"height\":33.0},\"pivot\":{\"x\":16.5,\"y\":16.5},\"scale\":{\"horizontal\":1.0,\"vertical\":1.0},\"rotation\":0.0},\"turretDirection\":8,\"team\":1,\"armor\":100}"},
              {"typeId":"tankArenaGoal","state":"{\"body\":{\"position\":{\"x\":264.0,\"y\":264.0},\"size\":{\"width\":16.0,\"height\":16.0},\"pivot\":{\"x\":8.0,\"y\":8.0},\"scale\":{\"horizontal\":1.0,\"vertical\":1.0},\"rotation\":0.0},\"who\":0,\"contribution\":100,\"radius\":16,\"isClaimed\":false}"}
            ]
        """.trimIndent()

        val serializationManager = SerializableMetadata.newSerializationManagerInstance(
            *tankArenaSerializableMetadata,
        )
        val actors = serializationManager.deserializeActors(sceneJson)

        assertEquals(4, actors.size)
        val wall = actors.filterIsInstance<ServerWallActor>().single()
        val tank = actors.filterIsInstance<ServerTankActor>().single()
        val turret = actors.filterIsInstance<ServerTurretActor>().single()
        val goal = actors.filterIsInstance<ServerGoalActor>().single()

        assertEquals(99f, wall.body.position.x.raw)
        assertEquals(33f, wall.body.size.width.raw)

        assertEquals(0, tank.playerIndex)
        assertEquals(3, tank.lives)
        assertEquals(100, tank.armor)
        assertEquals(66f, tank.body.position.x.raw)

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
              {"typeId":"tankArenaWall","state":"{\"body\":{\"position\":{\"x\":0.0,\"y\":0.0},\"size\":{\"width\":33.0,\"height\":33.0},\"pivot\":{\"x\":16.5,\"y\":16.5},\"scale\":{\"horizontal\":1.0,\"vertical\":1.0},\"rotation\":0.0}}"},
              {"typeId":"futureMine","state":"{\"anything\":true}"}
            ]
        """.trimIndent()

        val serializationManager = SerializableMetadata.newSerializationManagerInstance(
            *tankArenaSerializableMetadata,
        )
        val actors = serializationManager.deserializeActors(sceneJson)

        assertEquals(1, actors.size)
        assertTrue(actors.single() is ServerWallActor)
    }

    @Test
    fun `constructed actors roundtrip through serializeActors plus deserializeActors`() {
        val serializationManager = SerializableMetadata.newSerializationManagerInstance(
            *tankArenaSerializableMetadata,
        )
        val original = listOf(
            ServerWallActor(
                ServerWallActor.State(body = box(x = 132f, y = 66f, size = 33f)),
            ),
            ServerTankActor(
                ServerTankActor.State(
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
            ServerTurretActor(
                ServerTurretActor.State(
                    body = box(x = 330f, y = 66f, size = 33f),
                    turretDirection = 8,
                    team = 1,
                    armor = 90,
                ),
            ),
            ServerGoalActor(
                ServerGoalActor.State(
                    body = box(x = 264f, y = 264f, size = 16f),
                    who = 0,
                    contribution = 25,
                    radius = 12,
                    isClaimed = true,
                ),
            ),
        )

        val json = serializationManager.serializeActors(original)
        val restored = serializationManager.deserializeActors(json)

        assertEquals(4, restored.size)
        val restoredTank = restored.filterIsInstance<ServerTankActor>().single()
        assertEquals(4, restoredTank.bodyDirection)
        assertEquals(12, restoredTank.turretDirection)
        assertEquals(75, restoredTank.armor)
        assertEquals(82, restoredTank.fuel)
        assertEquals(2, restoredTank.lives)
        assertEquals(2, restoredTank.tankType)

        val restoredTurret = restored.filterIsInstance<ServerTurretActor>().single()
        assertEquals(8, restoredTurret.turretDirection)
        assertEquals(90, restoredTurret.armor)

        val restoredGoal = restored.filterIsInstance<ServerGoalActor>().single()
        assertEquals(25, restoredGoal.contribution)
        assertEquals(12, restoredGoal.radius)
        assertTrue(restoredGoal.isClaimed)
    }

    private fun box(x: Float, y: Float, size: Float): BoxBody = BoxBody(
        initialPosition = SceneOffset(x.sceneUnit, y.sceneUnit),
        initialSize = SceneSize(size.sceneUnit, size.sceneUnit),
    )
}

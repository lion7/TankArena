package com.tankarena.sim.kubriko.server

import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.actor.body.PointBody
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.serialization.Serializable
import com.pandulapeter.kubriko.serialization.SerializableMetadata
import com.pandulapeter.kubriko.types.SceneOffset
import com.pandulapeter.kubriko.types.SceneSize
import com.tankarena.protocol.snapshot.ProjectileOwnerKind
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the save/restore contract for every Server*Actor against silent schema drift.
 * Each test constructs a non-default actor, runs it through serializeActors (which
 * invokes save()), then deserializes via tankArenaSerializableMetadata and asserts
 * the observable state survived intact.
 */
class ActorRoundTripTest {

    private val manager = SerializableMetadata.newSerializationManagerInstance(*tankArenaSerializableMetadata)

    @Test
    fun `ServerWallActor round-trip preserves body geometry`() {
        val original = ServerWallActor(
            ServerWallActor.State(body = box(x = 132f, y = 264f, w = 33f, h = 66f)),
        )
        val restored = roundTrip(original).filterIsInstance<ServerWallActor>().single()

        assertEquals(132f, restored.body.position.x.raw)
        assertEquals(264f, restored.body.position.y.raw)
        assertEquals(33f, restored.body.size.width.raw)
        assertEquals(66f, restored.body.size.height.raw)
    }

    @Test
    fun `ServerTankActor round-trip preserves all observable state`() {
        val original = ServerTankActor(
            ServerTankActor.State(
                body = box(x = 99f, y = 297f, w = 29f, h = 29f),
                bodyDirection = 6,
                turretDirection = 2,
                playerIndex = 1,
                tankType = 3,
                armor = 73,
                fuel = 41,
                lives = 2,
                team = 1,
                velocityX = 1.5f,
                velocityY = -2.25f,
                primaryCooldownTicks = 17,
                respawnInTicks = 0,
                maxArmor = 100,
                maxFuel = 80,
                spawnX = 200,
                spawnY = 300,
                spawnBodyDirection = 0,
                spawnTurretDirection = 4,
            ),
        )
        val restored = roundTrip(original).filterIsInstance<ServerTankActor>().single()

        assertEquals(6, restored.bodyDirection)
        assertEquals(2, restored.turretDirection)
        assertEquals(1, restored.playerIndex)
        assertEquals(3, restored.tankType)
        assertEquals(73, restored.armor)
        assertEquals(41, restored.fuel)
        assertEquals(2, restored.lives)
        assertEquals(1, restored.team)
        assertEquals(1.5f, restored.velocityX)
        assertEquals(-2.25f, restored.velocityY)
        assertEquals(17, restored.primaryCooldownTicks)
        assertEquals(99f, restored.body.position.x.raw)
        assertEquals(297f, restored.body.position.y.raw)
    }

    @Test
    fun `ServerTurretActor round-trip preserves direction team armor and cooldown`() {
        val original = ServerTurretActor(
            ServerTurretActor.State(
                body = box(x = 198f, y = 132f, w = 33f, h = 33f),
                turretDirection = 11,
                team = 1,
                armor = 64,
                cooldownTicks = 90,
            ),
        )
        val restored = roundTrip(original).filterIsInstance<ServerTurretActor>().single()

        assertEquals(11, restored.turretDirection)
        assertEquals(1, restored.team)
        assertEquals(64, restored.armor)
        assertEquals(90, restored.cooldownTicks)
        assertEquals(198f, restored.body.position.x.raw)
    }

    @Test
    fun `ServerGoalActor round-trip preserves who contribution radius and claim flag`() {
        val original = ServerGoalActor(
            ServerGoalActor.State(
                body = box(x = 264f, y = 264f, w = 16f, h = 16f),
                who = 1,
                contribution = 33,
                radius = 24,
                isClaimed = true,
            ),
        )
        val restored = roundTrip(original).filterIsInstance<ServerGoalActor>().single()

        assertEquals(1, restored.who)
        assertEquals(33, restored.contribution)
        assertEquals(24, restored.radius)
        assertEquals(true, restored.isClaimed)
    }

    @Test
    fun `ServerProjectileActor round-trip preserves ownership velocity damage and TTL`() {
        val original = ServerProjectileActor(
            ServerProjectileActor.State(
                body = PointBody(initialPosition = SceneOffset(150f.sceneUnit, 250f.sceneUnit)),
                ownerActorId = 42L,
                ownerKind = ProjectileOwnerKind.TURRET,
                damage = 17,
                velocityX = 6,
                velocityY = -3,
                ttlTicks = 23,
            ),
        )
        val restored = roundTrip(original).filterIsInstance<ServerProjectileActor>().single()

        assertEquals(42L, restored.ownerActorId)
        assertEquals(ProjectileOwnerKind.TURRET, restored.ownerKind)
        assertEquals(17, restored.damage)
        assertEquals(6, restored.velocityX)
        assertEquals(-3, restored.velocityY)
        assertEquals(23, restored.ttlTicks)
        assertEquals(150, restored.positionX)
        assertEquals(250, restored.positionY)
    }

    private fun roundTrip(actor: Serializable<*>): List<com.pandulapeter.kubriko.actor.Actor> {
        val json = manager.serializeActors(listOf(actor))
        return manager.deserializeActors(json)
    }

    private fun box(x: Float, y: Float, w: Float, h: Float): BoxBody = BoxBody(
        initialPosition = SceneOffset(x.sceneUnit, y.sceneUnit),
        initialSize = SceneSize(w.sceneUnit, h.sceneUnit),
    )
}

package com.tankarena.sim.kubriko.server

import com.pandulapeter.kubriko.Kubriko
import com.pandulapeter.kubriko.actor.Actor
import com.pandulapeter.kubriko.collision.CollisionManager
import com.pandulapeter.kubriko.helpers.ManualTickSource
import com.pandulapeter.kubriko.helpers.TickSource
import com.pandulapeter.kubriko.helpers.extensions.get
import com.pandulapeter.kubriko.manager.ActorManager
import com.pandulapeter.kubriko.serialization.Serializable
import com.pandulapeter.kubriko.serialization.SerializableMetadata
import com.tankarena.content.CanonicalMapDefinition
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.protocol.Team
import com.tankarena.protocol.snapshot.ActorState
import com.tankarena.protocol.snapshot.GoalState
import com.tankarena.protocol.snapshot.TankState
import com.tankarena.protocol.snapshot.TurretState
import com.tankarena.protocol.snapshot.WallState
import com.tankarena.protocol.snapshot.WorldSnapshot
import com.tankarena.sim.kubriko.TerrainSlideManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

private const val MILLIS_PER_TICK: Int = 33

class ServerMatchPrototype private constructor(
    private val map: CanonicalMapDefinition,
    private val initialActors: List<Serializable<*>>,
) {

    private val tickSource: ManualTickSource = TickSource.manual() as ManualTickSource
    private val serializationManager = SerializableMetadata.newSerializationManagerInstance(
        *tankArenaSerializableMetadata,
    )
    private val worldWidthPixels: Int = map.metadata.widthTiles * LEGACY_TILE_SIZE
    private val worldHeightPixels: Int = map.metadata.heightTiles * LEGACY_TILE_SIZE
    private val kubriko: Kubriko = Kubriko.newInstance(
        ActorManager.newInstance(
            initialActors = initialActors,
            shouldPutFarAwayActorsToSleep = false,
        ),
        CollisionManager.newInstance(),
        serializationManager,
        TerrainSlideManager(worldWidthPixels, worldHeightPixels),
        tickSource = tickSource,
    )
    private val actorManager: ActorManager = kubriko.get()
    private val actorIds: MutableMap<Actor, Long> = IdentityHashMap()
    private var nextActorId: Long = 1L
    private var currentTick: Long = 0L

    val worldWidth: Int get() = worldWidthPixels
    val worldHeight: Int get() = worldHeightPixels

    fun initialize() {
        kubriko.initialize()
        awaitActorsPopulated(expectedSize = initialActors.size)
        for (actor in actorManager.allActors.value) {
            actorIds.getOrPut(actor) { nextActorId++ }
        }
    }

    fun tick(): WorldSnapshot {
        tickSource.tick(MILLIS_PER_TICK)
        currentTick += 1
        return buildSnapshot()
    }

    fun snapshot(): WorldSnapshot = buildSnapshot()

    fun dispose() {
        kubriko.dispose()
    }

    private fun awaitActorsPopulated(expectedSize: Int) = runBlocking {
        withTimeout(2_000) {
            while (actorManager.allActors.value.size < expectedSize) {
                delay(5)
            }
        }
    }

    private fun buildSnapshot(): WorldSnapshot {
        val states = actorManager.allActors.value.mapNotNull { actor ->
            val id = actorIds[actor] ?: return@mapNotNull null
            projectToWireState(actor, id)
        }
        return WorldSnapshot(tick = currentTick, actors = states)
    }

    private fun projectToWireState(actor: Actor, id: Long): ActorState? {
        return when (actor) {
            is ServerWallActor -> {
                val size = actor.body.size
                val position = actor.body.position
                WallState(
                    actorId = id,
                    x = (position.x.raw + size.width.raw / 2f).toInt(),
                    y = (position.y.raw + size.height.raw / 2f).toInt(),
                    width = size.width.raw.toInt(),
                    height = size.height.raw.toInt(),
                )
            }

            is ServerTankActor -> {
                val size = actor.body.size
                val position = actor.body.position
                TankState(
                    actorId = id,
                    team = actor.team.toProtocolTeam(),
                    tankType = actor.tankType,
                    x = (position.x.raw + size.width.raw / 2f).toInt(),
                    y = (position.y.raw + size.height.raw / 2f).toInt(),
                    bodyDirection = actor.bodyDirection,
                    turretDirection = actor.turretDirection,
                    armor = actor.armor,
                    alive = actor.armor > 0,
                    controlled = actor.playerIndex >= 0,
                )
            }

            is ServerTurretActor -> {
                val size = actor.body.size
                val position = actor.body.position
                TurretState(
                    actorId = id,
                    team = actor.team.toProtocolTeam(),
                    x = (position.x.raw + size.width.raw / 2f).toInt(),
                    y = (position.y.raw + size.height.raw / 2f).toInt(),
                    turretDirection = actor.turretDirection,
                    alive = actor.armor > 0,
                )
            }

            is ServerGoalActor -> {
                val size = actor.body.size
                val position = actor.body.position
                GoalState(
                    actorId = id,
                    team = when (actor.who) {
                        0 -> Team.PLAYER
                        1 -> Team.ENEMY
                        else -> Team.NEUTRAL
                    },
                    x = (position.x.raw + size.width.raw / 2f).toInt(),
                    y = (position.y.raw + size.height.raw / 2f).toInt(),
                    captured = actor.isClaimed,
                )
            }

            else -> null
        }
    }

    private fun Int.toProtocolTeam(): Team = when (this) {
        0 -> Team.PLAYER
        1 -> Team.ENEMY
        else -> Team.NEUTRAL
    }

    companion object {
        fun fromCanonicalMap(map: CanonicalMapDefinition): ServerMatchPrototype {
            val serializationManagerForBuild = SerializableMetadata.newSerializationManagerInstance(
                *tankArenaSerializableMetadata,
            )
            val sceneJson = CanonicalSceneBuilder.buildSceneJson(map, serializationManagerForBuild)
            val actors = serializationManagerForBuild.deserializeActors(sceneJson)
            return ServerMatchPrototype(map = map, initialActors = actors)
        }
    }
}

private typealias IdentityHashMap<K, V> = java.util.IdentityHashMap<K, V>

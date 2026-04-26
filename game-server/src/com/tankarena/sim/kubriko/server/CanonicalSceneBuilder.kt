package com.tankarena.sim.kubriko.server

import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.serialization.Serializable
import com.pandulapeter.kubriko.serialization.SerializableMetadata
import com.pandulapeter.kubriko.serialization.SerializationManager
import com.pandulapeter.kubriko.types.SceneOffset
import com.pandulapeter.kubriko.types.SceneSize
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.sim.kubriko.server.legacy.CanonicalMapDefinition
import com.tankarena.sim.kubriko.server.legacy.ObjectKinds

private const val TANK_FOOTPRINT: Int = LEGACY_TILE_SIZE - 4
private const val TURRET_FOOTPRINT: Int = LEGACY_TILE_SIZE
private const val GOAL_FOOTPRINT: Int = 16

object CanonicalSceneBuilder {

    fun buildActors(map: CanonicalMapDefinition): List<Serializable<*>> {
        val actors = mutableListOf<Serializable<*>>()
        appendSolidWalls(map, actors)
        appendBoundaryWalls(map, actors)
        appendAuthoredObjects(map, actors)
        return actors
    }

    fun buildSceneJson(
        map: CanonicalMapDefinition,
        serializationManager: SerializationManager<SerializableMetadata<*>, Serializable<*>>,
    ): String = serializationManager.serializeActors(buildActors(map))

    private fun appendSolidWalls(map: CanonicalMapDefinition, sink: MutableList<Serializable<*>>) {
        val width = map.metadata.widthTiles
        val height = map.metadata.heightTiles
        for (y in 0 until height) {
            for (x in 0 until width) {
                val solid = map.layers.solid.getOrElse(x + y * width) { -1 }
                if (solid < 0) continue
                sink += wallActor(
                    cx = x * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    cy = y * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    width = LEGACY_TILE_SIZE,
                    height = LEGACY_TILE_SIZE,
                )
            }
        }
    }

    private fun appendBoundaryWalls(map: CanonicalMapDefinition, sink: MutableList<Serializable<*>>) {
        val w = map.metadata.widthTiles * LEGACY_TILE_SIZE
        val h = map.metadata.heightTiles * LEGACY_TILE_SIZE
        val t = LEGACY_TILE_SIZE
        sink += wallActor(cx = w / 2, cy = -t / 2, width = w + 2 * t, height = t)
        sink += wallActor(cx = w / 2, cy = h + t / 2, width = w + 2 * t, height = t)
        sink += wallActor(cx = -t / 2, cy = h / 2, width = t, height = h + 2 * t)
        sink += wallActor(cx = w + t / 2, cy = h / 2, width = t, height = h + 2 * t)
    }

    private fun appendAuthoredObjects(map: CanonicalMapDefinition, sink: MutableList<Serializable<*>>) {
        var nextPlayerIndex = 0
        for (obj in map.objects) {
            when (obj.kind) {
                ObjectKinds.PLAYER_START -> {
                    sink += tankActor(
                        cx = obj.x,
                        cy = obj.y,
                        playerIndex = nextPlayerIndex++,
                        bodyDirection = obj.properties["direction"]?.toIntOrNull() ?: 0,
                        turretDirection = obj.properties["direction"]?.toIntOrNull() ?: 0,
                        tankType = obj.properties["tankType"]?.toIntOrNull() ?: 0,
                        armor = obj.properties["armor"]?.toIntOrNull() ?: 100,
                        fuel = obj.properties["fuel"]?.toIntOrNull() ?: 100,
                        lives = obj.properties["lives"]?.toIntOrNull() ?: 1,
                        team = 0,
                    )
                }

                ObjectKinds.ENFORCER, ObjectKinds.DESTROYER -> {
                    sink += tankActor(
                        cx = obj.x,
                        cy = obj.y,
                        playerIndex = -1,
                        bodyDirection = obj.properties["direction"]?.toIntOrNull() ?: 0,
                        turretDirection = obj.properties["direction"]?.toIntOrNull() ?: 0,
                        tankType = obj.properties["tankType"]?.toIntOrNull() ?: 0,
                        armor = obj.properties["armor"]?.toIntOrNull() ?: 100,
                        fuel = obj.properties["fuel"]?.toIntOrNull() ?: 100,
                        lives = obj.properties["lives"]?.toIntOrNull() ?: 1,
                        team = 1,
                    )
                }

                ObjectKinds.TURRET -> {
                    sink += turretActor(
                        cx = obj.x,
                        cy = obj.y,
                        turretDirection = obj.properties["direction"]?.toIntOrNull() ?: 0,
                        team = 1,
                        armor = obj.properties["armor"]?.toIntOrNull() ?: 100,
                    )
                }

                ObjectKinds.GOAL -> {
                    sink += goalActor(
                        cx = obj.x,
                        cy = obj.y,
                        who = obj.properties["who"]?.toIntOrNull() ?: 0,
                        contribution = obj.properties["contribution"]?.toIntOrNull() ?: 100,
                        radius = obj.properties["radius"]?.toIntOrNull() ?: GOAL_FOOTPRINT,
                    )
                }

                else -> Unit
            }
        }
    }

    private fun wallActor(cx: Int, cy: Int, width: Int, height: Int): ServerWallActor =
        ServerWallActor(
            ServerWallActor.State(
                body = BoxBody(
                    initialPosition = SceneOffset((cx - width / 2).toFloat().sceneUnit, (cy - height / 2).toFloat().sceneUnit),
                    initialSize = SceneSize(width.toFloat().sceneUnit, height.toFloat().sceneUnit),
                ),
            ),
        )

    private fun tankActor(
        cx: Int,
        cy: Int,
        playerIndex: Int,
        bodyDirection: Int,
        turretDirection: Int,
        tankType: Int,
        armor: Int,
        fuel: Int,
        lives: Int,
        team: Int,
    ): ServerTankActor = ServerTankActor(
        ServerTankActor.State(
            body = BoxBody(
                initialPosition = SceneOffset((cx - TANK_FOOTPRINT / 2).toFloat().sceneUnit, (cy - TANK_FOOTPRINT / 2).toFloat().sceneUnit),
                initialSize = SceneSize(TANK_FOOTPRINT.toFloat().sceneUnit, TANK_FOOTPRINT.toFloat().sceneUnit),
            ),
            bodyDirection = bodyDirection,
            turretDirection = turretDirection,
            playerIndex = playerIndex,
            tankType = tankType,
            armor = armor,
            fuel = fuel,
            lives = lives,
            team = team,
        ),
    )

    private fun turretActor(cx: Int, cy: Int, turretDirection: Int, team: Int, armor: Int): ServerTurretActor =
        ServerTurretActor(
            ServerTurretActor.State(
                body = BoxBody(
                    initialPosition = SceneOffset((cx - TURRET_FOOTPRINT / 2).toFloat().sceneUnit, (cy - TURRET_FOOTPRINT / 2).toFloat().sceneUnit),
                    initialSize = SceneSize(TURRET_FOOTPRINT.toFloat().sceneUnit, TURRET_FOOTPRINT.toFloat().sceneUnit),
                ),
                turretDirection = turretDirection,
                team = team,
                armor = armor,
            ),
        )

    private fun goalActor(cx: Int, cy: Int, who: Int, contribution: Int, radius: Int): ServerGoalActor =
        ServerGoalActor(
            ServerGoalActor.State(
                body = BoxBody(
                    initialPosition = SceneOffset((cx - GOAL_FOOTPRINT / 2).toFloat().sceneUnit, (cy - GOAL_FOOTPRINT / 2).toFloat().sceneUnit),
                    initialSize = SceneSize(GOAL_FOOTPRINT.toFloat().sceneUnit, GOAL_FOOTPRINT.toFloat().sceneUnit),
                ),
                who = who,
                contribution = contribution,
                radius = radius,
                isClaimed = false,
            ),
        )
}

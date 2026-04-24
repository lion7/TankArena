package com.tankarena.sim.kubriko.server

import com.pandulapeter.kubriko.serialization.SerializableMetadata
import kotlinx.serialization.json.Json

object TankArenaTypeIds {
    const val WALL: String = "tankArenaWall"
    const val TANK: String = "tankArenaTank"
    const val TURRET: String = "tankArenaTurret"
    const val GOAL: String = "tankArenaGoal"
    const val PROJECTILE: String = "tankArenaProjectile"
}

private val sceneJson = Json { ignoreUnknownKeys = true }

val tankArenaSerializableMetadata: Array<SerializableMetadata<*>> = arrayOf(
    SerializableMetadata(TankArenaTypeIds.WALL) { sceneJson.decodeFromString<ServerWallActor.State>(it) },
    SerializableMetadata(TankArenaTypeIds.TANK) { sceneJson.decodeFromString<ServerTankActor.State>(it) },
    SerializableMetadata(TankArenaTypeIds.TURRET) { sceneJson.decodeFromString<ServerTurretActor.State>(it) },
    SerializableMetadata(TankArenaTypeIds.GOAL) { sceneJson.decodeFromString<ServerGoalActor.State>(it) },
    SerializableMetadata(TankArenaTypeIds.PROJECTILE) { sceneJson.decodeFromString<ServerProjectileActor.State>(it) },
)

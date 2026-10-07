package com.tankarena.sim.kubriko.server

import com.pandulapeter.kubriko.serialization.SerializableMetadata
import kotlinx.serialization.json.Json

object TankArenaTypeIds {
    const val WALL: String = "tankArenaWall"
    const val TANK: String = "tankArenaTank"
    const val TURRET: String = "tankArenaTurret"
    const val GOAL: String = "tankArenaGoal"
    const val PROJECTILE: String = "tankArenaProjectile"
    const val MINE: String = "tankArenaMine"
    const val ROCKET: String = "tankArenaRocket"
    const val MORTAR: String = "tankArenaMortar"
    const val FLAG: String = "tankArenaFlag"
    const val PRODUCT: String = "tankArenaProduct"
    const val LOCK: String = "tankArenaLock"
    const val WARP: String = "tankArenaWarp"
    const val DESTROYER: String = "tankArenaDestroyer"
    const val ENFORCER: String = "tankArenaEnforcer"
    const val TRAIN: String = "tankArenaTrain"
    const val ZEPPELIN: String = "tankArenaZeppelin"
    const val B52: String = "tankArenaB52"
}

private val sceneJson = Json { ignoreUnknownKeys = true }

val tankArenaSerializableMetadata: Array<SerializableMetadata<*>> = arrayOf(
    SerializableMetadata(TankArenaTypeIds.WALL) { sceneJson.decodeFromString<ServerWallActor.State>(it) },
    SerializableMetadata(TankArenaTypeIds.TANK) { sceneJson.decodeFromString<ServerTankActor.State>(it) },
    SerializableMetadata(TankArenaTypeIds.TURRET) { sceneJson.decodeFromString<ServerTurretActor.State>(it) },
    SerializableMetadata(TankArenaTypeIds.GOAL) { sceneJson.decodeFromString<ServerGoalActor.State>(it) },
    SerializableMetadata(TankArenaTypeIds.PROJECTILE) { sceneJson.decodeFromString<ServerProjectileActor.State>(it) },
    SerializableMetadata(TankArenaTypeIds.MINE) { sceneJson.decodeFromString<ServerMineActor.State>(it) },
    SerializableMetadata(TankArenaTypeIds.ROCKET) { sceneJson.decodeFromString<ServerRocketActor.State>(it) },
    SerializableMetadata(TankArenaTypeIds.MORTAR) { sceneJson.decodeFromString<ServerMortarActor.State>(it) },
    SerializableMetadata(TankArenaTypeIds.FLAG) { sceneJson.decodeFromString<ServerFlagActor.State>(it) },
    SerializableMetadata(TankArenaTypeIds.PRODUCT) { sceneJson.decodeFromString<ServerProductActor.State>(it) },
    SerializableMetadata(TankArenaTypeIds.LOCK) { sceneJson.decodeFromString<ServerLockActor.State>(it) },
    SerializableMetadata(TankArenaTypeIds.WARP) { sceneJson.decodeFromString<ServerWarpActor.State>(it) },
    SerializableMetadata(TankArenaTypeIds.DESTROYER) { sceneJson.decodeFromString<ServerDestroyerActor.State>(it) },
    SerializableMetadata(TankArenaTypeIds.ENFORCER) { sceneJson.decodeFromString<ServerEnforcerActor.State>(it) },
    SerializableMetadata(TankArenaTypeIds.TRAIN) { sceneJson.decodeFromString<ServerTrainActor.State>(it) },
    SerializableMetadata(TankArenaTypeIds.ZEPPELIN) { sceneJson.decodeFromString<ServerZeppelinActor.State>(it) },
    SerializableMetadata(TankArenaTypeIds.B52) { sceneJson.decodeFromString<ServerB52Actor.State>(it) },
)

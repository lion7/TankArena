package com.tankarena.core

/**
 * Extension points for rewrite parity work.
 */
interface WeaponBehavior {
    val id: String
    fun onFire(state: GameState, sourceTank: TankEntity): List<Entity>
}

interface AiController {
    val id: String
    fun decide(state: GameState, tank: TankEntity): PlayerIntent
}

interface GameModeRules {
    val id: String
    fun onTick(state: GameState): GameState
}

interface MissionScript {
    val id: String
    fun onTick(state: GameState): GameState
}

class GameplayExtensions(
    val weapons: Map<String, WeaponBehavior> = emptyMap(),
    val aiControllers: Map<String, AiController> = emptyMap(),
    val gameModes: Map<String, GameModeRules> = emptyMap(),
    val missionScripts: Map<String, MissionScript> = emptyMap(),
)

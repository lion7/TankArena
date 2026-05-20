package com.tankarena.core

/**
 * Extension points for rewrite parity work.
 */
interface WeaponBehavior {
    val id: String
    fun onFire(state: GameState, sourceTank: TankEntity, nextEntityId: () -> Long): List<Entity>
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
    val weapons: Map<String, WeaponBehavior> = defaultWeapons(),
    val aiControllers: Map<String, AiController> = defaultAiControllers(),
    val gameModes: Map<String, GameModeRules> = defaultGameModes(),
    val missionScripts: Map<String, MissionScript> = emptyMap(),
)

private fun defaultWeapons(): Map<String, WeaponBehavior> =
    listOf(BasicShellWeapon, TripleSpreadWeapon).associateBy { it.id }

private fun defaultAiControllers(): Map<String, AiController> =
    listOf(BasicChaseAiController).associateBy { it.id }

private fun defaultGameModes(): Map<String, GameModeRules> =
    listOf(LastTankStandingModeRules).associateBy { it.id }

object BasicShellWeapon : WeaponBehavior {
    override val id: String = "basic_shell"

    override fun onFire(state: GameState, sourceTank: TankEntity, nextEntityId: () -> Long): List<Entity> = listOf(
        ProjectileEntity(
            id = nextEntityId(),
            position = sourceTank.position,
            velocity = Vec2(0f, if (sourceTank.playerId == 0) -160f else 160f),
            ownerTankId = sourceTank.id,
            ttlTicks = 30,
        ),
    )
}

object TripleSpreadWeapon : WeaponBehavior {
    override val id: String = "triple_spread"

    override fun onFire(state: GameState, sourceTank: TankEntity, nextEntityId: () -> Long): List<Entity> {
        val direction = if (sourceTank.playerId == 0) -1f else 1f
        return listOf(
            ProjectileEntity(nextEntityId(), sourceTank.position, Vec2(-40f, 160f * direction), sourceTank.id, ttlTicks = 24),
            ProjectileEntity(nextEntityId(), sourceTank.position, Vec2(0f, 180f * direction), sourceTank.id, ttlTicks = 24),
            ProjectileEntity(nextEntityId(), sourceTank.position, Vec2(40f, 160f * direction), sourceTank.id, ttlTicks = 24),
        )
    }
}

object BasicChaseAiController : AiController {
    override val id: String = "basic_chase"

    override fun decide(state: GameState, tank: TankEntity): PlayerIntent {
        val target = state.entities
            .filterIsInstance<TankEntity>()
            .filter { it.alive && it.playerId != tank.playerId }
            .minByOrNull { (it.position.x - tank.position.x) * (it.position.x - tank.position.x) + (it.position.y - tank.position.y) * (it.position.y - tank.position.y) }

        val actions = linkedSetOf<PlayerAction>()
        if (target != null) {
            val dx = target.position.x - tank.position.x
            val dy = target.position.y - tank.position.y

            if (dx > 4f) actions += PlayerAction.MoveRight
            if (dx < -4f) actions += PlayerAction.MoveLeft
            if (dy > 4f) actions += PlayerAction.MoveDown
            if (dy < -4f) actions += PlayerAction.MoveUp

            if (kotlin.math.abs(dx) < 10f || kotlin.math.abs(dy) < 10f) {
                actions += PlayerAction.FirePrimary
            }
        }

        return PlayerIntent(playerId = tank.playerId, activeActions = actions)
    }
}

object LastTankStandingModeRules : GameModeRules {
    override val id: String = "last_tank_standing"

    override fun onTick(state: GameState): GameState {
        val aliveTanks = state.entities.filterIsInstance<TankEntity>().filter { it.alive }
        val winner = if (aliveTanks.size == 1) aliveTanks.first().playerId else null
        return state.copy(winnerPlayerId = winner)
    }
}

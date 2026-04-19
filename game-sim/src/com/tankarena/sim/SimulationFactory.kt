package com.tankarena.sim

import com.tankarena.content.CanonicalMapDefinition
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.ObjectKinds
import com.tankarena.content.WeaponType
import com.tankarena.core.Int2
import com.tankarena.sim.runtime.SimulationHost

object SimulationFactory {
    private const val DEFAULT_TURRET_DELAY_TICKS = 75
    private const val DEFAULT_TURRET_DAMAGE = 20
    private const val DEFAULT_TURRET_RANGE_PIXELS = 8 * LEGACY_TILE_SIZE
    private const val INITIAL_ARMOR = 100
    private const val INITIAL_FUEL = 100
    private const val DEFAULT_GOAL_CONTRIBUTION = 25

    fun fromCanonicalMap(
        map: CanonicalMapDefinition,
        mode: MissionMode = MissionMode.SINGLE_PLAYER_VS_COMPUTER,
    ): TankArenaSimulation {
        val bounds = WorldBounds(
            widthPixels = map.metadata.widthTiles * LEGACY_TILE_SIZE,
            heightPixels = map.metadata.heightTiles * LEGACY_TILE_SIZE,
        )

        val playerStarts = map.objects
            .filter { it.kind == ObjectKinds.PLAYER_START }
            .sortedBy { it.id }

        val tanks = if (playerStarts.isNotEmpty()) {
            playerStarts.mapIndexed { index, start ->
                val facing = start.properties.directionToFacing()
                TankState(
                    id = index + 1L,
                    playerIndex = index,
                    tankType = start.properties["startType"]?.toIntOrNull() ?: index,
                    position = Int2(start.x, start.y),
                    facing = facing,
                    turretFacing = facing,
                    velocity = Int2(0, 0),
                    armor = INITIAL_ARMOR,
                    fuel = INITIAL_FUEL,
                    selectedWeapon = WeaponType.MAIN_CANNON,
                    lives = start.properties["lives"]?.toIntOrNull() ?: 3,
                )
            }
        } else {
            listOf(
                TankState(
                    id = 1,
                    playerIndex = 0,
                    tankType = 0,
                    position = Int2(bounds.widthPixels / 2, bounds.heightPixels / 2),
                    facing = Int2(0, -1),
                    turretFacing = Int2(0, -1),
                    velocity = Int2(0, 0),
                    armor = INITIAL_ARMOR,
                    fuel = INITIAL_FUEL,
                    selectedWeapon = WeaponType.MAIN_CANNON,
                ),
            )
        }

        val turrets = map.objects
            .filter { it.kind == ObjectKinds.TURRET }
            .mapIndexed { index, turret ->
                val delay = turret.properties["delay"]?.toIntOrNull()
                    ?.takeIf { it > 0 } ?: DEFAULT_TURRET_DELAY_TICKS
                val power = turret.properties["power"]?.toIntOrNull()
                    ?.takeIf { it > 0 } ?: DEFAULT_TURRET_DAMAGE
                val radius = turret.properties["radius"]?.toIntOrNull()
                    ?.takeIf { it > 0 }?.let { it * LEGACY_TILE_SIZE }
                    ?: DEFAULT_TURRET_RANGE_PIXELS
                TurretState(
                    id = 10_000L + index,
                    turretType = turret.properties["turretType"]?.toIntOrNull() ?: 0,
                    direction = turret.properties["direction"]?.toIntOrNull() ?: 0,
                    position = Int2(turret.x, turret.y),
                    cooldownTicks = delay,
                    fireDelayTicks = delay,
                    rangePixels = radius,
                    damage = power,
                )
            }

        val goals = map.objects
            .filter { it.kind == ObjectKinds.GOAL }
            .mapIndexed { index, goal ->
                val radius = goal.properties["radius"]?.toIntOrNull()
                    ?.takeIf { it > 0 } ?: LEGACY_TILE_SIZE
                val who = goal.properties["who"]?.toIntOrNull() ?: 0
                val contribution = goal.properties["goalContribution"]?.toIntOrNull()
                    ?.takeIf { it > 0 } ?: DEFAULT_GOAL_CONTRIBUTION
                GoalState(
                    id = 20_000L + index,
                    position = Int2(goal.x, goal.y),
                    radius = radius,
                    who = who,
                    contribution = contribution,
                )
            }

        val spawnPoints = tanks.associate { it.id to it.position }

        val host = SimulationHost(
            bounds = bounds,
            initialMode = mode,
            initialTanks = tanks,
            initialTurrets = turrets,
            initialGoals = goals,
            map = map,
            spawnPoints = spawnPoints,
        )
        return TankArenaSimulation(host = host)
    }
}

private fun Map<String, String>.directionToFacing(): Int2 {
    return when (this["direction"]?.toIntOrNull()) {
        0 -> Int2(0, -1)
        4 -> Int2(1, 0)
        8 -> Int2(0, 1)
        12 -> Int2(-1, 0)
        else -> Int2(0, -1)
    }
}

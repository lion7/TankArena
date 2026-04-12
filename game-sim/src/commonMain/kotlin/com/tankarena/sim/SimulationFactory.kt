package com.tankarena.sim

import com.tankarena.content.CanonicalMapDefinition
import com.tankarena.content.ObjectKinds
import com.tankarena.content.WeaponType
import com.tankarena.core.Int2

object SimulationFactory {
    private const val TILE_SIZE = 33

    fun fromCanonicalMap(map: CanonicalMapDefinition): TankArenaSimulation {
        val bounds = WorldBounds(
            widthPixels = map.metadata.widthTiles * TILE_SIZE,
            heightPixels = map.metadata.heightTiles * TILE_SIZE,
        )

        val playerStarts = map.objects
            .filter { it.kind == ObjectKinds.PLAYER_START }
            .sortedBy { it.id }

        val tanks = if (playerStarts.isNotEmpty()) {
            playerStarts.mapIndexed { index, start ->
                TankState(
                    id = index + 1L,
                    playerIndex = index,
                    tankType = start.properties["startType"]?.toIntOrNull() ?: index,
                    position = Int2(start.x, start.y),
                    facing = start.properties.directionToFacing(),
                    velocity = Int2(0, 0),
                    armor = 100,
                    fuel = 100,
                    selectedWeapon = WeaponType.MAIN_CANNON,
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
                    velocity = Int2(0, 0),
                    armor = 100,
                    fuel = 100,
                    selectedWeapon = WeaponType.MAIN_CANNON,
                ),
            )
        }

        val turrets = map.objects
            .filter { it.kind == ObjectKinds.TURRET }
            .mapIndexed { index, turret ->
                TurretState(
                    id = 10_000L + index,
                    turretType = turret.properties["turretType"]?.toIntOrNull() ?: 0,
                    direction = turret.properties["direction"]?.toIntOrNull() ?: 0,
                    position = Int2(turret.x, turret.y),
                    cooldownTicks = 0,
                )
            }

        return TankArenaSimulation(
            WorldState(
                bounds = bounds,
                tanks = tanks,
                turrets = turrets,
            )
        )
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

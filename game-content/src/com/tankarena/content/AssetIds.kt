package com.tankarena.content

import kotlinx.serialization.Serializable

@Serializable
enum class TankArenaWorld {
    DESERT,
    TEMPERATE,
    CITY,
    NIGHT,
}

@Serializable
enum class VehicleClass {
    CAR,
    TANK,
    CHOPPER,
    PLANE,
}

@Serializable
enum class WeaponType {
    MAIN_CANNON,
    CHAIN_GUN,
    FLAMETHROWER,
    MINE,
    ROCKET,
    MORTAR,
    A_BOMB,
    MEN_WITH_CHAIN_GUN,
    MEN_WITH_FLAMETHROWER,
    SMOKE_SCREEN,
    INVISIBILITY,
    EXTRA_SPEED,
    LIGHT,
}


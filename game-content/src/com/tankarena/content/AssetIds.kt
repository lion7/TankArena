package com.tankarena.content

import kotlinx.serialization.Serializable

@Serializable
enum class TankArenaWorld {
    DESERT,
    TEMPERATE,
    CITY,
    NIGHT,
}

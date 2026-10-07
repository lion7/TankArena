package com.tankarena.content

import kotlinx.serialization.Serializable

@Serializable
enum class GameModeCompatibility {
    DONT_CARE,
    DUAL,
    SINGLE,
    DUAL_VS_COMPUTER,
    SINGLE_OR_DUAL,
}

@Serializable
data class MapMetadata(
    val name: String,
    val widthTiles: Int,
    val heightTiles: Int,
    val missionCode: String,
    val nextMissionCode: String? = null,
    val randomBonus: Boolean = true,
    val world: TankArenaWorld = TankArenaWorld.DESERT,
    val night: Boolean = false,
    val publicPassword: Boolean = false,
    val modeCompatibility: GameModeCompatibility = GameModeCompatibility.DONT_CARE,
    val legacyMapVersion: Int = 0,
)

@Serializable
data class TileLayers(
    val base: List<Int>,
    val solid: List<Int>,
    val top: List<Int>,
    val goalLayer: List<Int>,
    val bonusLayer: List<Int>,
    val manTypeLayer: List<Int>,
    val manAmountLayer: List<Int>,
)

@Serializable
data class MissionText(
    val briefing: String = "",
    val success: String = "",
    val failure: String = "",
)

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
data class AuthoredObject(
    val id: String,
    val kind: String,
    val x: Int,
    val y: Int,
    val properties: Map<String, String> = emptyMap(),
)

object ObjectKinds {
    const val PLAYER_START = "player_start"
    const val TURRET = "turret"
    const val FLAG = "flag"
    const val GOAL = "goal"
    const val LOCK = "lock"
    const val WARP = "warp"
    const val PRODUCT = "product"
    const val DESTROYER = "destroyer"
    const val ENFORCER = "enforcer"
}

@Serializable
data class MissionText(
    val briefing: String = "",
    val success: String = "",
    val failure: String = "",
)

@Serializable
data class CanonicalMapDefinition(
    val metadata: MapMetadata,
    val layers: TileLayers,
    val missionText: MissionText,
    val objects: List<AuthoredObject>,
    val importNotes: List<String> = emptyList(),
)

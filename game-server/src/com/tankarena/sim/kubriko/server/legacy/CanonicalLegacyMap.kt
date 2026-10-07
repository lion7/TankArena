package com.tankarena.sim.kubriko.server.legacy

import com.tankarena.content.MapMetadata
import com.tankarena.content.MissionText
import com.tankarena.content.TileLayers
import kotlinx.serialization.Serializable

/**
 * Server-internal scaffolding for ingesting legacy `.MAP` files and constructing test
 * fixtures. The runtime client and editor consume scene JSON + [com.tankarena.content.MapSceneSidecar]
 * directly; this type is no longer part of any public module surface.
 */
@Serializable
data class CanonicalMapDefinition(
    val metadata: MapMetadata,
    val layers: TileLayers,
    val missionText: MissionText,
    val objects: List<AuthoredObject>,
    val importNotes: List<String> = emptyList(),
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
    const val AI_TANK = "ai_tank"
    const val B52 = "b52"
    const val MAN = "man"
    const val MINE = "mine"
    const val BONUS = "bonus"
    const val TRAIN = "train"
    const val WAGON = "wagon"
    const val ZEPPELIN = "zeppelin"
}

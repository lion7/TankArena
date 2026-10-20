package com.tankarena.sim

import kotlinx.serialization.Serializable

/**
 * Which rules a match plays by (ADR 0023). [CLASSIC] is the original game; [EXTENDED] is Classic plus
 * named, documented changes (none yet). Each ruleset has its own rules version, so recordings of one
 * are not invalidated by changes that only affect the other.
 */
@Serializable
enum class Ruleset {
    CLASSIC,
    EXTENDED,
    ;

    /**
     * The rules version recordings of this ruleset are played under. A change to the Classic rules
     * changes Extended too, so it bumps both; an Extended-only change bumps only Extended.
     */
    val rulesVersion: Int
        get() = when (this) {
            CLASSIC -> GAME_RULES_VERSION
            EXTENDED -> EXTENDED_RULES_VERSION
        }
}

/**
 * Rules version of the Extended ruleset; see [GAME_RULES_VERSION] for Classic.
 *
 * 1: Extended introduced; it plays exactly like Classic v11.
 * 2: Classic v12 (legacy steps).
 * 3: Classic v13 (the rest of Classic parity).
 */
const val EXTENDED_RULES_VERSION: Int = 3

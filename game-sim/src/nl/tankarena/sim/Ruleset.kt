package nl.tankarena.sim

import kotlinx.serialization.Serializable

/**
 * Which rules a match plays by (ADR 0023). [CLASSIC] is the original game; [EXTENDED] is Classic plus
 * named, documented changes ([RuleFlags]). Each ruleset has its own rules version, so recordings of one
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

    /** The rule changes this ruleset makes to the original's; Classic has none. */
    val flags: RuleFlags
        get() = when (this) {
            CLASSIC -> RuleFlags()
            EXTENDED -> RuleFlags(stackedComputerVehiclesSurvive = true, oneVehiclePerPlayer = true, teams = true, pointerMarker = true)
        }
}

/**
 * Named changes to the original's rules (ADR 0023). Each is off in Classic.
 */
data class RuleFlags(
    /**
     * Computer-driven vehicles that overlap do not destroy each other. In the original, computer tanks
     * placed on top of each other blow up at the start (FOREST, NIRVANA, NUKEBOMB, Q2, RACE_10,
     * SUBURBS; check_obj_overlap()).
     */
    val stackedComputerVehiclesSurvive: Boolean = false,
    /**
     * In player vs player each player gets one vehicle, at the first of their pads for it. The
     * original puts one on every such pad, all driven by the same keys (prepare_player()).
     */
    val oneVehiclePerPlayer: Boolean = false,
    /**
     * Up to four local players as two teams of up to two (ADR 0027): players 0 and 2 take the
     * original's player one's place (pads, flag, base, turrets sparing player one), 1 and 3 player
     * two's. A side is out of lives when all its players are.
     */
    val teams: Boolean = false,
    /**
     * The rocket marker goes where the player points (a mouse, Extended): while fire is held it
     * jumps to the pointed position, as far as its range allows. The keys steer it as before when
     * nothing is pointed at.
     */
    val pointerMarker: Boolean = false,
)

/**
 * Rules version of the Extended ruleset; see [GAME_RULES_VERSION] for Classic.
 *
 * 1: Extended introduced; it plays exactly like Classic v11.
 * 2: Classic v12 (legacy steps).
 * 3: Classic v13 (the rest of Classic parity).
 * 4: Classic v14 (rockets and mines can be hit).
 * 5: the first flags: stacked computer vehicles survive, one vehicle per player in player vs player.
 * 6: four players as two teams.
 * 7: the rocket marker follows the mouse.
 * 8: Classic v15 (hand-over once the destroyed vehicle is done dying).
 * 9: Classic v16 (destroyed structures without a ruined picture are gone).
 */
const val EXTENDED_RULES_VERSION: Int = 9

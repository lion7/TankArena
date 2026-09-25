package com.tankarena.sim.kubriko.server

import com.pandulapeter.kubriko.serialization.Serializable

/** The three ways the original game starts a map (`game.mode` in src/include/types.h). */
enum class MatchMode {
    /** One player drives the map tanks with id 1 (`src/game/single.c`). */
    SINGLE,

    /** Player vs player: tanks come from the start pads, human map tanks are removed (`src/game/dual.c`). */
    DUAL,

    /** Two players co-operate: player 1 drives id 1, player 2 drives id 2 (`src/game/dualvc.c`). */
    DUAL_VS_COMPUTER,
}

/**
 * Decides which tanks each human player drives once the scene is loaded. Scenes are mode
 * agnostic: human-drivable map tanks carry their legacy `contr.id` and dual start pads carry
 * their pad type, and this resolves them for the chosen [MatchMode] the way `prepare_tank()`
 * (src/tanks/tank.c) and `prepare_player()` (src/objects/player.c) do.
 *
 * A player drives a *group*: in single / co-operative play every human tank sharing the
 * selected id obeys that player's controls (`control_tank()` in src/tanks/control.c), and in
 * player-vs-player every start pad of the chosen vehicle kind spawns a tank for its player.
 *
 * Tanks that already carry a `playerIndex` (hand-built fixtures, older scenes) are left alone.
 */
internal object MatchSetup {

    fun assignControl(actors: List<Serializable<*>>, mode: MatchMode): List<Serializable<*>> {
        val tanks = actors.filterIsInstance<ServerTankActor>()
        val humanIds = tanks
            .filter { it.startPadType < 0 && !it.computer && it.controllerId >= 0 && it.playerIndex < 0 }
            .map { it.controllerId }
            .distinct()
            .sorted()
        val pads = tanks.filter { it.startPadType >= 0 }
        val prebound = tanks.filter { it.startPadType < 0 && it.playerIndex >= 0 }.map { it.playerIndex }.toSet()
        val players = if (mode == MatchMode.SINGLE) listOf(0) else listOf(0, 1)

        val idForPlayer = mutableMapOf<Int, Int>()
        if (mode != MatchMode.DUAL) {
            for (player in players) {
                if (player in prebound) continue
                val preferred = (player + 1).takeIf { it in humanIds && it !in idForPlayer.values }
                val chosen = preferred ?: humanIds.firstOrNull { it !in idForPlayer.values }
                if (chosen != null) idForPlayer[player] = chosen
            }
        }
        val padKindForPlayer = mutableMapOf<Int, Int>()
        for (player in players) {
            if (player in prebound || player in idForPlayer) continue
            // DUAL always spawns from pads; the co-operative modes only fall back to pads when the
            // map has no human-drivable tank for this player.
            pads.filter { (it.startPadType and 1) == player }
                .minOfOrNull { it.startPadType shr 1 }
                ?.let { padKindForPlayer[player] = it }
        }

        return actors.mapNotNull { actor ->
            if (actor !is ServerTankActor) return@mapNotNull actor
            if (actor.startPadType >= 0) {
                val player = actor.startPadType and 1
                return@mapNotNull actor.takeIf { padKindForPlayer[player] == actor.startPadType shr 1 }
            }
            if (actor.playerIndex >= 0 || actor.computer || actor.controllerId < 0) return@mapNotNull actor
            // Legacy prepare_tank(): human map tanks are removed in player-vs-player.
            if (mode == MatchMode.DUAL) return@mapNotNull null
            val player = idForPlayer.entries.firstOrNull { it.value == actor.controllerId }?.key
            if (player != null) actor.withPlayerIndex(player) else actor
        }
    }

    private fun ServerTankActor.withPlayerIndex(index: Int): ServerTankActor =
        ServerTankActor(save().copy(playerIndex = index))
}

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

/** Player vs player (legacy dual_game(), src/game/dual.c): what the players chose for this round. */
@kotlinx.serialization.Serializable
data class DualSetup(
    /** Legacy `pl_tanks`: the vehicle each player picked on the selection screen (index into tanks.c). */
    val tankTypes: List<Int> = listOf(0, 0),
    /** Legacy `race_laps`. */
    val raceLaps: Int = DEFAULT_RACE_LAPS,
    /** Legacy `game.round + 1` and `game.wins`: shown to both players as the round starts. */
    val round: Int = 1,
    val wins: List<Int> = listOf(0, 0),
) {
    companion object {
        const val DEFAULT_RACE_LAPS = 3
        /** Legacy make_tank(o, 6, ...): race pads always spawn the race tank. */
        const val RACE_TANK = 6
        /** Start pad kinds (`type >> 1`): tank, helicopter, plane, race. */
        const val PAD_RACE = 3
    }
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

    fun assignControl(actors: List<Serializable<*>>, mode: MatchMode, dual: DualSetup = DualSetup()): List<Serializable<*>> {
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
            if (player in prebound || player in idForPlayer || mode == MatchMode.DUAL) continue
            // The co-operative modes only fall back to pads when the map has no human-drivable
            // tank for this player.
            pads.filter { (it.startPadType and 1) == player }
                .minOfOrNull { it.startPadType shr 1 }
                ?.let { padKindForPlayer[player] = it }
        }

        return actors.mapNotNull { actor ->
            if (actor !is ServerTankActor) return@mapNotNull actor
            if (actor.startPadType >= 0) {
                val player = actor.startPadType and 1
                val kind = actor.startPadType shr 1
                if (mode == MatchMode.DUAL) {
                    // Legacy prepare_player(): race pads always race; the others only spawn when
                    // they are for the kind of vehicle the player picked.
                    if (kind == DualSetup.PAD_RACE) return@mapNotNull actor.asVehicle(DualSetup.RACE_TANK)
                    val chosen = dual.tankTypes.getOrElse(player) { 0 }
                    return@mapNotNull actor.asVehicle(chosen).takeIf { padKind(chosen) == kind }
                }
                return@mapNotNull actor.takeIf { padKindForPlayer[player] == kind }
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

    /** Legacy prepare_player() pad types: 0 tanks (and cars), 1 helicopters, 2 planes. */
    private fun padKind(tankType: Int): Int = when (LegacyVehicles.spec(tankType).kind) {
        VehicleKind.CHOPPER -> 1
        VehicleKind.PLANE -> 2
        else -> 0
    }

    /** Legacy make_tank(o, pl_tanks[c], ...): a pad spawns the chosen vehicle with its own loadout. */
    private fun ServerTankActor.asVehicle(tankType: Int): ServerTankActor {
        val spec = LegacyVehicles.spec(tankType)
        return ServerTankActor(
            save().copy(
                tankType = tankType,
                armor = spec.armor,
                maxArmor = spec.armor,
                fuel = spec.maxFuel,
                maxFuel = spec.maxFuel,
                ammo = spec.ammo,
                power = spec.power,
                currentWeapon = LegacyVehicles.startingWeapon(spec, spec.ammo),
            ),
        )
    }
}

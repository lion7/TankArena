package com.tankarena.sim


/** The three ways the original game starts a map (`game.mode` in src/include/types.h). */
enum class MatchMode {
    /** One player drives the map tanks with id 1 (`src/game/single.c`). */
    SINGLE,

    /** Player vs player: tanks come from the start pads, human map tanks are removed (`src/game/dual.c`). */
    DUAL,

    /** Two players co-operate: player 1 drives id 1, player 2 drives id 2 (`src/game/dualvc.c`). */
    DUAL_VS_COMPUTER,
    ;

    /** The original's number of players in this mode. */
    val defaultPlayers: Int get() = if (this == SINGLE) 1 else 2
}

/** A player's side (ADR 0027): 0 the original's player one (players 0 and 2), 1 player two (1 and 3); -1 none. */
internal val Int.side: Int get() = if (this < 0) -1 else this and 1

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

    fun assignControl(
        actors: List<SceneObject<*>>,
        mode: MatchMode,
        dual: DualSetup = DualSetup(),
        flags: RuleFlags = RuleFlags(),
        playerCount: Int = mode.defaultPlayers,
    ): List<SceneObject<*>> {
        if (playerCount > 2) return assignTeams(actors, mode, dual, playerCount)
        val tanks = actors.filterIsInstance<Tank>()
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

        // Extended: one vehicle per player, at the first of their pads that spawns one.
        val playersWithVehicle = mutableSetOf<Int>()
        fun firstForPlayer(player: Int, tank: Tank?): Tank? =
            if (tank == null || !flags.oneVehiclePerPlayer || playersWithVehicle.add(player)) tank else null
        return actors.mapNotNull { actor ->
            if (actor !is Tank) return@mapNotNull actor
            if (actor.startPadType >= 0) {
                val player = actor.startPadType and 1
                val kind = actor.startPadType shr 1
                if (mode == MatchMode.DUAL) {
                    // Legacy prepare_player(): race pads always race; the others only spawn when
                    // they are for the kind of vehicle the player picked.
                    if (kind == DualSetup.PAD_RACE) return@mapNotNull firstForPlayer(player, actor.asVehicle(DualSetup.RACE_TANK))
                    val chosen = dual.tankTypes.getOrElse(player) { 0 }
                    return@mapNotNull firstForPlayer(player, actor.asVehicle(chosen).takeIf { padKind(chosen) == kind })
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

    /**
     * ADR 0027: three or four players as two teams. Player vs player: each player gets the first
     * free pad of their side for the vehicle they chose (a race pad for a race), or shares one when
     * there are not enough. Against the computer every player is on the good side: each takes a
     * human tank of the map (id player + 1 first), and who finds none gets a copy of a team mate's.
     * Players on the same spot are spread out when the match starts ([Match.spreadPlayers]).
     */
    private fun assignTeams(actors: List<SceneObject<*>>, mode: MatchMode, dual: DualSetup, playerCount: Int): List<SceneObject<*>> {
        val tanks = actors.filterIsInstance<Tank>()
        val pads = tanks.filter { it.startPadType >= 0 }
        if (mode == MatchMode.DUAL) {
            val others = actors.filter { it !is Tank || it.computer && it.startPadType < 0 }
            val used = mutableSetOf<Tank>()
            val vehicles = (0 until playerCount).mapNotNull { player ->
                val side = player.side
                val chosen = dual.tankTypes.getOrElse(player) { 0 }
                val own = pads.filter { (it.startPadType and 1) == side }
                val race = own.filter { it.startPadType shr 1 == DualSetup.PAD_RACE }
                val fitting = race.ifEmpty { own.filter { it.startPadType shr 1 == padKind(chosen) } }
                val pad = fitting.firstOrNull { it !in used } ?: fitting.firstOrNull() ?: return@mapNotNull null
                used += pad
                val type = if (race.isNotEmpty()) DualSetup.RACE_TANK else chosen
                Tank(pad.asVehicle(type).save().copy(playerIndex = player))
            }
            return others + vehicles
        }
        val humanIds = tanks.filter { it.startPadType < 0 && !it.computer && it.controllerId >= 0 }.map { it.controllerId }.distinct().sorted()
        val idForPlayer = mutableMapOf<Int, Int>()
        for (player in 0 until playerCount) {
            val preferred = (player + 1).takeIf { it in humanIds && it !in idForPlayer.values }
            (preferred ?: humanIds.firstOrNull { it !in idForPlayer.values })?.let { idForPlayer[player] = it }
        }
        val assigned = actors.mapNotNull { actor ->
            if (actor !is Tank) return@mapNotNull actor
            if (actor.startPadType >= 0) return@mapNotNull null
            if (actor.computer || actor.controllerId < 0) return@mapNotNull actor
            val player = idForPlayer.entries.firstOrNull { it.value == actor.controllerId }?.key
            if (player != null) actor.withPlayerIndex(player) else actor
        }
        // Players without a tank of their own drive a copy of a team mate's (or anyone's).
        val extra = (0 until playerCount).filter { it !in idForPlayer }.mapNotNull { player ->
            val mate = assigned.filterIsInstance<Tank>().filter { it.playerIndex >= 0 }
                .minByOrNull { if (it.playerIndex.side == player.side) 0 else 1 } ?: return@mapNotNull null
            Tank(mate.save().copy(playerIndex = player, controllerId = -1, ref = 0, goalContribution = 0))
        }
        return assigned + extra
    }

    private fun Tank.withPlayerIndex(index: Int): Tank =
        Tank(save().copy(playerIndex = index))

    /** Legacy prepare_player() pad types: 0 tanks (and cars), 1 helicopters, 2 planes. */
    private fun padKind(tankType: Int): Int = when (LegacyVehicles.spec(tankType).kind) {
        VehicleKind.CHOPPER -> 1
        VehicleKind.PLANE -> 2
        else -> 0
    }

    /**
     * Legacy make_tank(o, pl_tanks[c], ...): a pad spawns the chosen vehicle with its own loadout,
     * or with the pad's ammo when the pad has one (control_player(), `pl.weap.active`).
     */
    private fun Tank.asVehicle(tankType: Int): Tank {
        val spec = LegacyVehicles.spec(tankType)
        val ammo = padAmmo ?: spec.ammo
        return Tank(
            save().copy(
                tankType = tankType,
                armor = spec.armor,
                maxArmor = spec.armor,
                fuel = spec.maxFuel,
                maxFuel = spec.maxFuel,
                ammo = ammo,
                power = spec.power,
                currentWeapon = LegacyVehicles.startingWeapon(spec, ammo),
            ),
        )
    }
}

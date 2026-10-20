package com.tankarena.sim

import kotlinx.serialization.Serializable

/**
 * The original's command-line cheats (src/game/main.c:52-97, cheat.txt). They change how the game
 * plays, so recordings keep them.
 */
@Serializable
data class Cheats(
    /** `-GOD`: good vehicles take no hits except falling or crashing (HT_DESTROY); humans burn no fuel. */
    val god: Boolean = false,
    /** `-NOFAIL`: nothing counts against the mission. */
    val noFail: Boolean = false,
    /** `-LOADSOFWEAPONS`: shooting uses no ammo and the flamer no fuel. */
    val loadsOfWeapons: Boolean = false,
    /** `-NOLOCKLIMIT`: the rocket marker goes as far as it likes. */
    val noLockLimit: Boolean = false,
) {
    companion object {
        /** The switches among [args], spelt `-GOD` like the original or `--god`, in any case. */
        fun fromArgs(args: Array<String>): Cheats {
            val switches = args.map { it.trimStart('-').uppercase() }.toSet()
            return Cheats(
                god = "GOD" in switches,
                noFail = "NOFAIL" in switches,
                loadsOfWeapons = "LOADSOFWEAPONS" in switches,
                noLockLimit = "NOLOCKLIMIT" in switches,
            )
        }
    }
}

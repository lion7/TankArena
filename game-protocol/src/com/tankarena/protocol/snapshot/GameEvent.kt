package com.tankarena.protocol.snapshot

import kotlinx.serialization.Serializable

@Serializable
enum class ExplosionKind { MINE, MORTAR, ROCKET, ABOMB }

/**
 * Sound sample identifiers matching legacy Tank Arena sound system.
 * See docs/game/mechanics.md §"Sound System" for trigger conditions.
 */
@Serializable
enum class SoundKind {
    /** Main bullet hit */
    MAIN,
    /** Chain gun hit */
    CHAIN,
    /** Flamethrower hit */
    FLAME,
    /** Small rocket hit */
    SROCKET,
    /** Mortar impact */
    MORTAR,
    /** Rocket launch */
    ROCKET,
    /** Empty weapon (click) */
    EMPTY,
    /** Smoke screen deploy */
    SMOKESCR,
    /** Invisibility activate */
    INVISIBLE,
    /** Speed boost activate */
    SPEEDUP,
    /** Light pickup */
    LIGHT,
    /** Tank destroyed */
    EXPLODE,
    /** Vehicle crash */
    CRASH,
    /** Water impact (splash) */
    SPLASH,
    /** Mine deploy */
    MINE,
    /** Bonus/product pickup */
    BONUS,
    /** A building collapses */
    COLLAPSE,
    /** An A-bomb goes off */
    NUKE,
    /** A soldier dies */
    SCREAM,
    /** A rocket is coming for this vehicle */
    INCOMING,
    /** Legacy SND_BEEP: a race checkpoint passed. */
    BEEP,
}

/**
 * Legacy effect animations (`eff[]` in src/world/effects.c): a sequence of pictures, each shown
 * for its own number of ticks. Only the kinds the server emits are listed.
 */
@Serializable
enum class EffectKind {
    /** SMOKE1..2: exhaust puffs of a moving vehicle. */
    SMOKE,
    /** RUIN1..4: debris of a hit structure. */
    RUIN,
    /** FIRE1..3: main-gun muzzle flash. */
    FIRE,
    /** EXP1..5: hit or burning explosion. */
    EXPLOSION,
    /** CHAIN1..3: a chain-gun round hitting something. */
    CHAIN,
    /** FLAME1..5: a flame burning on what it hit. */
    FLAME,
    /** PLUF1..2: a chain-gun round that runs out of range. */
    PLUF,
    /** SPLASH1..3: water splash. */
    SPLASH,
    /** SFIRE: chain-gun muzzle flash. */
    SFIRE,
    /** ROOK1..5: smoke rising from a wreck. */
    ROOK,
    /** IXP1..5: large explosion (rocket). */
    LEXPLOSION,
    /** MFIRE1..3: mortar muzzle flash. */
    MORTAR_FIRE,
    /** LAVA1..4: lava bubbling under a vehicle. */
    LAVA,
    /** OLIE1..3: an oil tank bursting. */
    OIL_SPLASH,
    /** SMOKES1..6: a long-lived cloud of the smoke-screen weapon. */
    SMOKE_SCREEN,
    /** BLOOD1..3: a soldier killed. */
    BLOOD,
    /** EXHAUST2: the smoke trail of a small rocket. */
    EXHAUST,
}

@Serializable
sealed interface GameEvent {
    /** A purely visual legacy effect (`create_effect()`) at world position ([x], [y]). */
    @Serializable
    data class Effect(val kind: EffectKind, val x: Int, val y: Int) : GameEvent

    @Serializable
    data class Explosion(
        val x: Int,
        val y: Int,
        val radius: Int,
        val kind: ExplosionKind,
    ) : GameEvent

    @Serializable
    data class TankDestroyed(val actorId: Long) : GameEvent

    @Serializable
    data class TankSpawned(val actorId: Long) : GameEvent

    @Serializable
    data class TurretDestroyed(val actorId: Long) : GameEvent

    /** Control of [playerId] moved to another tank (death hand-over or F1–F10 tank switch). */
    @Serializable
    data class ControlSwitched(val playerId: Int, val actorId: Long) : GameEvent

    @Serializable
    data class DamageTaken(val actorId: Long, val amount: Int) : GameEvent

    /** Player vs player: [playerId] won the round (legacy `game.winner`). */
    @Serializable
    data class RoundWon(val playerId: Int) : GameEvent

    @Serializable
    data class MissionWon(val playerId: Int, val reason: String = "") : GameEvent

    @Serializable
    data class MissionLost(val playerId: Int, val reason: String = "") : GameEvent

    /**
     * End-of-mission result payload. Emitted once when the mission concludes.
     * @param won Whether the mission was won
     * @param reason Human-readable reason (e.g. "GOALS_CAPTURED", "ALL_ENEMIES_ELIMINATED")
     * @param score Total score accumulated
     * @param kills Number of enemy tanks/turrets destroyed
     * @param captures Number of goals captured
     * @param ticks Number of ticks the mission lasted
     */
    @Serializable
    data class MissionResult(
        val won: Boolean,
        val reason: String = "",
        val score: Int = 0,
        val kills: Int = 0,
        val captures: Int = 0,
        val ticks: Long = 0,
    ) : GameEvent

    /**
     * Audio event for distance-attenuated stereo playback.
     * @param kind The sound sample to play
     * @param x World X coordinate of the sound source
     * @param y World Y coordinate of the sound source
     */
    @Serializable
    data class Sound(val kind: SoundKind, val x: Int, val y: Int) : GameEvent

    /**
     * Emitted when a tank picks up a product (supermarket mode).
     * @param productActorId The product that was collected
     * @param tankActorId The tank that picked it up
     * @param price The cash cost deducted
     */
    @Serializable
    data class ProductCollected(val productActorId: Long, val tankActorId: Long, val price: Int) : GameEvent
}

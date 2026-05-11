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
}

@Serializable
sealed interface GameEvent {
    @Serializable
    data class Fired(val actorId: Long, val x: Int, val y: Int) : GameEvent

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
    data class DamageTaken(val actorId: Long, val amount: Int) : GameEvent

    @Serializable
    data class MissionWon(val playerId: Int) : GameEvent

    @Serializable
    data class MissionLost(val playerId: Int) : GameEvent

    /**
     * Audio event for distance-attenuated stereo playback.
     * @param kind The sound sample to play
     * @param x World X coordinate of the sound source
     * @param y World Y coordinate of the sound source
     */
    @Serializable
    data class Sound(val kind: SoundKind, val x: Int, val y: Int) : GameEvent

    /**
     * Emitted when a flag is captured (picked up) by a tank.
     * @param flagActorId The flag actor that was captured
     * @param tankActorId The tank that picked up the flag
     */
    @Serializable
    data class FlagCaptured(val flagActorId: Long, val tankActorId: Long) : GameEvent

    /**
     * Emitted when a flag is returned to its home position (carrier destroyed).
     * @param flagActorId The flag actor that was returned
     */
    @Serializable
    data class FlagReturned(val flagActorId: Long) : GameEvent

    /**
     * Emitted when an enemy carries a flag into the base zone — mission win condition.
     * @param flagActorId The flag actor involved
     * @param tankActorId The enemy tank that delivered the flag
     */
    @Serializable
    data class FlagDelivered(val flagActorId: Long, val tankActorId: Long) : GameEvent
}

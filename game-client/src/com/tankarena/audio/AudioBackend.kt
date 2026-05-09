package com.tankarena.audio

import com.tankarena.protocol.snapshot.GameEvent

/**
 * Pluggable audio backend interface for Tank Arena sound playback.
 * 
 * The server emits [GameEvent.Sound] events with world coordinates.
 * The client routes these to the backend with computed volume/pan values
 * based on distance from the player camera.
 * 
 * See docs/game/mechanics.md §"Sound System" for the audio formulas:
 * - Max range: 612 pixels
 * - Volume: full at d<100, linear falloff 100-612, silent at d>=612
 * - Pan: stereo panning based on relative player distances (dual mode)
 * - Pitch: 900-1100 variation
 * - Silencing: volume <= 32 is muted
 */
interface AudioBackend {
    /**
     * Play a sound with the given parameters.
     *
     * @param kind The sound sample identifier
     * @param volume Computed volume 0-255 (0 = silent, 255 = full)
     * @param pan Stereo pan position 0-255 (128 = center, 0 = full left, 255 = full right)
     * @param pitch Pitch modifier 900-1100 (1000 = normal)
     */
    fun play(kind: SoundKind, volume: Int, pan: Int, pitch: Int)

    /** Stop all currently playing sounds. */
    fun stopAll()

    /** Shut down the audio backend and release resources. */
    fun dispose()
}

/** Sound kind enum mirroring the protocol SoundKind for client-side use. */
enum class SoundKind {
    MAIN, CHAIN, FLAME, SROCKET, MORTAR, ROCKET, EMPTY, SMOKESCR,
    INVISIBLE, SPEEDUP, LIGHT, EXPLODE, CRASH, SPLASH, MINE, BONUS,
}

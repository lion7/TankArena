package com.tankarena.audio

import com.tankarena.protocol.snapshot.GameEvent
import com.tankarena.protocol.snapshot.PlayerView

/**
 * Audio manager that processes [GameEvent.Sound] events and routes them to an [AudioBackend].
 * 
 * Computes distance-based volume attenuation and stereo panning based on the player's
 * camera position. Handles both single-player and dual-player (split-screen) modes.
 * 
 * Per docs/game/mechanics.md §"Sound System":
 * - Max range: 612 pixels
 * - Volume: full at d<100, linear falloff 100-612, silent at d>=612
 * - Pan: stereo panning based on relative player distances (dual mode)
 * - Pitch: 900-1100 variation
 * - Silencing: volume <= 32 is muted
 */
class AudioManager(
    private val backend: AudioBackend,
) {
    companion object {
        private const val MAX_RANGE = 612
        private const val NEAR_FIELD = 100
    }

    private var player1View: PlayerView? = null
    private var player2View: PlayerView? = null
    private val random = java.util.Random()

    /**
     * Process a list of game events, routing sound events to the audio backend.
     * 
     * @param events List of game events from the server
     * @param playerViews Per-player views containing camera positions
     */
    fun processEvents(events: List<GameEvent>, playerViews: List<PlayerView>) {
        // Update player views for distance calculations
        player1View = playerViews.firstOrNull { it.playerId == 0 }
        player2View = playerViews.firstOrNull { it.playerId == 1 }

        for (event in events) {
            if (event is GameEvent.Sound) {
                handleSoundEvent(event)
            }
        }
    }

    private fun handleSoundEvent(event: GameEvent.Sound) {
        val soundX = event.x
        val soundY = event.y

        // Calculate distances to each player's camera
        val d1 = if (player1View != null) {
            distanceToCamera(soundX, soundY, player1View!!)
        } else {
            Float.POSITIVE_INFINITY
        }

        val d2 = if (player2View != null) {
            distanceToCamera(soundX, soundY, player2View!!)
        } else {
            Float.POSITIVE_INFINITY
        }

        // Use minimum distance for volume calculation
        val minDistance = minOf(d1, d2)
        val volume = computeVolume(minDistance)

        // Calculate pan position
        val cameraX = player1View?.cameraCenterX ?: soundX
        val pan = computePan(d1, d2, soundX, cameraX)

        // Calculate pitch variation
        val pitch = computePitch()

        // Convert protocol SoundKind to client SoundKind
        val clientKind = convertSoundKind(event.kind)

        // Play the sound
        backend.play(clientKind, volume, pan, pitch)
    }

    /**
     * Compute volume based on distance from sound source to listener.
     * Per legacy formula: full volume at d<100, linear falloff to 0 at d>=612.
     */
    private fun computeVolume(distance: Float): Int {
        if (distance < NEAR_FIELD) return 255
        if (distance >= MAX_RANGE) return 0
        return ((MAX_RANGE - distance) / 2).toInt().coerceIn(0, 255)
    }

    /**
     * Compute stereo pan position based on distances to two players.
     * For single player, uses sound position relative to camera center.
     */
    private fun computePan(d1: Float, d2: Float, soundX: Int, cameraX: Int): Int {
        // Single player mode: pan based on horizontal offset from camera
        if (d2 == Float.POSITIVE_INFINITY) {
            val offset = soundX - cameraX
            val normalized = (offset / MAX_RANGE.toFloat()).coerceIn(-1f, 1f)
            return ((1f - normalized) * 128).toInt().coerceIn(0, 255)
        }
        
        // Dual player mode: legacy formula
        val pan = -(MAX_RANGE - d1) / 4 + (MAX_RANGE - d2) / 4 + 128
        return pan.toInt().coerceIn(0, 255)
    }

    /**
     * Compute pitch variation: 900-1100 range.
     */
    private fun computePitch(): Int {
        return 900 + random.nextInt(201)
    }

    private fun distanceToCamera(soundX: Int, soundY: Int, view: PlayerView): Float {
        val dx = (soundX - view.cameraCenterX).toFloat()
        val dy = (soundY - view.cameraCenterY).toFloat()
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun convertSoundKind(protocolKind: com.tankarena.protocol.snapshot.SoundKind): SoundKind {
        return when (protocolKind) {
            com.tankarena.protocol.snapshot.SoundKind.MAIN -> SoundKind.MAIN
            com.tankarena.protocol.snapshot.SoundKind.CHAIN -> SoundKind.CHAIN
            com.tankarena.protocol.snapshot.SoundKind.FLAME -> SoundKind.FLAME
            com.tankarena.protocol.snapshot.SoundKind.SROCKET -> SoundKind.SROCKET
            com.tankarena.protocol.snapshot.SoundKind.MORTAR -> SoundKind.MORTAR
            com.tankarena.protocol.snapshot.SoundKind.ROCKET -> SoundKind.ROCKET
            com.tankarena.protocol.snapshot.SoundKind.EMPTY -> SoundKind.EMPTY
            com.tankarena.protocol.snapshot.SoundKind.SMOKESCR -> SoundKind.SMOKESCR
            com.tankarena.protocol.snapshot.SoundKind.INVISIBLE -> SoundKind.INVISIBLE
            com.tankarena.protocol.snapshot.SoundKind.SPEEDUP -> SoundKind.SPEEDUP
            com.tankarena.protocol.snapshot.SoundKind.LIGHT -> SoundKind.LIGHT
            com.tankarena.protocol.snapshot.SoundKind.EXPLODE -> SoundKind.EXPLODE
            com.tankarena.protocol.snapshot.SoundKind.CRASH -> SoundKind.CRASH
            com.tankarena.protocol.snapshot.SoundKind.SPLASH -> SoundKind.SPLASH
            com.tankarena.protocol.snapshot.SoundKind.MINE -> SoundKind.MINE
            com.tankarena.protocol.snapshot.SoundKind.BONUS -> SoundKind.BONUS
        }
    }

    fun dispose() {
        backend.dispose()
    }
}

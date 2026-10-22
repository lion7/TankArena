package nl.tankarena.audio

import nl.tankarena.protocol.snapshot.GameEvent
import nl.tankarena.protocol.snapshot.PlayerView

/**
 * Audio manager that processes [GameEvent.Sound] events and routes them to an [AudioBackend].
 * 
 * Computes distance-based volume attenuation and stereo panning based on the player's
 * camera position. Handles both single-player and dual-player (split-screen) modes.
 * 
 * Per original/docs/mechanics.md §"Sound System":
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
        private const val MAX_RANGE = 612f
        private const val NEAR_FIELD = 100f
        /** Legacy create_sound_absolute(): `if (vol > 32)`. */
        private const val MIN_VOLUME = 32
    }

    /** The game options' sound volume (legacy `sound_volume`), 0..255, over every sound. */
    var soundVolume: Int
        get() = backend.masterVolume
        set(value) {
            backend.masterVolume = value
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
        player1View = playerViews.firstOrNull { it.playerId == 0 }
        player2View = playerViews.firstOrNull { it.playerId == 1 }
        twoPlayers = playerViews.size > 1

        for (event in events) {
            if (event is GameEvent.Sound) {
                handleSoundEvent(event)
            }
        }
    }

    private var twoPlayers = false

    /**
     * Legacy create_sound_absolute() (original/src/hardware/sound.c): the distance to the player's vehicle
     * (the nearer of two), at most 612 (also without a vehicle), sets the volume; with two players the
     * difference pans the sound; quieter than 33 it is not played.
     */
    private fun handleSoundEvent(event: GameEvent.Sound) {
        val d1 = distanceToVehicle(event.x, event.y, player1View)
        val d2 = distanceToVehicle(event.x, event.y, player2View)
        val volume = computeVolume(if (twoPlayers) minOf(d1, d2) else d1)
        if (volume <= MIN_VOLUME) return
        val pan = if (twoPlayers) (-(MAX_RANGE - d1) / 4 + (MAX_RANGE - d2) / 4 + 128).toInt().coerceIn(0, 255) else 128
        backend.play(convertSoundKind(event.kind), volume, pan, computePitch())
    }

    /** Legacy: full volume nearer than 100, then (612 - d) / 2. */
    private fun computeVolume(distance: Float): Int {
        if (distance < NEAR_FIELD) return 255
        if (distance >= MAX_RANGE) return 0
        return ((MAX_RANGE - distance) / 2).toInt().coerceIn(0, 255)
    }

    /** Legacy `900 + rnd(200)`. */
    private fun computePitch(): Int = 900 + random.nextInt(200)

    private fun distanceToVehicle(soundX: Int, soundY: Int, view: PlayerView?): Float {
        if (view == null || view.controlledActorId == null) return MAX_RANGE
        val dx = (soundX - view.viewerX).toFloat()
        val dy = (soundY - view.viewerY).toFloat()
        return kotlin.math.sqrt(dx * dx + dy * dy).coerceAtMost(MAX_RANGE)
    }

    private fun convertSoundKind(protocolKind: nl.tankarena.protocol.snapshot.SoundKind): SoundKind {
        return when (protocolKind) {
            nl.tankarena.protocol.snapshot.SoundKind.MAIN -> SoundKind.MAIN
            nl.tankarena.protocol.snapshot.SoundKind.CHAIN -> SoundKind.CHAIN
            nl.tankarena.protocol.snapshot.SoundKind.FLAME -> SoundKind.FLAME
            nl.tankarena.protocol.snapshot.SoundKind.SROCKET -> SoundKind.SROCKET
            nl.tankarena.protocol.snapshot.SoundKind.MORTAR -> SoundKind.MORTAR
            nl.tankarena.protocol.snapshot.SoundKind.ROCKET -> SoundKind.ROCKET
            nl.tankarena.protocol.snapshot.SoundKind.EMPTY -> SoundKind.EMPTY
            nl.tankarena.protocol.snapshot.SoundKind.SMOKESCR -> SoundKind.SMOKESCR
            nl.tankarena.protocol.snapshot.SoundKind.INVISIBLE -> SoundKind.INVISIBLE
            nl.tankarena.protocol.snapshot.SoundKind.SPEEDUP -> SoundKind.SPEEDUP
            nl.tankarena.protocol.snapshot.SoundKind.LIGHT -> SoundKind.LIGHT
            nl.tankarena.protocol.snapshot.SoundKind.EXPLODE -> SoundKind.EXPLODE
            nl.tankarena.protocol.snapshot.SoundKind.CRASH -> SoundKind.CRASH
            nl.tankarena.protocol.snapshot.SoundKind.SPLASH -> SoundKind.SPLASH
            nl.tankarena.protocol.snapshot.SoundKind.MINE -> SoundKind.MINE
            nl.tankarena.protocol.snapshot.SoundKind.BONUS -> SoundKind.BONUS
            nl.tankarena.protocol.snapshot.SoundKind.COLLAPSE -> SoundKind.COLLAPSE
            nl.tankarena.protocol.snapshot.SoundKind.NUKE -> SoundKind.NUKE
            nl.tankarena.protocol.snapshot.SoundKind.SCREAM -> SoundKind.SCREAM
            nl.tankarena.protocol.snapshot.SoundKind.INCOMING -> SoundKind.INCOMING
            nl.tankarena.protocol.snapshot.SoundKind.BEEP -> SoundKind.BEEP
            nl.tankarena.protocol.snapshot.SoundKind.TELEPORT -> SoundKind.TELEPORT
            nl.tankarena.protocol.snapshot.SoundKind.RICOCHET1 -> SoundKind.RICOCHET1
            nl.tankarena.protocol.snapshot.SoundKind.RICOCHET2 -> SoundKind.RICOCHET2
            nl.tankarena.protocol.snapshot.SoundKind.RICOCHET3 -> SoundKind.RICOCHET3
            nl.tankarena.protocol.snapshot.SoundKind.RICOCHET4 -> SoundKind.RICOCHET4
            nl.tankarena.protocol.snapshot.SoundKind.RICOCHET5 -> SoundKind.RICOCHET5
            nl.tankarena.protocol.snapshot.SoundKind.RICOCHET6 -> SoundKind.RICOCHET6
        }
    }

    fun dispose() {
        backend.dispose()
    }
}

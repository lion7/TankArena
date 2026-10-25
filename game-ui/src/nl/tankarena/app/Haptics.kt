package nl.tankarena.app

import nl.tankarena.protocol.snapshot.GameEvent
import nl.tankarena.protocol.snapshot.ServerFrame
import nl.tankarena.protocol.snapshot.TankState

/** A hit, the vehicle lost, a shot: how long (ms) and how hard (1..255) each buzzes. */
private const val HIT_MILLIS = 45
private const val HIT_STRENGTH = 200
private const val LOST_MILLIS = 180
private const val LOST_STRENGTH = 255
private const val SHOT_MILLIS = 12
private const val SHOT_STRENGTH = 90

/** Buzzes come at most this often, so a machine gun or a fire does not buzz without end. */
private const val GAP_NANOS = 120_000_000L

/**
 * Extended on a touch screen: player one feels their shots and the hits on their vehicle through
 * [vibrate] (GamePlatform.vibrate). Run on the simulation thread with every new frame.
 */
internal class Haptics(private val vibrate: (millis: Int, strength: Int) -> Unit) {
    @Volatile
    var enabled = false

    private var lastCooldown = 0
    private var lastBuzz = 0L

    fun step(frame: ServerFrame, playerId: Int) {
        if (!enabled) return
        val actorId = frame.playerViews.firstOrNull { it.playerId == playerId }?.controlledActorId ?: return
        val tank = frame.world.actors.firstOrNull { it is TankState && it.actorId == actorId } as TankState?
        // A shot: the gun's cooldown starts again.
        val cooldown = tank?.primaryCooldownTicks ?: 0
        val shot = cooldown > lastCooldown
        lastCooldown = cooldown
        val (millis, strength) = when {
            frame.world.events.any { it is GameEvent.TankDestroyed && it.actorId == actorId } -> LOST_MILLIS to LOST_STRENGTH
            frame.world.events.any { it is GameEvent.DamageTaken && it.actorId == actorId } -> HIT_MILLIS to HIT_STRENGTH
            shot -> SHOT_MILLIS to SHOT_STRENGTH
            else -> return
        }
        val now = System.nanoTime()
        if (now - lastBuzz < GAP_NANOS && millis != LOST_MILLIS) return
        lastBuzz = now
        vibrate(millis, strength)
    }
}

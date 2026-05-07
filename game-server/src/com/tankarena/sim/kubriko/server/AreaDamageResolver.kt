package com.tankarena.sim.kubriko.server

import com.tankarena.protocol.snapshot.ExplosionKind
import com.tankarena.protocol.snapshot.GameEvent

/**
 * Single resolver for every weapon that does area-of-effect damage on detonation
 * (mines, mortars, rockets, A-bombs). Damage falls off linearly from full at the
 * impact center to zero at the edge of the radius. Returns the [GameEvent.Explosion]
 * the caller should publish.
 */
internal object AreaDamageResolver {

    fun resolve(
        tanks: List<ServerTankActor>,
        x: Int,
        y: Int,
        radius: Int,
        damage: Int,
        kind: ExplosionKind,
        owner: ServerTankActor? = null,
        ownerImmune: Boolean = false,
    ): GameEvent.Explosion {
        if (radius > 0 && damage > 0) {
            for (tank in tanks) {
                if (tank.armor <= 0) continue
                if (ownerImmune && tank === owner) continue
                val dx = (tank.positionX - x).toLong()
                val dy = (tank.positionY - y).toLong()
                val centerDist = kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                // Treat distance as the gap from the tank's hull edge rather than its
                // centroid so a tank whose body is grazed by the blast still takes some damage.
                val edgeDist = (centerDist - SERVER_TANK_HALF).coerceAtLeast(0f)
                if (edgeDist >= radius) continue
                val scaled = (damage.toFloat() * (1f - edgeDist / radius.toFloat())).toInt()
                if (scaled <= 0) continue
                tank.queueDamage(scaled)
            }
        }
        return GameEvent.Explosion(x, y, radius, kind)
    }
}

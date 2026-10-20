package com.tankarena.sim

import com.tankarena.core.DetMath
import com.tankarena.protocol.snapshot.ExplosionKind
import com.tankarena.protocol.snapshot.GameEvent
import com.tankarena.protocol.snapshot.SoundKind

/**
 * Single resolver for every weapon that does area-of-effect damage on detonation
 * (mines, mortars, rockets, A-bombs). Damage falls off linearly from full at the
 * impact center to zero at the edge of the radius. Returns a list of [GameEvent]s
 * the caller should publish: [GameEvent.Explosion] and [GameEvent.Sound].
 */
internal object AreaDamageResolver {

    fun resolve(
        tanks: List<Tank>,
        x: Int,
        y: Int,
        radius: Int,
        damage: Int,
        kind: ExplosionKind,
        owner: Tank? = null,
        ownerImmune: Boolean = false,
        turrets: List<Turret> = emptyList(),
        /** Legacy HT_ROCKET: the shield key does not stop it. */
        unshieldable: Boolean = false,
        /**
         * Legacy check_obj_hit_radius() hits everything in range for full power; only the mortar's
         * growing blast (create_mortar()) loses power with distance.
         */
        falloff: Boolean = true,
    ): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        
        if (radius > 0 && damage > 0) {
            for (tank in tanks) {
                if (tank.armor <= 0 && !tank.isWreck) continue
                if (ownerImmune && tank === owner) continue
                val dx = (tank.positionX - x).toLong()
                val dy = (tank.positionY - y).toLong()
                val centerDist = DetMath.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                // Treat distance as the gap from the tank's hull edge rather than its
                // centroid so a tank whose body is grazed by the blast still takes some damage.
                val edgeDist = (centerDist - SERVER_TANK_HALF).coerceAtLeast(0f)
                if (edgeDist >= radius) continue
                val scaled = if (falloff) (damage.toFloat() * (1f - edgeDist / radius.toFloat())).toInt() else damage
                if (scaled <= 0) continue
                if (unshieldable) tank.queueUnshieldableDamage(scaled) else tank.queueDamage(scaled)
            }
            for (turret in turrets) {
                if (!turret.isAlive) continue
                val dx = (turret.positionX - x).toLong()
                val dy = (turret.positionY - y).toLong()
                val half = (turret.body.size.width / 2f)
                val edgeDist = (DetMath.sqrt((dx * dx + dy * dy).toDouble()).toFloat() - half).coerceAtLeast(0f)
                if (edgeDist >= radius) continue
                val scaled = if (falloff) (damage.toFloat() * (1f - edgeDist / radius.toFloat())).toInt() else damage
                if (scaled > 0) turret.takeDamage(scaled)
            }
        }
        
        // Always emit explosion event
        events += GameEvent.Explosion(x, y, radius, kind)
        
        // Emit corresponding sound event
        events += GameEvent.Sound(
            kind = soundKindForExplosion(kind),
            x = x,
            y = y,
        )
        
        return events
    }

    private fun soundKindForExplosion(kind: ExplosionKind): SoundKind {
        return when (kind) {
            ExplosionKind.MINE -> SoundKind.MINE
            // Legacy create_mortar(): the impact goes off with SND_NUKE.
            ExplosionKind.MORTAR -> SoundKind.NUKE
            ExplosionKind.ROCKET -> SoundKind.SROCKET
            ExplosionKind.ABOMB -> SoundKind.EXPLODE
        }
    }
}

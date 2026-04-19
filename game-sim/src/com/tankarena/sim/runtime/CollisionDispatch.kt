package com.tankarena.sim.runtime

import com.pandulapeter.kubriko.collision.Collidable
import com.pandulapeter.kubriko.collision.CollisionDetector
import com.pandulapeter.kubriko.collision.extensions.isCollidingWith

/**
 * Manual mirror of [com.pandulapeter.kubriko.collision.CollisionManagerImpl.onUpdate].
 *
 * Because Kubriko 0.0.8 does not expose a public headless tick API, this
 * class drives Kubriko's collision **types** (Collidable / CollisionDetector /
 * CollisionMask / isCollidingWith) without owning a Kubriko engine instance.
 * The semantics — iterate detectors × declared collidable types, AABB
 * pre-check via the SAT helpers — are intentionally identical so the loop
 * body can be replaced wholesale by `CollisionManager.onUpdate(...)` once
 * Kubriko gains a headless tick.
 */
internal object CollisionDispatch {

    fun dispatch(
        detectors: List<CollisionDetector>,
        collidables: List<Collidable>,
    ) {
        if (detectors.isEmpty() || collidables.isEmpty()) return
        for (detector in detectors) {
            for (type in detector.collidableTypes) {
                val hits = collidables.filter { other ->
                    other !== detector && type.isInstance(other) && detector.isCollidingWith(other)
                }
                if (hits.isNotEmpty()) {
                    detector.onCollisionDetected(hits)
                }
            }
        }
    }
}

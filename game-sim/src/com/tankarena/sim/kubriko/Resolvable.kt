package com.tankarena.sim.kubriko

import com.pandulapeter.kubriko.actor.Actor

interface Resolvable : Actor {
    fun applyPendingResolutions(worldWidthPixels: Int, worldHeightPixels: Int)
}

package com.tankarena.sim.kubriko

import com.pandulapeter.kubriko.manager.ActorManager
import com.pandulapeter.kubriko.manager.Manager

class TerrainSlideManager(
    private val worldWidthPixels: Int,
    private val worldHeightPixels: Int,
    isLoggingEnabled: Boolean = false,
    instanceNameForLogging: String? = null,
) : Manager(
    isLoggingEnabled = isLoggingEnabled,
    instanceNameForLogging = instanceNameForLogging,
) {

    private val actorManager by manager<ActorManager>()

    override fun onUpdate(deltaTimeInMilliseconds: Int) {
        val actors = actorManager.allActors.value
        for (actor in actors) {
            if (actor is Resolvable) {
                actor.applyPendingResolutions(worldWidthPixels, worldHeightPixels)
            }
        }
    }
}

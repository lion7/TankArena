package com.tankarena.render.kubriko

import com.tankarena.sim.WorldState

/**
 * Runtime-facing projection of simulation state.
 *
 * This module intentionally avoids owning gameplay logic. A later Kubriko
 * scene implementation should consume these projections to render tiles,
 * entities, HUD, and debug overlays.
 */
data class RuntimeProjection(
    val tick: Long,
    val world: WorldState,
)

class TankArenaRuntimeProjector {
    fun project(state: WorldState): RuntimeProjection = RuntimeProjection(
        tick = state.tick,
        world = state,
    )
}


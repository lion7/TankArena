package com.tankarena.sim

import com.tankarena.input.PlayerIntentFrame
import com.tankarena.sim.runtime.SimulationHost

/**
 * Public façade around the Kubriko-style [SimulationHost].
 *
 * The host is the only authoritative mutation surface in the simulation: all
 * actor mutators are package-private to `:game-sim`, and the only entry point
 * exposed to consumers is [tick]. Callers receive immutable [WorldState]
 * snapshots and a list of [SimulationEvent]s.
 */
class TankArenaSimulation internal constructor(
    private val host: SimulationHost,
) {

    fun currentState(): WorldState = host.snapshot()

    fun tick(playerInputs: Map<Int, PlayerIntentFrame>): SimulationResult = host.tick(playerInputs)

    companion object {

    }
}

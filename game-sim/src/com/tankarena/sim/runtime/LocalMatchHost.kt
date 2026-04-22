package com.tankarena.sim.runtime

import com.tankarena.content.CanonicalMapDefinition
import com.tankarena.core.FixedStepClock
import com.tankarena.input.PlayerIntentFrame
import com.tankarena.protocol.FrameEnvelope
import com.tankarena.protocol.InputFrame
import com.tankarena.protocol.snapshot.ServerFrame
import com.tankarena.sim.MissionMode
import com.tankarena.sim.SimulationFactory

class LocalMatchHost(
    private val map: CanonicalMapDefinition,
    mode: MissionMode = MissionMode.SINGLE_PLAYER_VS_COMPUTER,
) {
    private val simulation = SimulationFactory.fromCanonicalMap(map, mode = mode)
    private val replication = ReplicationBuilder(map)
    private val latestInputs: MutableMap<Int, InputFrame> = mutableMapOf()

    val tickRate: Int = FixedStepClock.TICKS_PER_SECOND

    fun submitInput(frame: InputFrame) {
        latestInputs[frame.playerId] = frame
    }

    fun currentFrameEnvelope(): FrameEnvelope {
        return replication.build(
            state = simulation.currentState(),
            events = emptyList(),
        )
    }

    fun tick(): FrameEnvelope {
        val result = simulation.tick(
            latestInputs.mapValues { (_, frame) -> frame.toIntentFrame() },
        )
        lastServerFrame = replication.buildServerFrame(
            state = result.current,
            events = result.events,
        )
        return replication.build(
            state = result.current,
            events = result.events,
        )
    }

    /**
     * Most recent snapshot in the new wire shape. Populated each [tick]; null before the
     * first tick. Exposed for consumers migrating off the legacy [FrameEnvelope].
     */
    var lastServerFrame: ServerFrame? = null
        private set

    fun currentServerFrame(): ServerFrame = replication.buildServerFrame(
        state = simulation.currentState(),
        events = emptyList(),
    )
}

private fun InputFrame.toIntentFrame(): PlayerIntentFrame = PlayerIntentFrame(
    forward = forward,
    reverse = reverse,
    turnLeft = turnLeft,
    turnRight = turnRight,
    aimLeft = aimLeft,
    aimRight = aimRight,
    firePrimary = firePrimary,
    fireSecondary = fireSecondary,
    shield = shield,
    cycleWeaponLeft = cycleWeaponLeft,
    cycleWeaponRight = cycleWeaponRight,
)

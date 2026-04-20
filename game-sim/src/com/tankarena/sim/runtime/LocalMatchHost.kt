package com.tankarena.sim.runtime

import com.tankarena.content.CanonicalMapDefinition
import com.tankarena.core.FixedStepClock
import com.tankarena.input.PlayerIntentFrame
import com.tankarena.protocol.FrameEnvelope
import com.tankarena.protocol.InputFrame
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
        return replication.build(
            state = result.current,
            events = result.events,
        )
    }
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

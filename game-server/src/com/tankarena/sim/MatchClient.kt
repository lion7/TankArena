package com.tankarena.sim

import com.tankarena.content.MapSceneSidecar
import com.tankarena.input.PlayerIntentFrame
import com.tankarena.sim.legacy.CanonicalMapDefinition
import com.tankarena.protocol.InputFrame
import com.tankarena.protocol.snapshot.ServerFrame
import com.tankarena.protocol.snapshot.WorldSnapshot

interface MatchClient {
    fun submitInput(input: InputFrame)
    fun tick(): ServerFrame
    fun currentServerFrame(): ServerFrame
    fun dispose()
}

class LocalMatchClient private constructor(
    private val prototype: Match,
    /** Records the inputs and result of the match, so it can be replayed as a test. */
    val recorder: MatchRecorder? = null,
) : MatchClient {

    private val latestInputs: MutableMap<Int, InputFrame> = mutableMapOf()
    private var tickIndex: Long = 0

    override fun submitInput(input: InputFrame) {
        latestInputs[input.playerId] = input
    }

    override fun tick(): ServerFrame {
        val intents = if (latestInputs.isEmpty()) {
            emptyMap()
        } else {
            latestInputs.mapValues { (_, frame) -> frame.toIntent() }
        }
        recorder?.record(tickIndex, intents)
        val world = prototype.tick(intents)
        recorder?.recordEvents(prototype, tickIndex, world.events)
        tickIndex++
        return toServerFrame(world)
    }

    override fun currentServerFrame(): ServerFrame = toServerFrame(prototype.snapshot())

    override fun dispose() {
        prototype.dispose()
    }

    private fun toServerFrame(world: WorldSnapshot): ServerFrame = ServerFrame(
        tick = world.tick,
        world = world,
        playerViews = prototype.buildPlayerViews(),
    )

    companion object {
        /** [recordAs] names the shipped scene to record the match under; null plays without recording. */
        fun fromSceneJson(
            sceneJson: String,
            sidecar: MapSceneSidecar,
            mode: MatchMode = MatchMode.SINGLE,
            recordAs: String? = null,
            dual: DualSetup = DualSetup(),
        ): LocalMatchClient {
            val prototype = Match.fromSceneJson(sceneJson, sidecar, mode, dual)
            prototype.initialize()
            return LocalMatchClient(prototype, recordAs?.let { MatchRecorder(it, mode, dual.takeIf { mode == MatchMode.DUAL }) })
        }

        internal fun fromCanonicalMap(map: CanonicalMapDefinition, mode: MatchMode = MatchMode.SINGLE): LocalMatchClient {
            val prototype = Match.fromCanonicalMap(map, mode)
            prototype.initialize()
            return LocalMatchClient(prototype)
        }
    }
}

private fun InputFrame.toIntent(): PlayerIntentFrame = PlayerIntentFrame(
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
    selectTankId = selectTankId,
)

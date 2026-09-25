package com.tankarena.sim.kubriko.server

import com.tankarena.content.MapSceneSidecar
import com.tankarena.input.PlayerIntentFrame
import com.tankarena.sim.kubriko.server.legacy.CanonicalMapDefinition
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
    private val prototype: ServerMatchPrototype,
) : MatchClient {

    private val latestInputs: MutableMap<Int, InputFrame> = mutableMapOf()

    override fun submitInput(input: InputFrame) {
        latestInputs[input.playerId] = input
    }

    override fun tick(): ServerFrame {
        val intents = if (latestInputs.isEmpty()) {
            emptyMap()
        } else {
            latestInputs.mapValues { (_, frame) -> frame.toIntent() }
        }
        return toServerFrame(prototype.tick(intents))
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
        fun fromSceneJson(sceneJson: String, sidecar: MapSceneSidecar, mode: MatchMode = MatchMode.SINGLE): LocalMatchClient {
            val prototype = ServerMatchPrototype.fromSceneJson(sceneJson, sidecar, mode)
            prototype.initialize()
            return LocalMatchClient(prototype)
        }

        internal fun fromCanonicalMap(map: CanonicalMapDefinition, mode: MatchMode = MatchMode.SINGLE): LocalMatchClient {
            val prototype = ServerMatchPrototype.fromCanonicalMap(map, mode)
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

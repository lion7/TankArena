package com.tankarena.sim.kubriko.server

import com.tankarena.content.CanonicalMapDefinition
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

    override fun tick(): ServerFrame = toServerFrame(prototype.tick())

    override fun currentServerFrame(): ServerFrame = toServerFrame(prototype.snapshot())

    override fun dispose() {
        prototype.dispose()
    }

    private fun toServerFrame(world: WorldSnapshot): ServerFrame = ServerFrame(
        tick = world.tick,
        world = world,
        playerViews = emptyList(),
    )

    companion object {
        fun fromCanonicalMap(map: CanonicalMapDefinition): LocalMatchClient {
            val prototype = ServerMatchPrototype.fromCanonicalMap(map)
            prototype.initialize()
            return LocalMatchClient(prototype)
        }
    }
}

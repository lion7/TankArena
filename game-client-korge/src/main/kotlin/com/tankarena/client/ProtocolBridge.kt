package com.tankarena.client

import com.tankarena.core.Entity
import com.tankarena.core.GameState
import com.tankarena.protocol.EntitySnapshot
import com.tankarena.protocol.ServerSnapshotMessage

fun GameState.toSnapshotMessage(): ServerSnapshotMessage = ServerSnapshotMessage(
    tick = tick,
    entities = entities.map { it.toSnapshot() },
)

private fun Entity.toSnapshot(): EntitySnapshot = EntitySnapshot(
    id = id,
    kind = kind.name,
    x = position.x,
    y = position.y,
)

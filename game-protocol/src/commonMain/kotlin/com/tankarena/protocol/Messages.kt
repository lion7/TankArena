package com.tankarena.protocol

import kotlinx.serialization.Serializable

@Serializable
data class ClientInputMessage(
    val playerId: Int,
    val tick: Long,
    val actions: Set<String>,
)

@Serializable
data class ServerSnapshotMessage(
    val tick: Long,
    val entities: List<EntitySnapshot>,
)

@Serializable
data class EntitySnapshot(
    val id: Long,
    val kind: String,
    val x: Float,
    val y: Float,
)

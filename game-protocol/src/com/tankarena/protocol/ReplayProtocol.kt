package com.tankarena.protocol

import com.tankarena.core.Tick
import kotlinx.serialization.Serializable

@Serializable
data class ReplayFrame(
    val tick: Long,
    val playerInputs: Map<Int, InputFrame>,
)

@Serializable
data class ReplayHeader(
    val mapId: String,
    val randomSeed: Long,
    val tickRate: Int,
)

@Serializable
data class ReplayCapture(
    val header: ReplayHeader,
    val frames: List<ReplayFrame>,
)

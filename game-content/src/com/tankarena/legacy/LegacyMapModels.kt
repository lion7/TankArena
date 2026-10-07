package com.tankarena.legacy

import kotlinx.serialization.Serializable

@Serializable
data class LegacyMapHeader(
    val missionCode: String,
    val nextMissionCode: String,
    val widthTiles: Int,
    val heightTiles: Int,
    val missionTextOffsets: List<Int>,
    val randomBonus: Boolean,
    val background: Int,
    val publicPassword: Boolean,
    val night: Boolean,
    val mapType: Int,
    val mapVersion: Int,
    val mapContents: Int,
    val objectSize: Int,
    val shareware: Boolean,
    val lock: Boolean,
)

@Serializable
data class LegacyMapData(
    val header: LegacyMapHeader,
    val terrain0: List<Int>,
    val terrain1: List<Int>,
    val terrain2: List<Int>,
    val goalLayer: List<Int>,
    val bonusLayer: List<Int>,
    val manTypeLayer: List<Int>,
    val manAmountLayer: List<Int>,
    val briefingText: String,
    val successText: String,
    val failureText: String,
    val rawObjectBlobSize: Int,
    val rawObjectBlob: ByteArray,
)

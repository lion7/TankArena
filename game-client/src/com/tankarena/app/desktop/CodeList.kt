package com.tankarena.app.desktop

import com.tankarena.content.GameModeCompatibility
import com.tankarena.ui.compose.menu.MissionEntry

/**
 * Legacy list_codes() (src/game/listcod.c, `-CODES`): every map with its type and codes. The
 * rewrite's maps are never locked or shareware.
 */
internal fun codeList(missions: List<MissionEntry>): String = buildString {
    append("L = Locked, P = Public password, S = Shareware, T = Type\n")
    append("Type: 0 = Any, 1 = Dual, 2 = Single, 3 = Dualvc, 4 = S/D\n\n")
    append("%1s %1s %1s %1s %-15s %-22s %-22s\n".format("L", "P", "S", "T", "File", "Mission code", "Next code"))
    append("%1s %1s %1s %1s %-15s %-22s %-22s\n".format("-", "-", "-", "-", "----", "------------", "---------"))
    for (mission in missions.sortedBy { it.baseName }) {
        val metadata = mission.sidecar.metadata
        val file = mission.baseName.uppercase() + ".MAP"
        val type = metadata.modeCompatibility.ordinal
        if (metadata.modeCompatibility != GameModeCompatibility.DUAL) {
            val public = if (metadata.publicPassword) "Y" else "N"
            append("%1s %1s %1s %1d %-15s %-22s %-22s\n".format("N", public, "N", type, file, metadata.missionCode, metadata.nextMissionCode.orEmpty()))
        } else {
            append("%1s - %1s %1d %-15s\n".format("N", "N", type, file))
        }
    }
    append("\nTotal: ${missions.size}\n")
}

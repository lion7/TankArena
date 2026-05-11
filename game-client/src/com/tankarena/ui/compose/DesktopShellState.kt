package com.tankarena.ui.compose

import com.tankarena.ui.compose.menu.MissionEntry

enum class GameMode {
    PLAYER_VS_PLAYER,
    SINGLE_PLAYER_VS_COMPUTER,
    DUAL_PLAYER_VS_COMPUTER,
}

enum class MissionOutcome { WON, LOST }

sealed interface DesktopShellScreen {
    data object MainMenu : DesktopShellScreen
    data object GameModeSelect : DesktopShellScreen
    data object MissionSelect : DesktopShellScreen
    data class Playing(val mission: MissionEntry, val mode: GameMode) : DesktopShellScreen
    data class Debrief(
        val mission: MissionEntry,
        val mode: GameMode,
        val outcome: MissionOutcome,
        val score: Int = 0,
        val kills: Int = 0,
        val captures: Int = 0,
        val time: Long = 0,
    ) : DesktopShellScreen
    data object Editor : DesktopShellScreen
}

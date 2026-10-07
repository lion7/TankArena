package com.tankarena.ui.compose

import com.tankarena.ui.compose.menu.HelpFile
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

    /** Legacy `arena_info` / `keys_info`: a help file in the text viewer. */
    data class Help(val file: HelpFile) : DesktopShellScreen

    /** Legacy `read_string("MISSION CODE (ESC = LIST): ")`, or the map name prompt of `dual_game`. */
    data class CodePrompt(val mode: GameMode) : DesktopShellScreen

    /** Legacy `select_password_from_list`, or `select_from_list` for player vs player. */
    data class CodeList(val mode: GameMode) : DesktopShellScreen

    /** Legacy `message_key`: a message bar; any key continues to [next]. */
    data class Message(val text: String, val next: DesktopShellScreen) : DesktopShellScreen

    /** Legacy `view_file(mapfile, map.mission_text[0], ...)` right before the mission starts. */
    data class Briefing(val mission: MissionEntry, val mode: GameMode) : DesktopShellScreen

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

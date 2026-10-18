package com.tankarena.ui.compose.menu

import com.tankarena.ui.compose.DesktopShellScreen
import com.tankarena.ui.compose.GameMode
import com.tankarena.ui.compose.MissionOutcome

/**
 * The shell state around the mission-code prompt of legacy `single_game`,
 * `dual_v_computer_game` and `dual_game`: the last code per mode (the legacy statics
 * `single_code`, `dual_v_computer_code`, `dual_map_name`) and the reached codes in
 * [PasswordStore].
 */
class MissionGate(private val passwords: PasswordStore) {
    private val currentCodes = mutableMapOf<GameMode, String>()

    /** The text the prompt starts with: the last entered or reached code of [mode]. */
    fun currentCode(mode: GameMode): String = currentCodes[mode] ?: MissionCodes.defaultCode(mode)

    /** The entries behind the prompt's ESC: known passwords, or the dual maps. */
    fun listEntries(missions: List<MissionEntry>, mode: GameMode): List<String> =
        MissionCodes.listEntries(missions, passwords.load(), mode)

    /**
     * Enter on the prompt or on the list. Like the legacy code, the text is kept for the next
     * prompt even when it is wrong; a valid mission code is remembered as known
     * (`add_password_to_list`). Returns the screen to show next: the briefing (or the game when
     * the map has none), or a message that leads back to the game mode menu.
     */
    fun submit(missions: List<MissionEntry>, input: String, mode: GameMode): DesktopShellScreen {
        currentCodes[mode] = input
        return when (val resolution = MissionCodes.resolve(missions, input, mode)) {
            is CodeResolution.Rejected ->
                DesktopShellScreen.Message(resolution.message, next = DesktopShellScreen.GameModeSelect)

            is CodeResolution.Accepted -> {
                if (mode != GameMode.PLAYER_VS_PLAYER) passwords.add(input.trim())
                val mission = resolution.mission
                if (mission.sidecar.missionText.briefing.isBlank()) {
                    DesktopShellScreen.Playing(mission, mode)
                } else {
                    DesktopShellScreen.Briefing(mission, mode)
                }
            }
        }
    }

    /**
     * End of legacy `single_game` / `dual_v_computer_game`: a won mission makes its
     * `next_mission_code` the prompt's text and a known password. Returns the screen to show
     * next: the mission's success or failure text (legacy `debrief()`), or straight back to the
     * game mode menu after ESC ([outcome] null), player vs player, or a mission without text.
     */
    fun missionEnded(mission: MissionEntry, mode: GameMode, outcome: MissionOutcome?): DesktopShellScreen {
        if (mode == GameMode.PLAYER_VS_PLAYER || outcome == null) return DesktopShellScreen.GameModeSelect
        if (outcome == MissionOutcome.WON) {
            val next = mission.sidecar.metadata.nextMissionCode.orEmpty().trim()
            currentCodes[mode] = next
            passwords.add(next)
        }
        val text = mission.sidecar.missionText.let { if (outcome == MissionOutcome.WON) it.success else it.failure }
        return if (text.isBlank()) DesktopShellScreen.GameModeSelect else DesktopShellScreen.AfterMission(text)
    }
}

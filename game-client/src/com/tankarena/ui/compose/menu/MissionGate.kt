package com.tankarena.ui.compose.menu

import com.tankarena.ui.compose.DesktopShellScreen
import com.tankarena.ui.compose.DualMatch
import com.tankarena.ui.compose.GameMode
import com.tankarena.ui.compose.MissionOutcome

/**
 * The shell state around the mission-code prompt of legacy `single_game`,
 * `dual_v_computer_game` and `dual_game`: the last code per mode (the legacy statics
 * `single_code`, `dual_v_computer_code`, `dual_map_name`) and the reached codes in
 * [PasswordStore].
 */
class MissionGate(private val passwords: PasswordStore, private val settings: GameSettings = GameSettings()) {
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
                if (mission.sidecar.missionText.briefing.isBlank()) afterBriefing(mission, mode) else DesktopShellScreen.Briefing(mission, mode)
            }
        }
    }

    /**
     * What follows the briefing: the mission, or for player vs player the tank selection of the
     * first round (legacy dual_game() shows the map text once, then loops over the rounds).
     */
    fun afterBriefing(mission: MissionEntry, mode: GameMode): DesktopShellScreen =
        if (mode == GameMode.PLAYER_VS_PLAYER) {
            DesktopShellScreen.TankSelect(DualMatch(mission, options = settings.options))
        } else {
            DesktopShellScreen.Playing(mission, mode)
        }

    /**
     * End of legacy `single_game` / `dual_v_computer_game`: a won mission makes its
     * `next_mission_code` the prompt's text and a known password. Returns the screen to show
     * next: the mission's success or failure text (legacy `debrief()`), or straight back to the
     * game mode menu after ESC ([outcome] null), player vs player, or a mission without text.
     * Every fifth mission won plays THUNDER.FLI before the text.
     */
    fun missionEnded(mission: MissionEntry, mode: GameMode, outcome: MissionOutcome?): DesktopShellScreen {
        if (mode == GameMode.PLAYER_VS_PLAYER || outcome == null) return DesktopShellScreen.GameModeSelect
        var thunder = false
        if (outcome == MissionOutcome.WON) {
            val next = mission.sidecar.metadata.nextMissionCode.orEmpty().trim()
            currentCodes[mode] = next
            passwords.add(next)
            val (counted, play) = settings.options.afterVictory()
            settings.update(counted)
            thunder = play
        }
        val text = mission.sidecar.missionText.let { if (outcome == MissionOutcome.WON) it.success else it.failure }
        val after = if (text.isBlank()) DesktopShellScreen.GameModeSelect else DesktopShellScreen.AfterMission(text)
        return if (thunder) DesktopShellScreen.Animation(THUNDER_FLI, after) else after
    }

    /**
     * Legacy dual_game(): after a round, the next round's tank selection, or once the match is
     * decided VICTORY.FLI and the game mode menu. ESC ([winner] null) ends the whole match.
     */
    fun roundEnded(match: DualMatch, winner: Int?): DesktopShellScreen {
        if (winner == null) return DesktopShellScreen.GameModeSelect
        val next = match.afterRound(winner)
        return if (next.decided) DesktopShellScreen.Animation(VICTORY_FLI, DesktopShellScreen.GameModeSelect) else DesktopShellScreen.TankSelect(next)
    }

    companion object {
        const val THUNDER_FLI = "THUNDER.FLI"
        const val VICTORY_FLI = "VICTORY.FLI"
    }
}

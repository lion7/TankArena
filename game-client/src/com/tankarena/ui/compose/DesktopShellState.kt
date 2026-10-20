package com.tankarena.ui.compose

import com.tankarena.ui.compose.menu.GameOptions
import com.tankarena.ui.compose.menu.HelpFile
import com.tankarena.ui.compose.menu.IntroStep
import com.tankarena.ui.compose.menu.MissionEntry

enum class GameMode {
    PLAYER_VS_PLAYER,
    SINGLE_PLAYER_VS_COMPUTER,
    DUAL_PLAYER_VS_COMPUTER,
}

enum class MissionOutcome { WON, LOST }

/**
 * Legacy dual_game() (src/game/dual.c): the rounds of one player-vs-player match on [mission].
 * [round] is 1-based; [wins] counts the rounds each player has won (`game.wins`). [options]
 * are the game options the match was started with (rounds, laps, the losers tank).
 */
data class DualMatch(
    val mission: MissionEntry,
    val round: Int = 1,
    val wins: List<Int> = listOf(0, 0),
    val options: GameOptions = GameOptions(),
) {
    /** The match after a round [winner] won. */
    fun afterRound(winner: Int): DualMatch =
        copy(round = round + 1, wins = wins.mapIndexed { player, n -> if (player == winner) n + 1 else n })

    /** Legacy `end_game`: someone has won more than half of the `rnds` rounds. */
    val decided: Boolean get() = options.matchDecided(wins)
}

sealed interface DesktopShellScreen {
    /** Legacy write_title(): the intro animations and credits before the main menu. */
    data class Intro(val step: IntroStep) : DesktopShellScreen

    data object MainMenu : DesktopShellScreen

    /** Legacy setup.c (TANK -SETUP): the players' control modes and keys; then [next]. */
    data class ControlsSetup(val next: DesktopShellScreen) : DesktopShellScreen

    /** Legacy main(): "EXIT TO DOS? (Y/N)" after the main menu is left. */
    data object ExitConfirm : DesktopShellScreen

    /** Legacy game_options(). */
    data object Options : DesktopShellScreen

    /**
     * Legacy show_fli() and `fade_out(3)` (VICTORY.FLI after a player-vs-player match,
     * THUNDER.FLI after every fifth mission won), then [next].
     */
    data class Animation(val fileName: String, val next: DesktopShellScreen) : DesktopShellScreen
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

    /**
     * One round of a game: [dual] is the player-vs-player match it belongs to and [tankTypes]
     * the vehicles the players picked for it (legacy `pl_tanks`).
     */
    data class Playing(
        val mission: MissionEntry,
        val mode: GameMode,
        val dual: DualMatch? = null,
        val tankTypes: List<Int> = listOf(0, 0),
    ) : DesktopShellScreen

    /** Legacy select_tanks() (src/game/seltank.c) before every player-vs-player round. */
    data class TankSelect(val match: DualMatch) : DesktopShellScreen

    /**
     * Legacy `debrief()` (src/game/single.c): after a won mission its success text, after a lost
     * one its failure text, in the text viewer; then back to the game mode menu.
     */
    data class AfterMission(val text: String, val won: Boolean = true) : DesktopShellScreen
    /** The map editor (ADR 0021): choosing a map, then editing it. */
    data object EditorOpen : DesktopShellScreen
    /** A new map: name, world, size and start. */
    data object EditorNew : DesktopShellScreen
    data class Editor(
        val map: com.tankarena.app.desktop.EditorMapChoice,
        /** Kept here so that the edits and their undo history outlive a play-test. */
        val editor: com.tankarena.sim.map.MapEditor = com.tankarena.sim.map.MapEditor(map.load()),
    ) : DesktopShellScreen

    /** A play-test of the map being edited, as it is now; afterwards back to [editing]. */
    data class PlayTest(val editing: Editor, val mission: MissionEntry, val mode: GameMode) : DesktopShellScreen

    /** First start: Classic or Extended (ADR 0023). */
    data object RulesetChoice : DesktopShellScreen

    /** Extended: the main menu, shown wherever Classic shows its main or game mode menu. */
    data object ExtendedMenu : DesktopShellScreen

    /** Extended: two players at one keyboard, together or against each other. */
    data object LocalGame : DesktopShellScreen

    /** Extended: the missions reached in [mode] (one player, or two against the computer). */
    data class Campaign(val mode: GameMode) : DesktopShellScreen

    /** Extended: settings and help submenus. */
    data object ExtendedSettings : DesktopShellScreen
    data object ExtendedHelp : DesktopShellScreen

    /** Extended: the saved recordings, and one of them being watched. */
    data object Replays : DesktopShellScreen
    data class Replay(val file: java.io.File) : DesktopShellScreen
}

package com.tankarena.ui.compose.menu

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tankarena.sim.Ruleset

/**
 * The first start (ADR 0023): Classic plays and looks like the original, Extended has the new menu
 * and, later, more players, gamepads and networked play. The choice is kept in `settings.json`.
 */
@Composable
fun RulesetChoiceScreen(
    onChoose: (Ruleset) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RetroMenuScreen(
        title = "CHOOSE YOUR GAME",
        items = listOf(
            RetroMenuItem(label = "Classic - the original", onActivate = { onChoose(Ruleset.CLASSIC) }),
            RetroMenuItem(label = "Extended - new menus", onActivate = { onChoose(Ruleset.EXTENDED) }),
        ),
        modifier = modifier,
        onCancel = onExit,
    )
}

/**
 * The Extended main menu (ADR 0023): every game mode directly, replays, the editor, settings and
 * help. Entries the rewrite does not have yet are shown but cannot be picked.
 */
@Composable
fun ExtendedMenuScreen(
    onCampaign: () -> Unit,
    onTwoVsComputer: () -> Unit,
    onPlayerVsPlayer: () -> Unit,
    onReplays: () -> Unit,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
    onQuit: () -> Unit,
    modifier: Modifier = Modifier,
    onNetworkGame: (() -> Unit)? = null,
    onMapEditor: (() -> Unit)? = null,
) {
    RetroMenuScreen(
        title = "TANK ARENA",
        items = listOf(
            RetroMenuItem(label = "Campaign", onActivate = onCampaign),
            RetroMenuItem(label = "Two players vs computer", onActivate = onTwoVsComputer),
            RetroMenuItem(label = "Player vs player", onActivate = onPlayerVsPlayer),
            RetroMenuItem(label = "Network game", enabled = onNetworkGame != null, onActivate = onNetworkGame ?: {}),
            RetroMenuItem(label = "Replays", onActivate = onReplays),
            RetroMenuItem(label = "Map editor", enabled = onMapEditor != null, onActivate = onMapEditor ?: {}),
            RetroMenuItem(label = "Settings", onActivate = onSettings),
            RetroMenuItem(label = "Help", onActivate = onHelp),
            RetroMenuItem(label = "Quit", onActivate = onQuit),
        ),
        modifier = modifier,
        onCancel = onQuit,
    )
}

/** Extended settings: the game options of the original, and the way back to Classic. */
@Composable
fun ExtendedSettingsScreen(
    onGameOptions: () -> Unit,
    onControls: () -> Unit,
    onClassic: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RetroMenuScreen(
        title = "SETTINGS",
        items = listOf(
            RetroMenuItem(label = "Game options", onActivate = onGameOptions),
            RetroMenuItem(label = "Controls", onActivate = onControls),
            RetroMenuItem(label = "Switch to Classic", onActivate = onClassic),
            RetroMenuItem(label = "Back", onActivate = onBack),
        ),
        modifier = modifier,
        onCancel = onBack,
    )
}

/** Extended help: the original's two help files. */
@Composable
fun ExtendedHelpScreen(
    onArenaInfo: () -> Unit,
    onKeys: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RetroMenuScreen(
        title = "HELP",
        items = listOf(
            RetroMenuItem(label = "Arena info", onActivate = onArenaInfo),
            RetroMenuItem(label = "Keys", onActivate = onKeys),
            RetroMenuItem(label = "Back", onActivate = onBack),
        ),
        modifier = modifier,
        onCancel = onBack,
    )
}

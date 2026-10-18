package com.tankarena.ui.compose.menu

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tankarena.ui.compose.GameMode

@Composable
fun GameModeMenuScreen(
    onSelectPlayerVsPlayer: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    onSelectSinglePlayer: (() -> Unit)? = null,
    onSelectDualVsComputer: (() -> Unit)? = null,
    /** The mode last played from this menu; legacy menu() keeps its selection across games. */
    selected: GameMode? = null,
) {
    val items = listOf(
        RetroMenuItem(label = "Player VS Player mode", onActivate = onSelectPlayerVsPlayer),
        RetroMenuItem(
            label = "One player VS Computer mode",
            enabled = onSelectSinglePlayer != null,
            onActivate = onSelectSinglePlayer ?: {},
        ),
        RetroMenuItem(
            label = "Two player VS Computer mode",
            enabled = onSelectDualVsComputer != null,
            onActivate = onSelectDualVsComputer ?: {},
        ),
        RetroMenuItem(label = "Leave this menu", onActivate = onCancel),
    )
    RetroMenuScreen(
        title = "SELECT GAME MODE",
        items = items,
        modifier = modifier,
        initialSelection = when (selected) {
            null, GameMode.PLAYER_VS_PLAYER -> 0
            GameMode.SINGLE_PLAYER_VS_COMPUTER -> 1
            GameMode.DUAL_PLAYER_VS_COMPUTER -> 2
        },
        onCancel = onCancel,
    )
}

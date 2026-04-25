package com.tankarena.ui.compose.menu

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun GameModeMenuScreen(
    onSelectPlayerVsPlayer: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    onSelectSinglePlayer: (() -> Unit)? = null,
    onSelectDualVsComputer: (() -> Unit)? = null,
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
        onCancel = onCancel,
        footerHint = "\u2191 / \u2193 select   ENTER confirm   ESC back",
    )
}

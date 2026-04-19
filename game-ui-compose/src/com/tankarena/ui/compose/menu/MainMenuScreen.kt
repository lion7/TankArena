package com.tankarena.ui.compose.menu

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun MainMenuScreen(
    onStartNewGame: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    onGameOptions: (() -> Unit)? = null,
    onMapEditor: (() -> Unit)? = null,
    onArenaInfo: (() -> Unit)? = null,
    onDefaultKeys: (() -> Unit)? = null,
) {
    val items = listOf(
        RetroMenuItem(label = "Start new game", onActivate = onStartNewGame),
        RetroMenuItem(label = "Game options", enabled = onGameOptions != null, onActivate = onGameOptions ?: {}),
        RetroMenuItem(label = "Map editor", enabled = onMapEditor != null, onActivate = onMapEditor ?: {}),
        RetroMenuItem(label = "Arena info", enabled = onArenaInfo != null, onActivate = onArenaInfo ?: {}),
        RetroMenuItem(label = "Default keys", enabled = onDefaultKeys != null, onActivate = onDefaultKeys ?: {}),
        RetroMenuItem(label = "Exit to DOS", onActivate = onExit),
    )
    RetroMenuScreen(
        title = "MAIN MENU",
        items = items,
        modifier = modifier,
        onCancel = onExit,
        footerHint = "\u2191 / \u2193 select   ENTER confirm   ESC exit",
    )
}

package nl.tankarena.ui.compose.extended

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/** Where the Extended home screen leads; null for what is not there yet. */
class ExtendedHomeActions(
    val campaign: () -> Unit,
    val localGame: () -> Unit,
    val replays: () -> Unit,
    val mapEditor: (() -> Unit)?,
    val settings: () -> Unit,
    val help: () -> Unit,
    val quit: () -> Unit,
    val networkGame: (() -> Unit)? = null,
)

/**
 * The Extended main menu (ADR 0023): every way into the game on one screen, with the mouse, the
 * keyboard, a pad or a finger. ESC quits; Ctrl+Shift+M switches to Classic (handled by the app).
 */
@Composable
fun ExtendedHomeScreen(actions: ExtendedHomeActions, modifier: Modifier = Modifier) {
    val keys = remember { KeyRouter() }
    val entries = listOf(
        ListEntry("Campaign", "missions one after another") to actions.campaign,
        ListEntry("Local game", "two players, together or against each other") to actions.localGame,
        ListEntry("Network game", "players on other machines", enabled = actions.networkGame != null) to (actions.networkGame ?: {}),
        ListEntry("Replays", "watch your recorded games") to actions.replays,
        ListEntry("Map editor", "", enabled = actions.mapEditor != null) to (actions.mapEditor ?: {}),
        ListEntry("Settings", "controls, video, sound, match rules") to actions.settings,
        ListEntry("Help", "how to play, the keys") to actions.help,
    )
    ExtendedPage(
        title = "TANK ARENA",
        // Extended's name: its initials spell TANK.
        subtitle = "Next Kombat",
        onBack = actions.quit,
        backLabel = "Quit",
        // Classic is played with the keyboard, so only a keyboard switches to it (not shown on a touch screen).
        footer = "Up/Down and Enter, or the mouse · Ctrl+Shift+M: Classic",
        onKey = keys::handle,
        modifier = modifier,
    ) {
        ExtendedList(entries.map { it.first }, onPick = { entries[it].second() }, keys = keys)
    }
}

package nl.tankarena.ui.compose.menu

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import nl.tankarena.sim.Ruleset

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

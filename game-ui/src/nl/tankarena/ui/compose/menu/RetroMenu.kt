package nl.tankarena.ui.compose.menu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import nl.tankarena.ui.compose.legacy.LegacyColors
import nl.tankarena.ui.compose.legacy.LegacyFonts
import nl.tankarena.ui.compose.legacy.Mode13Screen
import nl.tankarena.ui.compose.legacy.Mode13View

data class RetroMenuItem(
    val label: String,
    /** False for entries the rewrite does not have yet; the cursor skips them. */
    val enabled: Boolean = true,
    val onActivate: () -> Unit,
)

/**
 * Legacy `menu()` (src/game/menu.c): the panel screen with a brighter band, a black box in it,
 * the title and the entries in the big font, centred; the selected entry in `FNT_HIGH`, the
 * others in `FNT_NORM`. Up and Down move (wrapping round), Enter picks, ESC leaves.
 */
@Composable
fun RetroMenuScreen(
    title: String,
    items: List<RetroMenuItem>,
    modifier: Modifier = Modifier,
    initialSelection: Int = items.indexOfFirst { it.enabled }.coerceAtLeast(0),
    onCancel: (() -> Unit)? = null,
) {
    var selected by remember(items.map { it.label }) {
        mutableIntStateOf(initialSelection.coerceIn(0, (items.size - 1).coerceAtLeast(0)))
    }
    Mode13View(
        modifier = modifier,
        onKey = { event ->
            when (event.key) {
                Key.DirectionUp -> selected = step(items, selected, -1)
                Key.DirectionDown -> selected = step(items, selected, 1)
                Key.Enter, Key.NumPadEnter -> items.getOrNull(selected)?.takeIf { it.enabled }?.onActivate?.invoke()
                Key.Escape -> onCancel?.invoke() ?: return@Mode13View false
                else -> return@Mode13View false
            }
            true
        },
    ) {
        drawMenu(title, items.map { it.label }, selected)
    }
}

/** The picture of legacy `menu()` with entry [selected] (0-based) highlighted. */
internal fun Mode13Screen.drawMenu(title: String, labels: List<String>, selected: Int) {
    val height = LegacyFonts.BIG_HEIGHT
    val widest = labels.maxOfOrNull { Mode13Screen.bigLength(it) } ?: 0
    // `max` counts the title too: the entries are m[1]..m[max-1].
    val max = labels.size + 1
    val top = (180 - labels.size * height) / 2
    panel()
    addBar((280 - widest) / 2, top - 10, widest + 40, 20 + max * height + 11, LegacyColors.BAR_ADD)
    bar((290 - widest) / 2, top - 5, widest + 30, 10 + max * height + 11, LegacyColors.BLACK)
    bigTextCentre(title, 160, top, LegacyColors.HIGH)
    labels.forEachIndexed { index, label ->
        val color = if (index == selected) LegacyColors.HIGH else LegacyColors.NORMAL
        bigTextCentre(label, 160, top + 11 + (index + 1) * height, color)
    }
}

/** The next enabled entry in [direction], wrapping round like the legacy menu. */
private fun step(items: List<RetroMenuItem>, current: Int, direction: Int): Int {
    if (items.isEmpty()) return 0
    var index = current
    repeat(items.size) {
        index = (index + direction + items.size) % items.size
        if (items[index].enabled) return index
    }
    return current
}

package nl.tankarena.ui.compose.menu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import nl.tankarena.app.LocalTouchScreen
import nl.tankarena.ui.compose.legacy.LegacyColors
import nl.tankarena.ui.compose.legacy.LegacyFonts
import nl.tankarena.ui.compose.legacy.Mode13View

/** Legacy `list_length` of `select_password_from_list` and `select_from_list`. */
private const val LIST_LENGTH = 20

/**
 * Legacy `read_string` (original/src/game/prompt.c): a brighter band across the middle of the panel
 * screen with the prompt, then the text typed so far on black and a green cursor block. The text
 * starts as [initialText] (the legacy code keeps the last code, so after a win it already holds
 * the next one). Enter submits, ESC cancels.
 */
@Composable
fun PromptBarScreen(
    prompt: String,
    initialText: String,
    maxLength: Int,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember(prompt, initialText) { mutableStateOf(initialText.take(maxLength)) }
    Mode13View(
        modifier = modifier,
        onKey = { event ->
            when (event.key) {
                Key.Enter, Key.NumPadEnter -> onSubmit(text)
                Key.Escape -> onCancel()
                Key.Backspace -> text = text.backspaceInPrompt()
                else -> {
                    val typed = promptCharFor(event.key) ?: return@Mode13View false
                    text = text.typeInPrompt(typed, maxLength)
                }
            }
            true
        },
    ) {
        val length = prompt.length + maxLength + 1
        var x = 160 - length * 3
        panel()
        addBar(x - 10, 85, length * 6 + 20, 30, LegacyColors.BAR_ADD)
        writeText(x, 98, LegacyColors.HIGH, prompt)
        x += 6 * prompt.length
        bar(x, 98, maxLength * 6 + 6, 5, LegacyColors.BLACK)
        overwriteText(x, 98, LegacyColors.HIGH, text)
        bar(x + text.length * 6, 98, 5, 5, LegacyColors.CURSOR)
    }
}

/** Legacy `message_key` (original/src/game/prompt.c): a one-line message band; any key (or a tap) dismisses it. */
@Composable
fun MessageBarScreen(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Mode13View(
        modifier = modifier,
        onKey = {
            onDismiss()
            true
        },
        onTap = { _, _ -> onDismiss() },
    ) {
        val length = message.length * 6
        panel()
        addBar((300 - length) / 2, 85, length + 20, 30, LegacyColors.BAR_ADD)
        writeText((320 - length) / 2, 98, LegacyColors.HIGH, message)
    }
}

/** The two list boxes: `select_password_from_list` is wider than the map list of `select_from_list`. */
enum class PickListLayout(val boxLeft: Int, val boxWidth: Int, val textLeft: Int) {
    PASSWORDS(boxLeft = 50, boxWidth = 220, textLeft = 90),
    MAPS(boxLeft = 90, boxWidth = 140, textLeft = 125),
}

/**
 * Legacy `select_password_from_list` (original/src/game/passwd.c) and `select_from_list`
 * (original/src/game/sellist.c): a black box in a brighter band with a big title and up to [LIST_LENGTH]
 * entries, the selected one marked with `>`. Up/Down/PgUp/PgDn move, Enter picks, ESC cancels.
 */
@Composable
fun PickListScreen(
    title: String,
    entries: List<String>,
    layout: PickListLayout,
    onSelect: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember(entries) { mutableIntStateOf(0) }
    var start by remember(entries) { mutableIntStateOf(0) }

    fun moveTo(target: Int) {
        selected = target.coerceIn(0, (entries.size - 1).coerceAtLeast(0))
        if (start > selected) start = selected
        if (start < selected - (LIST_LENGTH - 1)) start = selected - (LIST_LENGTH - 1)
    }

    Mode13View(
        modifier = modifier,
        onKey = { event ->
            when (event.key) {
                Key.DirectionUp -> moveTo(selected - 1)
                Key.DirectionDown -> moveTo(selected + 1)
                Key.PageUp -> moveTo(selected - LIST_LENGTH)
                Key.PageDown -> moveTo(selected + LIST_LENGTH)
                Key.Enter, Key.NumPadEnter -> entries.getOrNull(selected)?.let(onSelect) ?: onCancel()
                Key.Escape -> onCancel()
                else -> return@Mode13View false
            }
            true
        },
    ) {
        panel()
        addBar(layout.boxLeft, 20, layout.boxWidth, 160, LegacyColors.BAR_ADD)
        bar(layout.boxLeft + 5, 25, layout.boxWidth - 10, 150, LegacyColors.BLACK)
        bigTextCentre(title, 160, 29, LegacyColors.HIGH)
        var y = 33 + LegacyFonts.BIG_HEIGHT
        for (index in start until minOf(start + LIST_LENGTH, entries.size)) {
            val marker = if (index == selected) '>' else ' '
            val width = overwriteText(layout.textLeft, y, LegacyColors.HIGH, "$marker ${entries[index]}")
            bar(layout.textLeft + width, y, 220 - layout.textLeft - width, 5, LegacyColors.BLACK)
            y += 6
        }
    }
}

/**
 * Legacy `message()` + `wait_yesno()` (original/src/game/prompt.c): a question in the message band that only
 * Y or N answer.
 */
@Composable
fun YesNoScreen(
    question: String,
    onYes: () -> Unit,
    onNo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val touch = LocalTouchScreen.current
    Mode13View(
        modifier = modifier,
        onKey = { event ->
            when (event.key) {
                Key.Y -> onYes()
                Key.N -> onNo()
                else -> return@Mode13View false
            }
            true
        },
        // On a touch screen: YES on the left, NO on the right.
        onTap = if (touch) ({ x, _ -> if (x < 160) onYes() else onNo() }) else null,
    ) {
        val length = question.length * 6
        panel()
        addBar((300 - length) / 2, 85, length + 20, 30, LegacyColors.BAR_ADD)
        writeText((320 - length) / 2, 98, LegacyColors.HIGH, question)
        if (touch) {
            addBar(50, 130, 80, 24, LegacyColors.BAR_ADD)
            addBar(190, 130, 80, 24, LegacyColors.BAR_ADD)
            writeText(81, 139, LegacyColors.HIGH, "YES")
            writeText(224, 139, LegacyColors.HIGH, "NO")
        }
    }
}


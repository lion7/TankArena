package com.tankarena.ui.compose.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp

/** Legacy `list_length` of `select_password_from_list` and `select_from_list`. */
private const val LIST_LENGTH = 20

/**
 * Legacy `read_string` (src/game/prompt.c): a one-line bar in the middle of the panel screen with
 * the prompt, the text typed so far and a green cursor block. The text starts as [initialText]
 * (the legacy code keeps the last code, so after a win it already holds the next one). Enter
 * submits, ESC cancels.
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
    BarScreen(
        modifier = modifier,
        onKey = { event ->
            when (event.key) {
                Key.Enter, Key.NumPadEnter -> onSubmit(text)
                Key.Escape -> onCancel()
                Key.Backspace -> text = text.backspaceInPrompt()
                else -> {
                    val typed = promptCharFor(event.key) ?: return@BarScreen false
                    text = text.typeInPrompt(typed, maxLength)
                }
            }
            true
        },
    ) {
        Box {
            // Reserve the full width up front, as the legacy bar is sized for prompt + maxlen + 1.
            Text(text = prompt + " ".repeat(maxLength + 1), color = Color.Transparent, style = RetroTypography.Line)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = prompt + text, color = Color.White, style = RetroTypography.Line)
                Box(
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .size(width = 9.dp, height = 11.dp)
                        .background(RetroColors.PromptCursor),
                )
            }
        }
    }
}

/** Legacy `message_key` (src/game/prompt.c): a one-line message bar; any key dismisses it. */
@Composable
fun MessageBarScreen(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BarScreen(
        modifier = modifier,
        onKey = {
            onDismiss()
            true
        },
    ) {
        Text(text = message, color = Color.White, style = RetroTypography.Line)
    }
}

/**
 * Legacy `select_password_from_list` (src/game/passwd.c) and `select_from_list`
 * (src/game/sellist.c): a panel with a big title and up to [LIST_LENGTH] entries, the selected
 * one marked with `>`. Up/Down/PgUp/PgDn move, Enter picks, ESC cancels.
 */
@Composable
fun PickListScreen(
    title: String,
    entries: List<String>,
    onSelect: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember(entries) { mutableIntStateOf(0) }
    var start by remember(entries) { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun moveTo(target: Int) {
        selected = target.coerceIn(0, (entries.size - 1).coerceAtLeast(0))
        if (start > selected) start = selected
        if (start < selected - (LIST_LENGTH - 1)) start = selected - (LIST_LENGTH - 1)
    }

    TiledPanelBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .retroPanel()
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionUp -> moveTo(selected - 1)
                        Key.DirectionDown -> moveTo(selected + 1)
                        Key.PageUp -> moveTo(selected - LIST_LENGTH)
                        Key.PageDown -> moveTo(selected + LIST_LENGTH)
                        Key.Enter, Key.NumPadEnter -> entries.getOrNull(selected)?.let(onSelect) ?: onCancel()
                        Key.Escape -> onCancel()
                        else -> return@onPreviewKeyEvent false
                    }
                    true
                }
                .widthIn(min = 420.dp)
                .padding(horizontal = 36.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = title, color = RetroColors.Title, style = RetroTypography.Title)
            Spacer(modifier = Modifier.height(16.dp))
            Column(
                modifier = Modifier.height(22.dp * LIST_LENGTH),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                entries.drop(start).take(LIST_LENGTH).forEachIndexed { offset, entry ->
                    val isSelected = start + offset == selected
                    Text(
                        text = (if (isSelected) "> " else "  ") + entry,
                        color = if (isSelected) RetroColors.ItemSelected else RetroColors.Title,
                        style = RetroTypography.Line,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** The tiled panel screen with one centred bar, shared by [PromptBarScreen] and [MessageBarScreen]. */
@Composable
private fun BarScreen(
    onKey: (KeyEvent) -> Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    TiledPanelBackground(modifier = modifier) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .retroPanel()
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event -> event.type == KeyEventType.KeyDown && onKey(event) }
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            content()
        }
    }
}

/** The double blue frame on black of the legacy `m13_add_bar` panels, as in [RetroMenuScreen]. */
private fun Modifier.retroPanel(): Modifier = this
    .border(width = 3.dp, color = RetroColors.PanelBorderOuter, shape = RectangleShape)
    .padding(3.dp)
    .border(width = 1.dp, color = RetroColors.PanelBorderInner, shape = RectangleShape)
    .background(RetroColors.PanelFill)

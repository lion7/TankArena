package com.tankarena.ui.compose.extended

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The Extended ruleset's look (ADR 0023): dark, with the game's sand colour as accent. */
internal object ExtendedColors {
    val background = Color(0xFF14161A)
    val panel = Color(0xFF1E2228)
    val selected = Color(0xFF3A3220)
    val accent = Color(0xFFE0B04A)
    val text = Color(0xFFE8E6E1)
    val dim = Color(0xFF8C8A85)
    val disabled = Color(0xFF55534F)
}

@Composable
fun ExtendedTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = ExtendedColors.accent,
            background = ExtendedColors.background,
            surface = ExtendedColors.panel,
            onBackground = ExtendedColors.text,
            onSurface = ExtendedColors.text,
        ),
        content = content,
    )
}

/**
 * A full-screen Extended page: [title] at the top, [content] below. ESC calls [onBack]; [onKey] sees
 * every other key first.
 */
@Composable
fun ExtendedPage(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    footer: String? = null,
    onKey: (KeyEvent) -> Boolean = { false },
    content: @Composable () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    ExtendedTheme {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF22262E), ExtendedColors.background)))
                .focusRequester(focus)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    if (onKey(event)) return@onPreviewKeyEvent true
                    if (event.key == Key.Escape && onBack != null) {
                        onBack()
                        true
                    } else {
                        false
                    }
                }
                .padding(horizontal = 48.dp, vertical = 32.dp),
        ) {
            Text(title, color = ExtendedColors.accent, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            if (subtitle != null) Text(subtitle, color = ExtendedColors.dim, fontSize = 15.sp)
            Spacer(Modifier.height(24.dp))
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) { content() }
            if (footer != null) {
                Spacer(Modifier.height(12.dp))
                Text(footer, color = ExtendedColors.dim, fontSize = 13.sp)
            }
        }
    }
}

/** One entry of an [ExtendedList]: a label, an optional detail line on the right, and whether it can be picked. */
data class ListEntry(val label: String, val detail: String = "", val enabled: Boolean = true)

/**
 * A list moved through with Up/Down (Page Up/Down, Home/End) or the mouse; Enter or a click picks the
 * selected entry, Left/Right go to [onStep] (a setting's value down or up). [onSelect] follows the
 * selection, for a details pane next to it. The selection stays where it is when the entries' texts
 * change (a setting's new value).
 */
@Composable
fun ExtendedList(
    entries: List<ListEntry>,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    initial: Int = entries.indexOfFirst { it.enabled }.coerceAtLeast(0),
    onSelect: (Int) -> Unit = {},
    keys: KeyRouter? = null,
    onStep: ((index: Int, up: Boolean) -> Unit)? = null,
) {
    var selected by remember(entries.size) { mutableIntStateOf(initial.coerceIn(0, (entries.size - 1).coerceAtLeast(0))) }
    val listState = rememberLazyListState()
    LaunchedEffect(selected) {
        onSelect(selected)
        if (entries.isNotEmpty()) listState.animateScrollToItem((selected - 3).coerceAtLeast(0))
    }
    fun move(by: Int) {
        if (entries.isEmpty()) return
        var index = selected
        repeat(entries.size) {
            index = (index + by).coerceIn(0, entries.size - 1)
            if (entries[index].enabled) {
                selected = index
                return
            }
        }
    }
    keys?.handler = handler@{ event ->
        when (event.key) {
            Key.DirectionUp -> move(-1)
            Key.DirectionDown -> move(1)
            Key.PageUp -> move(-8)
            Key.PageDown -> move(8)
            Key.MoveHome -> move(-entries.size)
            Key.MoveEnd -> move(entries.size)
            Key.Enter, Key.NumPadEnter -> if (entries.getOrNull(selected)?.enabled == true) onPick(selected)
            Key.DirectionLeft, Key.DirectionRight -> {
                val step = onStep ?: return@handler false
                if (entries.getOrNull(selected)?.enabled == true) step(selected, event.key == Key.DirectionRight)
            }
            else -> return@handler false
        }
        true
    }
    LazyColumn(state = listState, modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        itemsIndexed(entries) { index, entry ->
            val isSelected = index == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isSelected) ExtendedColors.selected else Color.Transparent, RoundedCornerShape(6.dp))
                    .clickable(enabled = entry.enabled) {
                        selected = index
                        onPick(index)
                    }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val color = when {
                    !entry.enabled -> ExtendedColors.disabled
                    isSelected -> ExtendedColors.accent
                    else -> ExtendedColors.text
                }
                Text(entry.label, color = color, fontSize = 20.sp, modifier = Modifier.weight(1f))
                if (entry.detail.isNotEmpty()) Text(entry.detail, color = ExtendedColors.dim, fontSize = 15.sp)
            }
        }
    }
}

/** Passes the page's keys to whichever list or control currently takes them. */
class KeyRouter {
    var handler: (KeyEvent) -> Boolean = { false }
    fun handle(event: KeyEvent): Boolean = handler(event)
}

/** A list on the left and a details pane on the right. */
@Composable
fun ListWithDetails(list: @Composable () -> Unit, details: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) { list() }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(ExtendedColors.panel, RoundedCornerShape(10.dp))
                .padding(20.dp),
        ) { details() }
    }
}

/** A fixed-width gap, for rows of controls. */
@Composable
fun Gap(width: Int = 16) = Spacer(Modifier.width(width.dp))

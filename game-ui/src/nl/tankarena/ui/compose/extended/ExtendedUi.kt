package nl.tankarena.ui.compose.extended

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import nl.tankarena.ui.compose.MODE13_FADE_MILLIS
import nl.tankarena.ui.compose.refocusOnTouch
import nl.tankarena.ui.compose.rememberFadeIn

/** Something to do on a page that a key does: a label to tap or click as well. */
class PageAction(val label: String, val onClick: () -> Unit)

/**
 * A full-screen Extended page in the original's look (ADR 0023): the panel pattern, and on it, like
 * legacy `menu()`, a black box in a brighter band with [title] centred at the top, [content] below
 * and [footer] (the keys) at the bottom. The page fades in like the 320×200 screens. ESC (Android's
 * Back, a pad's B) calls [onBack]; [onKey] sees every other key first. [actions] and Back are also
 * labels along the bottom to tap or click.
 */
@Composable
fun ExtendedPage(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    footer: String? = null,
    onKey: (KeyEvent) -> Boolean = { false },
    actions: List<PageAction> = emptyList(),
    content: @Composable () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val fade = rememberFadeIn(MODE13_FADE_MILLIS)
    val density = LocalDensity.current.density
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focus)
            .refocusOnTouch(focus)
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
            },
        contentAlignment = Alignment.Center,
    ) {
        val pixel = retroPixel(constraints.maxWidth, constraints.maxHeight, density)
        CompositionLocalProvider(LocalRetroPixel provides pixel) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .retroPanel()
                    .drawWithContent {
                        drawContent()
                        if (fade.value < 1f) drawRect(Color.Black.copy(alpha = 1f - fade.value))
                    }
                    .padding(retroDp(PAGE_MARGIN)),
                contentAlignment = Alignment.Center,
            ) {
                RetroBox(modifier = Modifier.fillMaxHeight().widthIn(max = retroDp(MAX_BOX_WIDTH)).fillMaxWidth(), band = 8) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        RetroText(title.uppercase(), color = RetroColors.high, font = RetroFont.TITLE, maxLines = 1, centred = true, modifier = Modifier.fillMaxWidth())
                        if (subtitle != null) {
                            Spacer(Modifier.heightIn(min = retroDp(4)))
                            RetroText(subtitle, color = RetroColors.normal, font = RetroFont.SMALL, maxLines = 3, centred = true, modifier = Modifier.fillMaxWidth())
                        }
                        Spacer(Modifier.heightIn(min = retroDp(10)))
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) { content() }
                        val labels = listOfNotNull(onBack?.let { PageAction("< Back", it) }) + actions
                        if (labels.isNotEmpty() || footer != null) Spacer(Modifier.heightIn(min = retroDp(6)))
                        if (labels.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(retroDp(12)), verticalAlignment = Alignment.CenterVertically) {
                                for (action in labels) RetroButton(action.label, action.onClick)
                            }
                        }
                        if (footer != null) RetroText(footer, color = RetroColors.dim, font = RetroFont.SMALL, maxLines = 3)
                    }
                }
            }
        }
    }
}

/** Legacy pixels of panel around the page's box. */
private const val PAGE_MARGIN = 16

/** On a wide window the box stops at this many legacy pixels across; the panel shows beside it. */
private const val MAX_BOX_WIDTH = 760

/** The smallest a row or label is that a finger taps. */
private val TOUCH_TARGET = 44.dp

/**
 * A label to tap or click: grey, white when the mouse is over it or it is pressed, framed like a
 * selected entry.
 */
@Composable
fun RetroButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val lit = enabled && (hovered || pressed)
    Box(
        modifier = modifier
            .heightIn(min = TOUCH_TARGET)
            .then(if (lit) Modifier.retroFrame(2) else Modifier)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = retroDp(6)),
        contentAlignment = Alignment.Center,
    ) {
        RetroText(label.uppercase(), color = if (!enabled) RetroColors.disabled else if (lit) RetroColors.high else RetroColors.normal, maxLines = 1)
    }
}

/**
 * One entry of an [ExtendedList]: a label, an optional detail on the right, whether it can be
 * picked, and whether Left/Right (or the `<` `>` beside the detail) step its value.
 */
data class ListEntry(val label: String, val detail: String = "", val enabled: Boolean = true, val steppable: Boolean = false)

/**
 * A list in the original's menu style: the entries grey, the selected one white in a frame. Moved
 * through with Up/Down (Page Up/Down, Home/End), a pad, the mouse pointer or by dragging; Enter, a
 * click or a tap picks. Left/Right, or tapping `<` `>` on a steppable entry, go to [onStep] (a
 * setting's value down or up). [onSelect] follows the selection, for a details pane next to it.
 * The selection stays where it is when the entries' texts change (a setting's new value).
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
    // Keys move the list to the selection; the mouse pointer selects what it is over and must not.
    var follow by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    LaunchedEffect(selected) { onSelect(selected) }
    LaunchedEffect(follow) {
        if (entries.isEmpty()) return@LaunchedEffect
        val visible = listState.layoutInfo.visibleItemsInfo
        val item = visible.firstOrNull { it.index == selected }
        val fully = item != null && item.offset >= listState.layoutInfo.viewportStartOffset &&
            item.offset + item.size <= listState.layoutInfo.viewportEndOffset
        if (!fully) listState.animateScrollToItem((selected - 2).coerceAtLeast(0))
    }
    fun move(by: Int) {
        if (entries.isEmpty()) return
        var index = selected
        repeat(entries.size) {
            index = (index + by).coerceIn(0, entries.size - 1)
            if (entries[index].enabled) {
                selected = index
                follow++
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
    BoxWithConstraints(modifier = modifier) {
        val detailWidth = maxWidth * 0.5f
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(entries) { index, entry ->
                val isSelected = index == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = TOUCH_TARGET)
                        .then(if (isSelected && entry.enabled) Modifier.retroFrame(2) else Modifier)
                        .pointerInput(index, entry.enabled) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val mouse = event.changes.firstOrNull()?.type == PointerType.Mouse
                                    if (mouse && entry.enabled && (event.type == PointerEventType.Enter || event.type == PointerEventType.Move)) selected = index
                                }
                            }
                        }
                        .clickable(enabled = entry.enabled, indication = null, interactionSource = null) {
                            selected = index
                            onPick(index)
                        }
                        .padding(horizontal = retroDp(8), vertical = retroDp(3)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val color = when {
                        !entry.enabled -> if (entry.detail.isEmpty() && entry.label.endsWith(":")) RetroColors.accent else RetroColors.disabled
                        isSelected -> RetroColors.high
                        else -> RetroColors.normal
                    }
                    RetroText(entry.label, color = color, maxLines = 1, modifier = Modifier.weight(1f))
                    val stepper = onStep != null && entry.steppable && entry.enabled
                    if (stepper) {
                        StepArrow("<") {
                            selected = index
                            onStep(index, false)
                        }
                    }
                    if (entry.detail.isNotEmpty()) {
                        RetroText(
                            entry.detail,
                            color = if (isSelected) RetroColors.text else RetroColors.dim,
                            maxLines = 1,
                            modifier = Modifier.widthIn(max = detailWidth),
                        )
                    }
                    if (stepper) {
                        StepArrow(">") {
                            selected = index
                            onStep(index, true)
                        }
                    }
                }
            }
        }
        // The list goes on above or below: arrows at the right edge, as the original's file lists.
        if (listState.canScrollBackward) RetroText("^", color = RetroColors.high, modifier = Modifier.align(Alignment.TopEnd))
        if (listState.canScrollForward) RetroText("v", color = RetroColors.high, modifier = Modifier.align(Alignment.BottomEnd))
    }
}

/** A `<` or `>` beside a setting's value, a finger wide. */
@Composable
private fun StepArrow(arrow: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.width(TOUCH_TARGET).heightIn(min = TOUCH_TARGET).clickable(indication = null, interactionSource = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        RetroText(arrow, color = RetroColors.high)
    }
}

/**
 * The places of a lobby or join screen, each in a frame: in one row, or two to a row when they do
 * not fit (a phone held upright).
 */
@Composable
fun PlaceBoxes(count: Int, content: @Composable ColumnScope.(place: Int) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val gap = retroDp(8)
        val perRow = if (maxWidth < retroDp(PLACE_WIDTH) * count) 2 else count
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            for (first in 0 until count step perRow) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (place in first until minOf(first + perRow, count)) {
                        RetroBox(modifier = Modifier.weight(1f).heightIn(min = retroDp(60)), band = 2, padding = 6) {
                            Column(verticalArrangement = Arrangement.spacedBy(retroDp(3))) { content(place) }
                        }
                    }
                }
            }
        }
    }
}

/** The narrowest a place's box is before they go two to a row, in legacy pixels. */
private const val PLACE_WIDTH = 150

/** Passes the page's keys to whichever list or control currently takes them. */
class KeyRouter {
    var handler: (KeyEvent) -> Boolean = { false }
    fun handle(event: KeyEvent): Boolean = handler(event)
}

/**
 * A list and a details pane: side by side, or on a screen higher than wide (a phone held upright)
 * the details under the list.
 */
@Composable
fun ListWithDetails(list: @Composable () -> Unit, details: @Composable () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val gap = retroDp(10)
        if (maxWidth < maxHeight) {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) { list() }
                RetroBox(modifier = Modifier.weight(1f).fillMaxWidth(), band = 2, padding = 6) { details() }
            }
        } else {
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) { list() }
                RetroBox(modifier = Modifier.weight(1f).fillMaxHeight(), band = 2, padding = 6) { details() }
            }
        }
    }
}

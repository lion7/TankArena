package com.tankarena.ui.compose.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

data class RetroMenuItem(
    val label: String,
    val subtitle: String? = null,
    val enabled: Boolean = true,
    val onActivate: () -> Unit,
)

@Composable
fun RetroMenuScreen(
    title: String,
    items: List<RetroMenuItem>,
    modifier: Modifier = Modifier,
    initialSelection: Int = items.indexOfFirst { it.enabled }.coerceAtLeast(0),
    onCancel: (() -> Unit)? = null,
    footerHint: String? = null,
) {
    TiledPanelBackground(modifier = modifier) {
        RetroMenuPanel(
            title = title,
            items = items,
            initialSelection = initialSelection,
            onCancel = onCancel,
            footerHint = footerHint,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun RetroMenuPanel(
    title: String,
    items: List<RetroMenuItem>,
    initialSelection: Int,
    onCancel: (() -> Unit)?,
    footerHint: String?,
    modifier: Modifier = Modifier,
) {
    var selected by remember(items) {
        mutableStateOf(initialSelection.coerceIn(0, (items.size - 1).coerceAtLeast(0)))
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Surface(
        modifier = modifier
            .widthIn(min = 480.dp, max = 720.dp)
            .wrapContentHeight()
            .border(width = 3.dp, color = RetroColors.PanelBorderOuter, shape = RectangleShape)
            .padding(3.dp)
            .border(width = 1.dp, color = RetroColors.PanelBorderInner, shape = RectangleShape)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> {
                        selected = previousEnabled(items, selected)
                        true
                    }
                    Key.DirectionDown -> {
                        selected = nextEnabled(items, selected)
                        true
                    }
                    Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                        items.getOrNull(selected)?.takeIf { it.enabled }?.onActivate?.invoke()
                        true
                    }
                    Key.Escape -> {
                        onCancel?.invoke()
                        onCancel != null
                    }
                    else -> false
                }
            },
        color = RetroColors.PanelFill,
        shape = RectangleShape,
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 36.dp, vertical = 28.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                color = RetroColors.Title,
                style = RetroTypography.Title,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(12.dp))
            RetroMenuItemList(
                items = items,
                selected = selected,
                onHover = { index -> if (items[index].enabled) selected = index },
                onClick = { index ->
                    val item = items[index]
                    if (item.enabled) {
                        selected = index
                        item.onActivate()
                    }
                },
            )
            if (footerHint != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = footerHint,
                    color = RetroColors.Footer,
                    style = RetroTypography.Footer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun RetroMenuItemList(
    items: List<RetroMenuItem>,
    selected: Int,
    onHover: (Int) -> Unit,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasSubtitles = items.any { it.subtitle != null }
    if (hasSubtitles && items.size > 6) {
        val listState = rememberLazyListState()
        LaunchedEffect(selected) {
            if (selected in items.indices) listState.animateScrollToItem(selected)
        }
        LazyColumn(
            modifier = modifier
                .fillMaxWidth()
                .height(360.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(items) { index, item ->
                RetroMenuRow(
                    item = item,
                    isSelected = index == selected,
                    onHover = { onHover(index) },
                    onClick = { onClick(index) },
                )
            }
        }
    } else {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items.forEachIndexed { index, item ->
                RetroMenuRow(
                    item = item,
                    isSelected = index == selected,
                    onHover = { onHover(index) },
                    onClick = { onClick(index) },
                )
            }
        }
    }
}

@Composable
private fun RetroMenuRow(
    item: RetroMenuItem,
    isSelected: Boolean,
    onHover: () -> Unit,
    onClick: () -> Unit,
) {
    val color = when {
        !item.enabled -> RetroColors.ItemDisabled
        isSelected -> RetroColors.ItemSelected
        else -> RetroColors.ItemIdle
    }
    val rowModifier = Modifier
        .fillMaxWidth()
        .background(if (isSelected && item.enabled) Color(0xFF0E1D33) else Color.Transparent)
        .padding(PaddingValues(horizontal = 12.dp, vertical = 4.dp))
        .pointerInput(item.enabled) {
            if (!item.enabled) return@pointerInput
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == PointerEventType.Enter || event.type == PointerEventType.Move) {
                        onHover()
                    }
                }
            }
        }
        .pointerInput(item.enabled) {
            if (!item.enabled) return@pointerInput
            detectTapGestures(onTap = { onClick() })
        }

    Column(
        modifier = rowModifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = item.label,
            color = color,
            style = RetroTypography.Item,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (item.subtitle != null) {
            Text(
                text = item.subtitle,
                color = if (item.enabled) RetroColors.Subtitle else RetroColors.ItemDisabled,
                style = RetroTypography.Subtitle,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(Alignment.CenterHorizontally),
            )
        }
    }
}

private fun nextEnabled(items: List<RetroMenuItem>, current: Int): Int {
    if (items.isEmpty()) return 0
    var index = current
    repeat(items.size) {
        index = (index + 1) % items.size
        if (items[index].enabled) return index
    }
    return current
}

private fun previousEnabled(items: List<RetroMenuItem>, current: Int): Int {
    if (items.isEmpty()) return 0
    var index = current
    repeat(items.size) {
        index = (index - 1 + items.size) % items.size
        if (items[index].enabled) return index
    }
    return current
}

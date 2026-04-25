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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.tankarena.ui.compose.MissionOutcome

@Composable
fun DebriefScreen(
    mission: MissionEntry,
    outcome: MissionOutcome,
    nextMission: MissionEntry?,
    onNextMission: (MissionEntry) -> Unit,
    onRetry: () -> Unit,
    onBackToMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = when (outcome) {
        MissionOutcome.WON -> "MISSION COMPLETE"
        MissionOutcome.LOST -> "MISSION FAILED"
    }
    val body = when (outcome) {
        MissionOutcome.WON -> mission.canonical.missionText.success
        MissionOutcome.LOST -> mission.canonical.missionText.failure
    }.ifBlank {
        when (outcome) {
            MissionOutcome.WON -> "Objective complete. Stand by for new orders."
            MissionOutcome.LOST -> "Mission lost. Regroup and try again."
        }
    }

    val items = buildList {
        if (outcome == MissionOutcome.WON && nextMission != null) {
            add(
                RetroMenuItem(
                    label = "Next Mission",
                    subtitle = nextMission.code,
                    onActivate = { onNextMission(nextMission) },
                ),
            )
        }
        add(RetroMenuItem(label = "Retry", onActivate = onRetry))
        add(RetroMenuItem(label = "Back to Menu", onActivate = onBackToMenu))
    }

    TiledPanelBackground(modifier = modifier) {
        DebriefPanel(
            title = title,
            body = body,
            missionCode = mission.code,
            items = items,
            onCancel = onBackToMenu,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun DebriefPanel(
    title: String,
    body: String,
    missionCode: String,
    items: List<RetroMenuItem>,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember(items) { mutableStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Surface(
        modifier = modifier
            .widthIn(min = 520.dp, max = 760.dp)
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
                        selected = (selected - 1 + items.size) % items.size
                        true
                    }
                    Key.DirectionDown -> {
                        selected = (selected + 1) % items.size
                        true
                    }
                    Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                        items.getOrNull(selected)?.onActivate?.invoke()
                        true
                    }
                    Key.Escape -> {
                        onCancel()
                        true
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
            Text(
                text = missionCode,
                color = RetroColors.Subtitle,
                style = RetroTypography.Subtitle,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            BriefingBox(body = body)
            Spacer(modifier = Modifier.height(8.dp))
            DebriefMenu(
                items = items,
                selected = selected,
                onHover = { selected = it },
                onClick = { index ->
                    selected = index
                    items[index].onActivate()
                },
            )
            Text(
                text = "\u2191 / \u2193 select   ENTER confirm   ESC menu",
                color = RetroColors.Footer,
                style = RetroTypography.Footer,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun BriefingBox(body: String) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .background(Color(0xFF050B14))
            .border(width = 1.dp, color = RetroColors.PanelBorderInner, shape = RectangleShape)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(scrollState),
    ) {
        Text(
            text = body,
            color = RetroColors.ItemIdle,
            style = RetroTypography.Subtitle,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DebriefMenu(
    items: List<RetroMenuItem>,
    selected: Int,
    onHover: (Int) -> Unit,
    onClick: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items.forEachIndexed { index, item ->
            DebriefRow(
                item = item,
                isSelected = index == selected,
                onHover = { onHover(index) },
                onClick = { onClick(index) },
            )
        }
    }
}

@Composable
private fun DebriefRow(
    item: RetroMenuItem,
    isSelected: Boolean,
    onHover: () -> Unit,
    onClick: () -> Unit,
) {
    val color = if (isSelected) RetroColors.ItemSelected else RetroColors.ItemIdle
    val rowModifier = Modifier
        .fillMaxWidth()
        .background(if (isSelected) Color(0xFF0E1D33) else Color.Transparent)
        .padding(PaddingValues(horizontal = 12.dp, vertical = 4.dp))
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == PointerEventType.Enter || event.type == PointerEventType.Move) {
                        onHover()
                    }
                }
            }
        }
        .pointerInput(Unit) {
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
                color = RetroColors.Subtitle,
                style = RetroTypography.Subtitle,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

package com.tankarena.ui.compose.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun MissionSelectScreen(
    missions: List<MissionEntry>?,
    onSelect: (MissionEntry) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        missions == null -> MissionLoadingScreen(modifier = modifier)
        missions.isEmpty() -> MissionEmptyScreen(onCancel = onCancel, modifier = modifier)
        else -> {
            val items = missions.map { mission ->
                RetroMenuItem(
                    label = mission.code,
                    subtitle = mission.briefingPreview.ifBlank { null },
                    onActivate = { onSelect(mission) },
                )
            }
            RetroMenuScreen(
                title = "SELECT MISSION",
                items = items,
                modifier = modifier,
                onCancel = onCancel,
                footerHint = "\u2191 / \u2193 select   ENTER launch   ESC back",
            )
        }
    }
}

@Composable
private fun MissionLoadingScreen(modifier: Modifier = Modifier) {
    TiledPanelBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator(color = RetroColors.PanelBorderOuter)
            Text(
                text = "SCANNING FOR MISSIONS\u2026",
                color = RetroColors.Title,
                style = RetroTypography.Item,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MissionEmptyScreen(onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val items = listOf(
        RetroMenuItem(label = "Leave this menu", onActivate = onCancel),
    )
    RetroMenuScreen(
        title = "NO MISSIONS FOUND",
        items = items,
        modifier = modifier,
        onCancel = onCancel,
        footerHint = "Place .MAP files under MAPS/ and try again.",
    )
}

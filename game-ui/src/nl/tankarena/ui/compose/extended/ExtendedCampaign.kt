package nl.tankarena.ui.compose.extended

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.tankarena.ui.compose.GameMode
import nl.tankarena.ui.compose.menu.MissionEntry
import nl.tankarena.ui.compose.menu.MissionRecord
import nl.tankarena.ui.compose.menu.findByCode

/**
 * The Extended campaign: the missions reached so far (and the public ones), with the briefing next
 * to the list, and a field for a code. [onStart] gets the code to start; [codes] are the known codes
 * that start a one-player mission; [lastCode] is where the list starts.
 */
@Composable
fun CampaignScreen(
    missions: List<MissionEntry>,
    codes: List<String>,
    lastCode: String,
    onStart: (String) -> Unit,
    onBack: () -> Unit,
    mode: GameMode = GameMode.SINGLE_PLAYER_VS_COMPUTER,
    title: String = "Campaign",
    /** A mission's record (kept across restarts), null when it was never played. */
    recordOf: (MissionEntry) -> MissionRecord? = { null },
    /**
     * The record of a mission that is also played the other way (alone or together), with the
     * name of that way; null when there is none. Shown apart, under this campaign's own.
     */
    otherRecordOf: (MissionEntry) -> Pair<String, MissionRecord>? = { null },
) {
    val keys = remember { KeyRouter() }
    var entering by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    val reached = codes.mapNotNull { code -> missions.findByCode(code)?.let { code to it } }
    val entries = listOf(ListEntry("Enter a code…")) + reached.map { (code, mission) ->
        val name = mission.sidecar.metadata.name.ifBlank { mission.baseName.uppercase() }
        // A mission ever won is ticked.
        val won = if ((recordOf(mission)?.won ?: 0) > 0) "✓ " else ""
        ListEntry(code, won + if (name.equals(code, ignoreCase = true)) "" else name)
    }
    val initial = reached.indexOfFirst { it.first.equals(lastCode, ignoreCase = true) }.let { if (it >= 0) it + 1 else 0 }
    var selected by remember { mutableIntStateOf(initial) }
    val fieldFocus = remember { FocusRequester() }
    ExtendedPage(
        title = title,
        subtitle = if (mode == GameMode.DUAL_PLAYER_VS_COMPUTER) "Two players against the computer" else "Your missions, one after another",
        onBack = { if (entering) entering = false else onBack() },
        footer = if (entering) "Enter starts the mission, ESC goes back" else "Enter plays the mission; the list holds every code you have reached",
        onKey = { event -> if (entering) false else keys.handle(event) },
    ) {
        ListWithDetails(
            list = {
                if (entering) {
                    LaunchedEffect(Unit) { fieldFocus.requestFocus() }
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it.uppercase().take(20) },
                        label = { Text("Mission code") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(fieldFocus)
                            .onPreviewKeyEvent { event ->
                                if (event.key == Key.Enter && event.type == KeyEventType.KeyDown) {
                                    if (typed.isNotBlank()) onStart(typed.trim())
                                    true
                                } else {
                                    false
                                }
                            },
                    )
                } else {
                    ExtendedList(
                        entries,
                        onPick = { index -> if (index == 0) entering = true else onStart(reached[index - 1].first) },
                        initial = initial,
                        onSelect = { selected = it },
                        keys = keys,
                    )
                }
            },
            details = {
                val mission = reached.getOrNull(selected - 1)?.second
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (mission == null || entering) {
                        Text("A mission code starts a mission and every one after it.", color = ExtendedColors.dim, fontSize = 16.sp)
                    } else {
                        val metadata = mission.sidecar.metadata
                        Text(metadata.name.ifBlank { mission.baseName.uppercase() }, color = ExtendedColors.accent, fontSize = 22.sp)
                        Text(
                            "${metadata.world.name.lowercase()}${if (metadata.night) ", night" else ""} — ${metadata.widthTiles}×${metadata.heightTiles}",
                            color = ExtendedColors.dim,
                            fontSize = 14.sp,
                        )
                        Text(recordOf(mission)?.text() ?: "Not played yet", color = ExtendedColors.text, fontSize = 15.sp)
                        otherRecordOf(mission)?.let { (way, record) ->
                            Text("$way: ${record.text().replaceFirstChar { it.lowercase() }}", color = ExtendedColors.dim, fontSize = 14.sp)
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(mission.sidecar.missionText.briefing.trim(), color = ExtendedColors.text, fontSize = 15.sp)
                    }
                }
            },
        )
    }
}

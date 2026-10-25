package nl.tankarena.ui.compose.extended

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
        footer = if (entering) "Enter starts the mission, ESC goes back" else "Enter plays the mission",
        actions = if (entering) listOf(PageAction("Start") { if (typed.isNotBlank()) onStart(typed.trim()) }) else emptyList(),
        onKey = { event -> if (entering) false else keys.handle(event) },
    ) {
        ListWithDetails(
            list = {
                if (entering) {
                    LaunchedEffect(Unit) { fieldFocus.requestFocus() }
                    RetroTextField(
                        value = typed,
                        onValueChange = { typed = it.uppercase().take(20) },
                        label = "Mission code",
                        onDone = { if (typed.isNotBlank()) onStart(typed.trim()) },
                        modifier = Modifier.fillMaxWidth().focusRequester(fieldFocus),
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
                        RetroText("A mission code starts a mission and every one after it.", color = RetroColors.dim)
                    } else {
                        val metadata = mission.sidecar.metadata
                        RetroText(metadata.name.ifBlank { mission.baseName.uppercase() }, color = RetroColors.accent)
                        RetroText(
                            "${metadata.world.name.lowercase()}${if (metadata.night) ", night" else ""} — ${metadata.widthTiles}×${metadata.heightTiles}",
                            color = RetroColors.dim,
                            font = RetroFont.SMALL,
                        )
                        RetroText(recordOf(mission)?.text() ?: "Not played yet", color = RetroColors.normal)
                        otherRecordOf(mission)?.let { (way, record) ->
                            RetroText("$way: ${record.text().replaceFirstChar { it.lowercase() }}", color = RetroColors.dim)
                        }
                        Spacer(Modifier.height(retroDp(8)))
                        RetroText(mission.sidecar.missionText.briefing.trim())
                    }
                }
            },
        )
    }
}

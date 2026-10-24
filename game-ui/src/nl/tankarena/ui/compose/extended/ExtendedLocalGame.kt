package nl.tankarena.ui.compose.extended

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.tankarena.ui.compose.menu.GameOptions
import nl.tankarena.ui.compose.menu.MissionCodes
import nl.tankarena.ui.compose.menu.MissionEntry

/** Legacy `map_contents` (MAPC_*): what a player-vs-player map has room for. */
private fun contentsText(contents: Int): String = buildList {
    if (contents and 1 != 0) add("tanks")
    if (contents and 2 != 0) add("helicopters")
    if (contents and 4 != 0) add("planes")
    if (contents and 8 != 0) add("race")
    if (contents and 16 != 0) add("flag hunt")
}.joinToString(", ").ifEmpty { "any vehicle" }

/** Extended local games: first whether the players play against each other or together. */
@Composable
fun LocalGameScreen(onVersus: () -> Unit, onTogether: () -> Unit, onBack: () -> Unit) {
    val keys = remember { KeyRouter() }
    val entries = listOf(
        ListEntry("Against each other", "player vs player, on a map of your choice") to onVersus,
        ListEntry("Together against the computer", "co-op, through missions of their own") to onTogether,
    )
    ExtendedPage(title = "Local game", subtitle = "Player one with keyboard and mouse, the others with gamepads", onBack = onBack, onKey = keys::handle) {
        ExtendedList(entries.map { it.first }, onPick = { entries[it].second() }, keys = keys)
    }
}

/** Extended local games against each other: the map, with the match options changed in place. */
@Composable
fun VersusMapsScreen(
    missions: List<MissionEntry>,
    options: GameOptions,
    lastMap: String,
    onOptions: (GameOptions) -> Unit,
    onVersus: (String) -> Unit,
    onBack: () -> Unit,
    /** The rounds each side has won on the maps played so far with these players. */
    series: List<Int> = listOf(0, 0),
) {
    val keys = remember { KeyRouter() }
    val maps = remember(missions) {
        MissionCodes.selectableMaps(missions).mapNotNull { name -> missions.firstOrNull { it.baseName.equals(name, ignoreCase = true) } }
    }
    val fixed = listOf(
        ListEntry("Rounds", if (options.rounds == GameOptions.ENDLESS) "endless" else options.rounds.toString()),
        ListEntry("Race laps", options.raceLaps.toString()),
        ListEntry("Losers tank", losersTankText(options.losersTank)),
        ListEntry("Maps:", enabled = false),
    )
    val entries = fixed + maps.map { ListEntry(it.baseName.uppercase(), it.sidecar.metadata.world.name.lowercase()) }
    val initial = maps.indexOfFirst { it.baseName.equals(lastMap, ignoreCase = true) }.let { if (it >= 0) it + fixed.size else 0 }
    var selected by remember { mutableIntStateOf(initial) }
    ExtendedPage(
        title = "Against each other",
        subtitle = if (series.any { it > 0 }) {
            "Series: team 1 ${series[0]} – ${series[1]} team 2 (the losers tank goes by it). Pick the next map"
        } else {
            "Pick a map; the match options are changed here too"
        },
        onBack = onBack,
        footer = "Enter picks; on the options Left/Right change them",
        onKey = keys::handle,
    ) {
        ListWithDetails(
            list = {
                ExtendedList(
                    entries,
                    onPick = { index ->
                        when (index) {
                            0 -> onOptions(options.nextRounds())
                            1 -> onOptions(options.nextLaps())
                            2 -> onOptions(options.nextLosersTank())
                            else -> maps.getOrNull(index - fixed.size)?.let { onVersus(it.baseName) }
                        }
                    },
                    initial = initial,
                    onSelect = { selected = it },
                    keys = keys,
                    onStep = { index, up ->
                        when (index) {
                            0 -> onOptions(options.stepped(ExtendedSetting.ROUNDS, up))
                            1 -> onOptions(options.stepped(ExtendedSetting.LAPS, up))
                            2 -> onOptions(options.stepped(ExtendedSetting.LOSERS_TANK, up))
                        }
                    },
                )
            },
            details = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    val map = maps.getOrNull(selected - fixed.size)
                    val text = when {
                        map != null -> null
                        selected == 0 -> "A match is won by winning more than half of its rounds."
                        selected == 1 -> "How many laps a race has."
                        selected == 2 -> "The player who is behind gets a stronger vehicle."
                        else -> ""
                    }
                    if (map != null) {
                        val metadata = map.sidecar.metadata
                        Text(map.baseName.uppercase(), color = ExtendedColors.accent, fontSize = 22.sp)
                        Text(
                            "${metadata.world.name.lowercase()}${if (metadata.night) ", night" else ""} — ${metadata.widthTiles}×${metadata.heightTiles} — ${contentsText(metadata.mapContents)}",
                            color = ExtendedColors.dim,
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(map.sidecar.missionText.briefing.trim(), color = ExtendedColors.text, fontSize = 15.sp)
                    } else {
                        Text(text.orEmpty(), color = ExtendedColors.dim, fontSize = 16.sp)
                    }
                }
            },
        )
    }
}

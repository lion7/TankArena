package nl.tankarena.ui.compose.extended

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import nl.tankarena.net.LanGame
import nl.tankarena.net.LobbyMember
import nl.tankarena.net.MAX_NET_PLAYERS
import nl.tankarena.ui.compose.GameMode
import nl.tankarena.ui.compose.menu.MissionCodes
import nl.tankarena.ui.compose.menu.MissionEntry
import nl.tankarena.ui.compose.menu.playableIn

/** Extended's network game (ADR 0024): host a game on this machine or join one. */
@Composable
fun NetworkMenuScreen(onHost: () -> Unit, onJoin: () -> Unit, onBack: () -> Unit) {
    val keys = remember { KeyRouter() }
    val entries = listOf(
        ListEntry("Host a game", "pick a map; the others join this machine") to onHost,
        ListEntry("Join a game", "type the address of the machine that hosts") to onJoin,
    )
    ExtendedPage(
        title = "Network game",
        subtitle = "Up to $MAX_NET_PLAYERS players on several machines, in two teams",
        onBack = onBack,
        footer = "Enter picks · ESC goes back",
        onKey = keys::handle,
    ) {
        ExtendedList(entries.map { it.first }, onPick = { entries[it].second() }, keys = keys)
    }
}

/**
 * The map of a hosted game: a mission the players play together against the computer, or a map
 * they play against each other on.
 */
@Composable
fun NetworkHostScreen(missions: List<MissionEntry>, onPick: (MissionEntry, GameMode) -> Unit, onBack: () -> Unit) {
    val keys = remember { KeyRouter() }
    val together = remember(missions) { missions.playableIn(GameMode.DUAL_PLAYER_VS_COMPUTER) }
    val versus = remember(missions) {
        MissionCodes.selectableMaps(missions).mapNotNull { name -> missions.firstOrNull { it.baseName.equals(name, ignoreCase = true) } }
    }
    val choices = together.map { it to GameMode.DUAL_PLAYER_VS_COMPUTER } + versus.map { it to GameMode.PLAYER_VS_PLAYER }
    val entries = buildList {
        add(ListEntry("Together against the computer:", enabled = false))
        together.forEach { add(ListEntry(it.code, it.sidecar.metadata.world.name.lowercase())) }
        add(ListEntry("Against each other, on:", enabled = false))
        versus.forEach { add(ListEntry(it.baseName.uppercase(), it.sidecar.metadata.world.name.lowercase())) }
    }
    // Entry index to choice: the two headings are not choices.
    fun choiceAt(index: Int): Pair<MissionEntry, GameMode>? = when {
        index in 1..together.size -> choices[index - 1]
        index > together.size + 1 -> choices.getOrNull(index - 2)
        else -> null
    }
    var selected by remember { mutableIntStateOf(1) }
    ExtendedPage(
        title = "Host a game",
        subtitle = "Pick the map",
        onBack = onBack,
        footer = "Enter picks · ESC goes back",
        onKey = keys::handle,
    ) {
        ListWithDetails(
            list = {
                ExtendedList(entries, onPick = { index -> choiceAt(index)?.let { (map, mode) -> onPick(map, mode) } }, initial = 1, onSelect = { selected = it }, keys = keys)
            },
            details = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    val choice = choiceAt(selected)
                    if (choice != null) {
                        val (map, mode) = choice
                        val metadata = map.sidecar.metadata
                        Text(map.baseName.uppercase(), color = ExtendedColors.accent, fontSize = 22.sp)
                        Text(
                            "${if (mode == GameMode.PLAYER_VS_PLAYER) "against each other" else "together"} — " +
                                "${metadata.world.name.lowercase()}${if (metadata.night) ", night" else ""} — ${metadata.widthTiles}×${metadata.heightTiles}",
                            color = ExtendedColors.dim,
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(map.sidecar.missionText.briefing.trim(), color = ExtendedColors.text, fontSize = 15.sp)
                    }
                }
            },
        )
    }
}

/**
 * Joining a game: the games heard on the network ([found]) to pick from, or an address to type (a
 * name or an IP address, with `:port` if not the usual one). [connecting] while the answer is
 * awaited; [error] says why the last try failed.
 */
@Composable
fun NetworkAddressScreen(
    found: List<LanGame>,
    lastAddress: String,
    connecting: Boolean,
    error: String?,
    onConnect: (String) -> Unit,
    onBack: () -> Unit,
) {
    val keys = remember { KeyRouter() }
    var typing by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf(lastAddress) }
    val fieldFocus = remember { FocusRequester() }
    val entries = (if (found.isEmpty()) listOf(ListEntry("Looking for games on the network…", enabled = false)) else emptyList()) +
        found.map { ListEntry("${it.beacon.name} — ${it.beacon.map.uppercase()}", "${it.beacon.players} of ${it.beacon.maxPlayers} · ${it.target}") } +
        ListEntry("Type an address…")
    ExtendedPage(
        title = "Join a game",
        subtitle = if (typing) "The address of the machine that hosts; it shows it in its lobby" else "Games on this network, or an address",
        onBack = { if (typing) typing = false else onBack() },
        footer = if (typing) "Enter joins · ESC goes back to the list" else "Enter joins · ESC goes back",
        onKey = { event -> if (typing || connecting) false else keys.handle(event) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (typing) {
                LaunchedEffect(Unit) { fieldFocus.requestFocus() }
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it.trim().take(MAX_ADDRESS_LENGTH) },
                    label = { Text("Address") },
                    singleLine = true,
                    enabled = !connecting,
                    modifier = Modifier
                        .width(420.dp)
                        .focusRequester(fieldFocus)
                        .onPreviewKeyEvent { event ->
                            if (event.key == Key.Enter && event.type == KeyEventType.KeyDown) {
                                if (typed.isNotBlank() && !connecting) onConnect(typed)
                                true
                            } else {
                                false
                            }
                        },
                )
            } else {
                Column(modifier = Modifier.fillMaxWidth().height(320.dp)) {
                    ExtendedList(
                        entries,
                        onPick = { index ->
                            when {
                                connecting -> Unit
                                index == entries.lastIndex -> typing = true
                                else -> found.getOrNull(if (found.isEmpty()) -1 else index)?.let { onConnect(it.target) }
                            }
                        },
                        keys = keys,
                    )
                }
            }
            when {
                connecting -> Text("Connecting…", color = ExtendedColors.dim, fontSize = 16.sp)
                error != null -> Text(error, color = ExtendedColors.accent, fontSize = 16.sp)
            }
        }
    }
}

/**
 * The players before a network game: everyone who joined, by machine. The host sees where the
 * others can join and starts with Enter once there are [minPlayers]; a guest waits for the host.
 */
@Composable
fun NetworkLobbyScreen(
    title: String,
    members: List<LobbyMember>,
    hosting: Boolean,
    addresses: List<String>,
    minPlayers: Int,
    /** Against each other: the places show their team. */
    teams: Boolean,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    val players = members.sumOf { it.seats.size }
    ExtendedPage(
        title = title,
        subtitle = if (hosting) {
            if (addresses.isEmpty()) "No network found on this machine" else "Others join at ${addresses.joinToString(" or ")}"
        } else {
            "Waiting for the host to start"
        },
        onBack = onBack,
        footer = when {
            !hosting -> "ESC leaves"
            players >= minPlayers -> "Enter starts · ESC stops the game"
            else -> "Waiting for players · ESC stops the game"
        },
        onKey = { event ->
            if (hosting && (event.key == Key.Enter || event.key == Key.NumPadEnter) && players >= minPlayers) {
                onStart()
                true
            } else {
                false
            }
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            for (place in 0 until MAX_NET_PLAYERS) {
                val member = members.firstOrNull { place in it.seats }
                Column(
                    modifier = Modifier
                        .width(220.dp)
                        .height(120.dp)
                        .background(if (member != null) ExtendedColors.selected else ExtendedColors.panel, RoundedCornerShape(10.dp))
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Player ${place + 1}", color = ExtendedColors.accent, fontSize = 20.sp)
                    Text(member?.name ?: "free", color = if (member != null) ExtendedColors.text else ExtendedColors.dim, fontSize = 16.sp)
                    // Extended teams (ADR 0027): players 1 and 3 against 2 and 4.
                    if (teams) Text(if (place % 2 == 0) "team 1" else "team 2", color = ExtendedColors.dim, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.fillMaxWidth().height(8.dp))
    }
}

private const val MAX_ADDRESS_LENGTH = 64

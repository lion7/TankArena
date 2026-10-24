package nl.tankarena.ui.compose.extended

import nl.tankarena.net.RelayGame
import nl.tankarena.net.GameCodes
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
import androidx.compose.runtime.key
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
import nl.tankarena.net.ServerMessage
import nl.tankarena.sim.LegacyVehicles
import nl.tankarena.net.LobbyMember
import nl.tankarena.net.MAX_NET_PLAYERS
import nl.tankarena.ui.compose.GameMode
import nl.tankarena.ui.compose.menu.MissionCodes
import nl.tankarena.ui.compose.menu.MissionEntry
import nl.tankarena.ui.compose.menu.playableIn

/** Extended's network game (ADR 0024): host a game on this machine or join one. */
@Composable
fun NetworkMenuScreen(relay: String, onHost: () -> Unit, onJoin: () -> Unit, onRelay: () -> Unit, onBack: () -> Unit) {
    val keys = remember { KeyRouter() }
    val entries = listOf(
        ListEntry("Host a game", "pick a map; the others join this machine") to onHost,
        ListEntry("Join a game", "from the network, the internet, or by address or code") to onJoin,
        ListEntry("Internet relay", relay.ifBlank { "none: games stay on this network" }) to onRelay,
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
    /** The relay's public games (ADR 0028). */
    internet: List<RelayGame>,
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
    // The games to pick, in the list's order: on this network, then on the internet.
    val targets = found.map { it.target } + internet.map { GameCodes.show(it.code) }
    val entries = (if (targets.isEmpty()) listOf(ListEntry("Looking for games…", enabled = false)) else emptyList()) +
        found.map { ListEntry("${it.beacon.name} — ${it.beacon.map.uppercase()}", "${it.beacon.players} of ${it.beacon.maxPlayers} · ${it.target}") } +
        internet.map { ListEntry("${it.name} — ${it.map.uppercase()}", "${it.players} of ${it.maxPlayers} · internet") } +
        ListEntry("Type an address or a game code…")
    ExtendedPage(
        title = "Join a game",
        subtitle = if (typing) "The host's address or its game code; the host's lobby shows both" else "Games on this network and the internet, or an address",
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
                    label = { Text("Address or game code") },
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
                                else -> targets.getOrNull(if (targets.isEmpty()) -1 else index)?.let(onConnect)
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
    /** The game on the internet relay: its code or what went wrong; null without a relay. */
    internet: String? = null,
    /** P lists the game publicly on the relay or takes it off the list. */
    onTogglePublic: (() -> Unit)? = null,
    onStart: () -> Unit,
    onBack: () -> Unit,
    /** The map the host picked (a guest's view). */
    map: String = "",
    /** Against each other: the rounds each side has won over every map so far. */
    series: List<Int> = listOf(0, 0),
    /** The host: M picks another map for the same players. */
    onChangeMap: (() -> Unit)? = null,
    /** A guest who left the round being played: they play again from the next one. */
    sittingOut: Boolean = false,
) {
    val players = members.sumOf { it.seats.size }
    ExtendedPage(
        title = title,
        subtitle = when {
            hosting -> if (addresses.isEmpty()) "No network found on this machine" else "Others join at ${addresses.joinToString(" or ")}"
            sittingOut -> "You left the round; you play again from the next one"
            map.isNotEmpty() -> "${map.uppercase()} · waiting for the host to start"
            else -> "Waiting for the host to start"
        },
        onBack = onBack,
        footer = when {
            !hosting -> "ESC leaves"
            players >= minPlayers -> "Enter starts · M another map · ESC stops the game"
            else -> "Waiting for players · M another map · ESC stops the game"
        },
        onKey = { event ->
            when {
                hosting && (event.key == Key.Enter || event.key == Key.NumPadEnter) && players >= minPlayers -> onStart()
                event.key == Key.M && onChangeMap != null -> onChangeMap()
                event.key == Key.P && onTogglePublic != null -> onTogglePublic()
                else -> return@ExtendedPage false
            }
            true
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
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
            if (series.any { it > 0 }) {
                Text("Series: team 1 ${series[0]} – ${series.getOrElse(1) { 0 }} team 2 (the losers tank goes by it)", color = ExtendedColors.text, fontSize = 18.sp)
            }
            if (internet != null) Text(internet, color = ExtendedColors.text, fontSize = 18.sp)
        }
    }
}

private const val MAX_ADDRESS_LENGTH = 64

/**
 * Against each other, before a round (legacy select_tanks() over the network): this machine's
 * players ([seats]) pick their vehicles one after the other from those [allowed]. [secondsLeft] and
 * [status] tell how long the others have.
 */
@Composable
fun VehicleChoiceScreen(
    round: Int,
    wins: List<Int>,
    seats: List<Int>,
    allowed: List<Boolean>,
    secondsLeft: Int,
    status: String,
    onPick: (seat: Int, vehicle: Int) -> Unit,
    onBack: () -> Unit,
    /** The vehicle a player drove last, to start their choice on; null for none. */
    last: (seat: Int) -> Int? = { null },
    /** The rounds each side has won over every map so far. */
    series: List<Int> = wins,
) {
    val keys = remember { KeyRouter() }
    var picking by remember(round) { mutableIntStateOf(0) }
    val seat = seats.getOrNull(picking)
    val entries = allowed.indices.map { vehicle ->
        val spec = LegacyVehicles.spec(vehicle)
        ListEntry(spec.name, spec.kind.name.lowercase().replace("chopper", "helicopter"), enabled = allowed[vehicle])
    }
    ExtendedPage(
        title = "Round $round",
        subtitle = "Team 1 ${wins.getOrElse(0) { 0 }} – ${wins.getOrElse(1) { 0 }} Team 2" +
            (if (series != wins) " (series ${series.getOrElse(0) { 0 }} – ${series.getOrElse(1) { 0 }})" else "") + " · $secondsLeft s to pick",
        onBack = onBack,
        footer = if (seat != null) "Enter picks · ESC leaves the game" else "ESC leaves the game",
        onKey = { event -> if (seat != null) keys.handle(event) else false },
    ) {
        if (seat != null) {
            Column {
                Text("Player ${seat + 1} (team ${seat % 2 + 1}): your vehicle", color = ExtendedColors.accent, fontSize = 20.sp)
                Spacer(Modifier.height(12.dp))
                // Each player's choice starts on what they drove last, if the map allows it.
                val start = last(seat)?.takeIf { allowed.getOrNull(it) == true } ?: allowed.indexOfFirst { it }.coerceAtLeast(0)
                key(seat) {
                    ExtendedList(
                        entries,
                        onPick = { vehicle ->
                            onPick(seat, vehicle)
                            picking++
                        },
                        initial = start,
                        keys = keys,
                    )
                }
            }
        } else {
            Text("Waiting for the others… $status", color = ExtendedColors.dim, fontSize = 18.sp)
        }
    }
}

/**
 * How a round of a network game ended, seen by a machine whose players are on [mySide] (null: the
 * dedicated host's view of nobody). The host goes on with Enter ([onNext]); the others wait.
 */
@Composable
fun NetworkResultScreen(
    result: ServerMessage.RoundOver,
    mySide: Int?,
    hosting: Boolean,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val winner = result.winner
    val headline = when {
        result.missionWon != null -> if (result.missionWon == true) "Mission accomplished" else "Mission failed"
        winner == null -> "Nobody won the round"
        winner == mySide -> "Your team won the round"
        else -> "Team ${winner + 1} won the round"
    }
    val matchLine = when {
        result.missionWon != null -> null
        !result.matchOver -> "Team 1 ${result.wins[0]} – ${result.wins[1]} Team 2"
        winner == null -> "The match ends: ${result.wins[0]} – ${result.wins[1]}"
        else -> "Team ${result.wins.indices.maxBy { result.wins[it] } + 1} wins the match, ${result.wins[0]} – ${result.wins[1]}"
    }
    ExtendedPage(
        title = if (result.missionWon != null) "Mission over" else "Round ${result.round}",
        onBack = onBack,
        footer = when {
            hosting && result.matchOver -> "Enter goes back to the lobby · ESC ends the game"
            hosting -> "Enter: the next round · ESC ends the game"
            result.matchOver -> "Waiting for the host to go back to the lobby · ESC leaves"
            else -> "Waiting for the host to start the next round · ESC leaves"
        },
        onKey = { event ->
            if ((event.key == Key.Enter || event.key == Key.NumPadEnter) && hosting) {
                onNext()
                true
            } else {
                false
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(headline, color = ExtendedColors.accent, fontSize = 28.sp)
            if (matchLine != null) Text(matchLine, color = ExtendedColors.text, fontSize = 20.sp)
            if (result.missionWon == null && result.series != result.wins) {
                Text("Series over every map: team 1 ${result.series[0]} – ${result.series[1]} team 2", color = ExtendedColors.dim, fontSize = 18.sp)
            }
        }
    }
}

/**
 * The internet relay's address (ADR 0028): a name or `host:port`, `wss://` for one behind TLS.
 * Empty: no relay, games stay on the network.
 */
@Composable
fun RelaySettingScreen(current: String, onSave: (String) -> Unit, onBack: () -> Unit) {
    var typed by remember { mutableStateOf(current) }
    val fieldFocus = remember { FocusRequester() }
    ExtendedPage(
        title = "Internet relay",
        subtitle = "Hosts publish their games there and get a code; others join with the code",
        onBack = onBack,
        footer = "Enter saves (empty: no relay) · ESC goes back",
    ) {
        LaunchedEffect(Unit) { fieldFocus.requestFocus() }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it.trim().take(MAX_ADDRESS_LENGTH) },
                label = { Text("Relay address") },
                singleLine = true,
                modifier = Modifier
                    .width(520.dp)
                    .focusRequester(fieldFocus)
                    .onPreviewKeyEvent { event ->
                        if (event.key == Key.Enter && event.type == KeyEventType.KeyDown) {
                            onSave(typed)
                            true
                        } else {
                            false
                        }
                    },
            )
            Text("Run one with: ./kotlin run -m app-relay", color = ExtendedColors.dim, fontSize = 14.sp)
        }
    }
}

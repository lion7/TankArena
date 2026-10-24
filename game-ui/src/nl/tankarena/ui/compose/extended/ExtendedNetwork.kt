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
import nl.tankarena.sim.MapScore
import nl.tankarena.net.LobbyMember
import nl.tankarena.net.MAX_NET_PLAYERS
import nl.tankarena.ui.compose.GameMode
import nl.tankarena.ui.compose.menu.MissionCodes
import nl.tankarena.ui.compose.menu.MissionEntry
import nl.tankarena.ui.compose.menu.playableIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment

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
fun NetworkHostScreen(
    missions: List<MissionEntry>,
    onPick: (MissionEntry, GameMode) -> Unit,
    onBack: () -> Unit,
    /** Against each other: a map's record in this game's series, null when it has not been played. */
    scoreOf: (String) -> MapScore? = { null },
) {
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
        versus.forEach { map ->
            val played = scoreOf(map.baseName)?.played?.takeIf { it > 0 }?.let { " · $it×" }.orEmpty()
            add(ListEntry(map.baseName.uppercase(), map.sidecar.metadata.world.name.lowercase() + played))
        }
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
                        RetroText(map.baseName.uppercase(), color = RetroColors.accent)
                        RetroText(
                            "${if (mode == GameMode.PLAYER_VS_PLAYER) "against each other" else "together"} — " +
                                "${metadata.world.name.lowercase()}${if (metadata.night) ", night" else ""} — ${metadata.widthTiles}×${metadata.heightTiles}",
                            color = RetroColors.dim,
                            font = RetroFont.SMALL,
                        )
                        if (mode == GameMode.PLAYER_VS_PLAYER) {
                            scoreOf(map.baseName)?.let { RetroText("This series: ${it.text()}", color = RetroColors.normal) }
                        }
                        Spacer(Modifier.height(retroDp(8)))
                        RetroText(map.sidecar.missionText.briefing.trim())
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
        actions = if (typing) listOf(PageAction("Join") { if (typed.isNotBlank() && !connecting) onConnect(typed) }) else emptyList(),
        onKey = { event -> if (typing || connecting) false else keys.handle(event) },
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(retroDp(8))) {
            if (typing) {
                LaunchedEffect(Unit) { fieldFocus.requestFocus() }
                RetroTextField(
                    value = typed,
                    onValueChange = { typed = it.trim().take(MAX_ADDRESS_LENGTH) },
                    label = "Address or game code",
                    onDone = { if (typed.isNotBlank() && !connecting) onConnect(typed) },
                    enabled = !connecting,
                    modifier = Modifier.widthIn(max = retroDp(420)).fillMaxWidth().focusRequester(fieldFocus),
                )
            } else {
                Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
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
                connecting -> RetroText("Connecting…", color = RetroColors.dim)
                error != null -> RetroText(error, color = RetroColors.accent)
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
    /** Against each other: the current map's record in this series. */
    mapScore: MapScore? = null,
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
        actions = buildList {
            if (hosting && players >= minPlayers) add(PageAction("Start", onStart))
            onChangeMap?.let { add(PageAction("Map", it)) }
            onTogglePublic?.let { add(PageAction("Public", it)) }
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
        Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(retroDp(10))) {
            PlaceBoxes(MAX_NET_PLAYERS) { place ->
                val member = members.firstOrNull { place in it.seats }
                RetroText("Player ${place + 1}", color = if (member != null) RetroColors.high else RetroColors.normal, maxLines = 1)
                RetroText(member?.name ?: "free", color = if (member != null) RetroColors.text else RetroColors.dim, maxLines = 2)
                // Extended teams (ADR 0027): players 1 and 3 against 2 and 4.
                if (teams) RetroText(if (place % 2 == 0) "team 1" else "team 2", color = RetroColors.dim, font = RetroFont.SMALL)
            }
            if (mapScore != null && map.isNotEmpty()) RetroText("${map.uppercase()}: ${mapScore.text()}")
            if (series.any { it > 0 }) {
                RetroText("Series: team 1 ${series[0]} – ${series.getOrElse(1) { 0 }} team 2 (the losers tank goes by it)")
            }
            if (internet != null) RetroText(internet, color = RetroColors.accent)
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
                RetroText("Player ${seat + 1} (team ${seat % 2 + 1}): your vehicle", color = RetroColors.accent)
                Spacer(Modifier.height(retroDp(6)))
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
            RetroText("Waiting for the others… $status", color = RetroColors.dim)
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
        actions = if (hosting) listOf(PageAction(if (result.matchOver) "Lobby" else "Next round", onNext)) else emptyList(),
        onKey = { event ->
            if ((event.key == Key.Enter || event.key == Key.NumPadEnter) && hosting) {
                onNext()
                true
            } else {
                false
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(retroDp(8), Alignment.CenterVertically)) {
            RetroText(headline, color = RetroColors.high, font = RetroFont.TITLE, centred = true, modifier = Modifier.fillMaxWidth())
            if (matchLine != null) RetroText(matchLine, centred = true, modifier = Modifier.fillMaxWidth())
            if (result.missionWon == null && result.series != result.wins) {
                RetroText("Series over every map: team 1 ${result.series[0]} – ${result.series[1]} team 2", color = RetroColors.dim, centred = true, modifier = Modifier.fillMaxWidth())
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
        actions = listOf(PageAction("Save") { onSave(typed) }),
    ) {
        LaunchedEffect(Unit) { fieldFocus.requestFocus() }
        Column(verticalArrangement = Arrangement.spacedBy(retroDp(8))) {
            RetroTextField(
                value = typed,
                onValueChange = { typed = it.trim().take(MAX_ADDRESS_LENGTH) },
                label = "Relay address",
                onDone = { onSave(typed) },
                modifier = Modifier.widthIn(max = retroDp(520)).fillMaxWidth().focusRequester(fieldFocus),
            )
            RetroText("Run one with: ./kotlin run -m app-relay", color = RetroColors.dim, font = RetroFont.SMALL)
        }
    }
}

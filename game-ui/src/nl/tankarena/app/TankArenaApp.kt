package nl.tankarena.app

import nl.tankarena.ui.compose.extended.extendedKeysText
import nl.tankarena.ui.compose.extended.RelaySettingScreen
import nl.tankarena.net.GameCodes
import nl.tankarena.net.RelayDirectory
import nl.tankarena.net.RelayGame
import nl.tankarena.net.RelayHost
import androidx.compose.runtime.mutableIntStateOf
import nl.tankarena.ui.compose.extended.NetworkResultScreen
import nl.tankarena.ui.compose.extended.VehicleChoiceScreen
import nl.tankarena.net.MAX_NET_PLAYERS
import nl.tankarena.net.NetSeries
import nl.tankarena.protocol.snapshot.PROTOCOL_VERSION
import nl.tankarena.net.LanFinder
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import nl.tankarena.sim.DualVehicles
import nl.tankarena.input.Seat
import nl.tankarena.ui.compose.extended.NetworkLobbyScreen
import nl.tankarena.ui.compose.extended.NetworkAddressScreen
import nl.tankarena.ui.compose.extended.NetworkHostScreen
import nl.tankarena.ui.compose.extended.NetworkMenuScreen
import nl.tankarena.net.parseAddress
import nl.tankarena.net.localAddresses
import nl.tankarena.net.DEFAULT_PORT
import nl.tankarena.net.GameServer
import nl.tankarena.net.GameClient
import nl.tankarena.net.ClientState
import nl.tankarena.net.RemoteMatchClient
import nl.tankarena.net.HostedMatch
import nl.tankarena.sim.MatchClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import nl.tankarena.audio.AudioManager
import nl.tankarena.content.GameModeCompatibility
import nl.tankarena.content.TankArenaWorld
import nl.tankarena.core.FixedStepClock
import nl.tankarena.input.GamepadMenuKeys
import nl.tankarena.protocol.snapshot.GameEvent
import nl.tankarena.protocol.snapshot.ServerFrame
import nl.tankarena.render.ViewportEventQueue
import nl.tankarena.sim.Cheats
import nl.tankarena.sim.DualSetup
import nl.tankarena.sim.LocalMatchClient
import nl.tankarena.sim.MatchMode
import nl.tankarena.sim.MatchRecording
import nl.tankarena.sim.Ruleset
import nl.tankarena.sim.SimulationLoop
import nl.tankarena.ui.compose.DesktopShellScreen
import nl.tankarena.ui.compose.EditorMapChoice
import nl.tankarena.ui.compose.GAME_FADE_MILLIS
import nl.tankarena.ui.compose.GameMode
import nl.tankarena.ui.compose.MissionOutcome
import nl.tankarena.ui.compose.extended.CampaignScreen
import nl.tankarena.ui.compose.extended.ExtendedControlsPage
import nl.tankarena.ui.compose.extended.ExtendedHelpPage
import nl.tankarena.ui.compose.extended.ExtendedHomeActions
import nl.tankarena.ui.compose.extended.ExtendedHomeScreen
import nl.tankarena.ui.compose.extended.ExtendedSettingsPage
import nl.tankarena.ui.compose.extended.ExtendedTextPage
import nl.tankarena.ui.compose.extended.JoinScreen
import nl.tankarena.ui.compose.extended.LocalGameScreen
import nl.tankarena.ui.compose.extended.RulesetChoicePage
import nl.tankarena.ui.compose.gammaCorrection
import nl.tankarena.ui.compose.menu.ControlsSetupScreen
import nl.tankarena.ui.compose.menu.Credits
import nl.tankarena.ui.compose.menu.CreditsScreen
import nl.tankarena.ui.compose.menu.FliScreen
import nl.tankarena.ui.compose.menu.GameModeMenuScreen
import nl.tankarena.ui.compose.menu.GameOptionsScreen
import nl.tankarena.ui.compose.menu.GameOptionsStore
import nl.tankarena.ui.compose.menu.GameSettings
import nl.tankarena.ui.compose.menu.HelpFile
import nl.tankarena.ui.compose.menu.IntroStep
import nl.tankarena.ui.compose.menu.MISSION_END_BANNER_MILLIS
import nl.tankarena.ui.compose.menu.MainMenuScreen
import nl.tankarena.ui.compose.menu.MessageBarScreen
import nl.tankarena.ui.compose.menu.MissionCatalog
import nl.tankarena.ui.compose.menu.MissionCodes
import nl.tankarena.ui.compose.menu.MissionEntry
import nl.tankarena.ui.compose.menu.MissionGate
import nl.tankarena.ui.compose.menu.PasswordStore
import nl.tankarena.ui.compose.menu.PickListLayout
import nl.tankarena.ui.compose.menu.PickListScreen
import nl.tankarena.ui.compose.menu.PlayerControls
import nl.tankarena.ui.compose.menu.PromptBarScreen
import nl.tankarena.ui.compose.menu.SelectionKey
import nl.tankarena.ui.compose.menu.TankSelectScreen
import nl.tankarena.ui.compose.menu.TextViewerScreen
import nl.tankarena.ui.compose.menu.YesNoScreen
import nl.tankarena.ui.resources.Res

/**
 * The whole game (ADR 0025): intro, both menus, games, replays and the editor, on any platform;
 * [platform] gives it sound, music, gamepads and quitting. [args] are the command line's (or none).
 */
@Composable
fun TankArenaApp(
    platform: GamePlatform,
    keyHook: GameKeyHook,
    args: List<String>,
    cheats: Cheats = Cheats.fromArgs(args.toTypedArray()),
) {
    val controls = remember { DesktopControls().also { it.pads = platform::gamepads } }
    // Legacy TANK -SETUP: the controls setup first.
    var screen by remember {
        val intro = DesktopShellScreen.Intro(IntroStep.LOGO)
        mutableStateOf<DesktopShellScreen>(
            if (args.any { it.trimStart('-').equals("SETUP", ignoreCase = true) }) DesktopShellScreen.ControlsSetup(next = intro) else intro,
        )
    }
    // Legacy menu(): the game mode menu opens on its first entry, and keeps the chosen one
    // while games are played from it.
    var lastMode by remember { mutableStateOf<GameMode?>(null) }
    // Legacy `pl_tanks`: the vehicles last picked on the selection screen.
    var playerTanks by remember { mutableStateOf(listOf(0, 0)) }
    var missions by remember { mutableStateOf<List<MissionEntry>?>(null) }
    // Extended: the screen a game was started from, shown again when it ends.
    var extendedReturn by remember { mutableStateOf<DesktopShellScreen?>(null) }
    // Joining a network game: the address typed last, a connection being made, why the last one failed.
    var netAddress by remember { mutableStateOf("") }
    var netConnecting by remember { mutableStateOf(false) }
    var netError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    // Legacy TANK.CFG: the game options, saved whenever they change.
    val settings = remember {
        GameSettings(GameOptionsStore()).also { settings ->
            // --classic / --extended choose the ruleset and keep the choice (ADR 0023).
            val forced = when {
                "--classic" in args -> Ruleset.CLASSIC
                "--extended" in args -> Ruleset.EXTENDED
                else -> null
            }
            forced?.let { settings.update(settings.options.copy(ruleset = it)) }
        }
    }
    val options = settings.options
    val extended = options.ruleset == Ruleset.EXTENDED
    SideEffect {
        controls.settings = options.controls
        controls.extended = extended
        controls.mouseControls = options.mouseControls
        controls.twinSticks = options.twinSticks
    }
    val missionGate = remember { MissionGate(PasswordStore(), settings) }

    // Legacy init_player(): TUNE.MID from the start, BELVED.MID while a night map is played; in
    // a game the music is turned down to 30% (palette.c fades it with the picture).
    val music = platform.music
    DisposableEffect(music) { onDispose(music::dispose) }
    val playing = (screen as? DesktopShellScreen.Playing)?.mission ?: (screen as? DesktopShellScreen.PlayTest)?.mission
    val night = playing?.sidecar?.metadata?.world == TankArenaWorld.NIGHT
    LaunchedEffect(night) {
        withContext(Dispatchers.IO) { music.play(if (night) Music.NIGHT_TUNE else Music.TUNE) }
    }
    LaunchedEffect(options.musicVolume, playing != null) {
        music.volume = if (playing != null) options.musicVolume * 3 / 10 else options.musicVolume
    }

    // Measuring: TANKARENA_AUTOSTART=<code> skips the menus into a one-player mission, and
    // TANKARENA_QUIT_AFTER=<seconds> closes the game again.
    LaunchedEffect(Unit) {
        val code = System.getenv("TANKARENA_AUTOSTART") ?: return@LaunchedEffect
        val all = MissionCatalog.load(UserMapStore::loadAll).also { missions = it }
        val mission = all.firstOrNull { it.code.equals(code, ignoreCase = true) || it.baseName.equals(code, ignoreCase = true) }
            ?: return@LaunchedEffect
        // TANKARENA_AUTOSTART_PVP=1 starts a player-vs-player match on that map at the tank selection.
        screen = if (System.getenv("TANKARENA_AUTOSTART_PVP") == "1") {
            DesktopShellScreen.TankSelect(nl.tankarena.ui.compose.DualMatch(mission, options = settings.options))
        } else {
            DesktopShellScreen.Playing(mission, GameMode.SINGLE_PLAYER_VS_COMPUTER)
        }
    }
    LaunchedEffect(Unit) {
        val seconds = System.getenv("TANKARENA_QUIT_AFTER")?.toLongOrNull() ?: return@LaunchedEffect
        delay(seconds * 1000)
        platform.quit()
    }

    /**
     * Extended local games ask who plays first: player one has the keyboard and mouse, the others
     * join with a gamepad (or as the second keyboard player, when that is switched on).
     */
    fun joinFirst(next: DesktopShellScreen): DesktopShellScreen = DesktopShellScreen.Join(next, minPlayers = 2, maxPlayers = MAX_PLAYERS)

    /**
     * The players at this machine for a network game: those who joined on the join screen, else one
     * with the single player's keys and the first gamepad if there is one. Returns how many.
     */
    fun useNetworkSeats(): Int {
        if (controls.seats == null) controls.seats = listOf(Seat(keys = null, pad = 0.takeIf { platform.gamepads().isNotEmpty() }, mouse = true))
        return controls.seats!!.size
    }

    /** With gamepads connected, who plays at this machine is asked first (up to three; one place stays for the others). */
    fun netJoinFirst(next: DesktopShellScreen, back: DesktopShellScreen): DesktopShellScreen {
        controls.seats = null
        val others = platform.gamepads().isNotEmpty() || options.keyboardSecond
        return if (!others) next else DesktopShellScreen.Join(next, minPlayers = 1, maxPlayers = MAX_NET_PLAYERS - 1, back = back)
    }

    /** The host starts the series' next round; its screen shows it. */
    fun hostRound(session: NetworkSession.Hosting, series: NetSeries): DesktopShellScreen =
        DesktopShellScreen.NetworkPlaying(session, series.startRound(), session.mission, session.mode, (0 until session.localPlayers).toList())

    /** Players this session: who joined (Extended, ADR 0027), else as many as the mode has. */
    fun sessionPlayers(mode: GameMode): Int {
        val default = if (mode == GameMode.SINGLE_PLAYER_VS_COMPUTER) 1 else 2
        return controls.seats?.size?.takeIf { extended && it > default } ?: default
    }

    SideEffect {
        keyHook.handler = { event -> handleGlobalKeys(event, screen, controls, settings) { saveScreenshot(platform, settings) } }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        // Outside a game the gamepads work the menus as the keyboard does (ADR 0026).
        // The join screen and the tank selection read the pads themselves.
        val inGame = screen is DesktopShellScreen.Playing || screen is DesktopShellScreen.PlayTest || screen is DesktopShellScreen.NetworkPlaying ||
            screen is DesktopShellScreen.TankSelect || screen is DesktopShellScreen.Join
        LaunchedEffect(inGame) {
            val post = platform.postKey
            if (inGame || post == null) return@LaunchedEffect
            val menuKeys = GamepadMenuKeys()
            while (true) {
                val keys = withFrameNanos { now -> menuKeys.step(now, platform.gamepads()) }
                for (key in keys) post(key)
            }
        }
        CompositionLocalProvider(LocalGamePlatform provides platform, LocalTouchScreen provides platform.touchScreen) {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize().gammaCorrection(options.gamma), color = Color.Black) {
                val atMenu = screen == DesktopShellScreen.MainMenu || screen == DesktopShellScreen.GameModeSelect
                val shown = when {
                    atMenu && options.ruleset == null -> DesktopShellScreen.RulesetChoice
                    // After a game, Extended goes back to where it was started from.
                    atMenu && extended -> if (screen == DesktopShellScreen.GameModeSelect) extendedReturn ?: DesktopShellScreen.ExtendedMenu else DesktopShellScreen.ExtendedMenu
                    else -> screen
                }
                when (val current = shown) {
                    // Legacy write_title(): a key during LOGO.FLI skips the rest of the intro.
                    is DesktopShellScreen.Intro -> when (current.step) {
                        IntroStep.LOGO -> FliScreen("LOGO.FLI", onDone = { stopped ->
                            screen = if (stopped) DesktopShellScreen.MainMenu else DesktopShellScreen.Intro(IntroStep.TITLE)
                        })
                        IntroStep.TITLE -> FliScreen("TITLE.FLI", onDone = { screen = DesktopShellScreen.Intro(IntroStep.CREDITS) })
                        IntroStep.CREDITS -> CreditsScreen("credits.bmp", 70, 40, Credits.CREDITS, onDone = {
                            screen = DesktopShellScreen.Intro(IntroStep.THANKS)
                        })
                        IntroStep.THANKS -> CreditsScreen("credits2.bmp", 160, 100, Credits.THANKS, onDone = {
                            screen = DesktopShellScreen.MainMenu
                        })
                    }

                    is DesktopShellScreen.Animation -> FliScreen(current.fileName, onDone = { screen = current.next })

                    DesktopShellScreen.Options -> GameOptionsScreen(
                        options = options,
                        onChange = settings::update,
                        onLeave = { screen = if (extended) DesktopShellScreen.ExtendedSettings else DesktopShellScreen.MainMenu },
                        // Classic has no other way to the controls than a command-line switch.
                        onControls = if (extended) null else ({ screen = DesktopShellScreen.ControlsSetup(next = DesktopShellScreen.Options) }),
                    )

                    DesktopShellScreen.RulesetChoice -> RulesetChoicePage(
                        onChoose = { ruleset ->
                            settings.update(options.copy(ruleset = ruleset))
                            screen = DesktopShellScreen.MainMenu
                        },
                        onExit = platform::quit,
                    )

                    DesktopShellScreen.ExtendedMenu -> {
                        // Back home: the next game asks again who plays with what.
                        SideEffect { controls.seats = null }
                        LaunchedEffect(Unit) {
                            if (missions == null) missions = MissionCatalog.load(UserMapStore::loadAll)
                        }
                        ExtendedHomeScreen(
                            ExtendedHomeActions(
                                campaign = { screen = DesktopShellScreen.Campaign(GameMode.SINGLE_PLAYER_VS_COMPUTER).also { extendedReturn = it } },
                                localGame = { screen = DesktopShellScreen.LocalGame.also { extendedReturn = it } },
                                replays = { screen = DesktopShellScreen.Replays },
                                mapEditor = { screen = DesktopShellScreen.EditorOpen },
                                settings = { screen = DesktopShellScreen.ExtendedSettings },
                                help = { screen = DesktopShellScreen.ExtendedHelp },
                                quit = platform::quit,
                                classic = { settings.update(options.copy(ruleset = Ruleset.CLASSIC)) },
                                networkGame = { screen = DesktopShellScreen.NetworkMenu },
                            ),
                        )
                    }

                    is DesktopShellScreen.Join -> JoinScreen(
                        title = "Who plays?",
                        minPlayers = current.minPlayers,
                        maxPlayers = current.maxPlayers,
                        keySets = options.controls,
                        first = Seat(keys = null, pad = null, mouse = true),
                        keyboardSets = if (options.keyboardSecond) setOf(SECOND_KEYBOARD_SET) else emptySet(),
                        readPadButtons = { platform.gamepads().map { it.buttons } },
                        onStart = { seats ->
                            controls.seats = seats
                            screen = current.next
                        },
                        onBack = { screen = current.back },
                    )

                    DesktopShellScreen.LocalGame -> {
                        LaunchedEffect(Unit) {
                            if (missions == null) missions = MissionCatalog.load(UserMapStore::loadAll)
                        }
                        missions?.let { all ->
                            LocalGameScreen(
                                missions = all,
                                options = options,
                                lastMap = missionGate.currentCode(GameMode.PLAYER_VS_PLAYER),
                                onOptions = settings::update,
                                onTogether = { screen = joinFirst(DesktopShellScreen.Campaign(GameMode.DUAL_PLAYER_VS_COMPUTER)) },
                                onVersus = { map ->
                                    lastMode = GameMode.PLAYER_VS_PLAYER
                                    screen = joinFirst(missionGate.submit(all, map, GameMode.PLAYER_VS_PLAYER))
                                },
                                onBack = {
                                    extendedReturn = null
                                    screen = DesktopShellScreen.MainMenu
                                },
                            )
                        }
                    }

                    DesktopShellScreen.NetworkMenu -> NetworkMenuScreen(
                        relay = options.relay,
                        onRelay = { screen = DesktopShellScreen.NetworkRelay },
                        onHost = { screen = DesktopShellScreen.NetworkHost },
                        onJoin = {
                            netError = null
                            screen = netJoinFirst(DesktopShellScreen.NetworkAddress, back = DesktopShellScreen.NetworkMenu)
                        },
                        onBack = { screen = DesktopShellScreen.MainMenu },
                    )

                    DesktopShellScreen.NetworkRelay -> RelaySettingScreen(
                        current = options.relay,
                        onSave = { typed ->
                            settings.update(options.copy(relay = typed))
                            screen = DesktopShellScreen.NetworkMenu
                        },
                        onBack = { screen = DesktopShellScreen.NetworkMenu },
                    )

                    DesktopShellScreen.NetworkHost -> {
                        LaunchedEffect(Unit) {
                            if (missions == null) missions = MissionCatalog.load(UserMapStore::loadAll)
                        }
                        missions?.let { all ->
                            NetworkHostScreen(
                                missions = all,
                                onPick = { mission, mode ->
                                    screen = netJoinFirst(DesktopShellScreen.NetworkHostStart(mission, mode), back = DesktopShellScreen.NetworkHost)
                                },
                                onBack = { screen = DesktopShellScreen.NetworkMenu },
                            )
                        }
                    }

                    is DesktopShellScreen.NetworkHostStart -> LaunchedEffect(current) {
                        val local = useNetworkSeats()
                        val server = GameServer(platform.deviceName, local, Ruleset.EXTENDED.rulesVersion)
                        server.mapName = current.mission.baseName
                        screen = runCatching { server.start() }.fold(
                            onSuccess = { port ->
                                val session = NetworkSession.Hosting(server, port, current.mission, current.mode, local)
                                // With a relay set the game is also on the internet, private until P.
                                if (options.relay.isNotBlank()) session.relay = RelayHost(server, options.relay).also { it.start() }
                                DesktopShellScreen.NetworkLobby(session)
                            },
                            onFailure = { DesktopShellScreen.Message("CANNOT HOST: ${it.message.orEmpty().uppercase()}", DesktopShellScreen.NetworkMenu) },
                        )
                    }

                    DesktopShellScreen.NetworkAddress -> {
                    // The games announced on the network, while this screen is open.
                    val finder = remember { LanFinder(protocolVersion = PROTOCOL_VERSION, rulesVersion = Ruleset.EXTENDED.rulesVersion) }
                    DisposableEffect(finder) {
                        val broadcasts = platform.receiveBroadcasts()
                        finder.start()
                        onDispose {
                            finder.stop()
                            broadcasts?.close()
                        }
                    }
                    val found by finder.games.collectAsState()
                    // The relay's public games, looked up every few seconds.
                    var internet by remember { mutableStateOf(emptyList<RelayGame>()) }
                    LaunchedEffect(options.relay) {
                        while (options.relay.isNotBlank()) {
                            internet = RelayDirectory.games(options.relay, PROTOCOL_VERSION, Ruleset.EXTENDED.rulesVersion)
                            kotlinx.coroutines.delay(5_000)
                        }
                    }
                    NetworkAddressScreen(
                        found = found,
                        internet = internet,
                        lastAddress = netAddress,
                        connecting = netConnecting,
                        error = netError,
                        onConnect = { typed ->
                            netAddress = typed
                            netConnecting = true
                            netError = null
                            val (address, port) = parseAddress(typed)
                            val code = GameCodes.normalize(typed)
                            val local = useNetworkSeats()
                            val client = GameClient(platform.deviceName, local, Ruleset.EXTENDED.rulesVersion)
                            scope.launch {
                                val joined = when {
                                    code == null -> client.join(address, port)
                                    options.relay.isBlank() -> false.also { netError = "That is a game code: set an internet relay in the network menu first." }
                                    else -> client.joinByCode(options.relay, code)
                                }
                                netConnecting = false
                                if (joined) {
                                    screen = DesktopShellScreen.NetworkLobby(NetworkSession.Joined(client, typed, local))
                                } else {
                                    if (netError == null) netError = (client.state.value as? ClientState.Out)?.reason ?: "Could not join."
                                    client.close()
                                }
                            }
                        },
                        onBack = { screen = DesktopShellScreen.NetworkMenu },
                    )
                    }

                    is DesktopShellScreen.NetworkLobby -> when (val session = current.session) {
                        is NetworkSession.Hosting -> {
                            val members by session.server.members.collectAsState()
                            val addresses = remember { localAddresses() }
                            val relay = session.relay
                            val code by (relay?.code ?: remember { kotlinx.coroutines.flow.MutableStateFlow(null) }).collectAsState()
                            val relayError by (relay?.error ?: remember { kotlinx.coroutines.flow.MutableStateFlow(null) }).collectAsState()
                            var listed by remember(relay) { mutableStateOf(relay?.public == true) }
                            NetworkLobbyScreen(
                                title = session.mission.baseName.uppercase(),
                                members = members,
                                hosting = true,
                                addresses = addresses.map { if (session.port == DEFAULT_PORT) it else "$it:${session.port}" },
                                minPlayers = 2,
                                teams = session.mode == GameMode.PLAYER_VS_PLAYER,
                                internet = relay?.let {
                                    when {
                                        relayError != null -> "Internet: ${relayError}"
                                        code != null -> "Internet: game code ${GameCodes.show(code!!)}, " +
                                            if (listed) "listed publicly (P: private)" else "private (P: list it publicly)"
                                        else -> "Internet: reaching the relay…"
                                    }
                                },
                                onTogglePublic = relay?.let {
                                    {
                                        it.public = !it.public
                                        listed = it.public
                                    }
                                },
                                onStart = {
                                    val series = NetSeries(
                                        session.server, session.mission.baseName, session.mission.document, session.mode.toMatchMode(),
                                        rounds = options.rounds, raceLaps = options.raceLaps, losersTank = options.losersTank,
                                        recordAs = session.mission.baseName,
                                    )
                                    session.series = series
                                    screen = if (series.versus) {
                                        series.openChoice()
                                        DesktopShellScreen.NetworkChoose(session)
                                    } else {
                                        hostRound(session, series)
                                    }
                                },
                                onBack = {
                                    session.close()
                                    screen = DesktopShellScreen.NetworkMenu
                                },
                            )
                        }
                        is NetworkSession.Joined -> {
                            val state by session.client.state.collectAsState()
                            val leave = {
                                session.close()
                                screen = DesktopShellScreen.NetworkMenu
                            }
                            when (val now = state) {
                                is ClientState.InLobby -> NetworkLobbyScreen(
                                    title = "Joined ${now.hostName}",
                                    members = now.members,
                                    hosting = false,
                                    addresses = emptyList(),
                                    minPlayers = 2,
                                    teams = false,
                                    onStart = {},
                                    onBack = leave,
                                )
                                is ClientState.Choosing -> {
                                    val choice = now.choice
                                    val deadline = remember(choice) { System.currentTimeMillis() + choice.seconds * 1_000L }
                                    var secondsLeft by remember(choice) { mutableIntStateOf(choice.seconds) }
                                    LaunchedEffect(choice) {
                                        while (secondsLeft > 0) {
                                            kotlinx.coroutines.delay(250)
                                            secondsLeft = ((deadline - System.currentTimeMillis() + 999) / 1_000).toInt().coerceAtLeast(0)
                                        }
                                    }
                                    VehicleChoiceScreen(
                                        round = choice.round,
                                        wins = choice.wins,
                                        seats = choice.seats,
                                        allowed = choice.allowed,
                                        secondsLeft = secondsLeft,
                                        status = "",
                                        onPick = session.client::choose,
                                        onBack = leave,
                                    )
                                }
                                is ClientState.Playing -> if (now.match === current.finished) {
                                    NetworkLobbyScreen(
                                        title = "Round over",
                                        members = emptyList(),
                                        hosting = false,
                                        addresses = emptyList(),
                                        minPlayers = 2,
                                        teams = false,
                                        onStart = {},
                                        onBack = leave,
                                    )
                                } else {
                                    LaunchedEffect(now) {
                                        val match = now.match
                                        screen = DesktopShellScreen.NetworkPlaying(
                                            session, match, MissionEntry.of(match.baseName, match.map), match.mode.toGameMode(), match.seats,
                                        )
                                    }
                                }
                                is ClientState.RoundResult -> NetworkResultScreen(
                                    result = now.result,
                                    mySide = session.client.seatsSide(),
                                    hosting = false,
                                    onNext = leave,
                                    onBack = leave,
                                )
                                is ClientState.Out -> LaunchedEffect(now) {
                                    screen = DesktopShellScreen.Message(now.reason.uppercase(), DesktopShellScreen.NetworkMenu)
                                }
                                ClientState.Connecting -> Unit
                            }
                        }
                    }

                    is DesktopShellScreen.NetworkChoose -> {
                        val session = current.session
                        val series = session.series!!
                        val picked by session.server.choices.collectAsState()
                        var secondsLeft by remember(series.round) { mutableIntStateOf(series.secondsLeft()) }
                        // Once everyone has picked (or the time is up) the round starts.
                        LaunchedEffect(series.round) {
                            while (!series.choiceDone()) {
                                kotlinx.coroutines.delay(200)
                                secondsLeft = series.secondsLeft()
                            }
                            screen = hostRound(session, series)
                        }
                        VehicleChoiceScreen(
                            round = series.round,
                            wins = series.wins,
                            seats = (0 until session.localPlayers).toList(),
                            allowed = series.allowedVehicles,
                            secondsLeft = secondsLeft,
                            status = "${picked.size} of ${session.server.seatsInGame().size} have picked",
                            onPick = session.server::choose,
                            onBack = {
                                session.close()
                                screen = DesktopShellScreen.NetworkMenu
                            },
                        )
                    }

                    is DesktopShellScreen.NetworkResult -> {
                        val session = current.session
                        NetworkResultScreen(
                            result = current.result,
                            mySide = 0,
                            hosting = true,
                            onNext = {
                                val series = session.series!!
                                screen = if (current.result.matchOver) {
                                    session.close()
                                    DesktopShellScreen.NetworkMenu
                                } else {
                                    series.openChoice()
                                    DesktopShellScreen.NetworkChoose(session)
                                }
                            },
                            onBack = {
                                session.close()
                                screen = DesktopShellScreen.NetworkMenu
                            },
                        )
                    }

                    is DesktopShellScreen.Campaign -> {
                        LaunchedEffect(Unit) {
                            if (missions == null) missions = MissionCatalog.load(UserMapStore::loadAll)
                        }
                        missions?.let { all ->
                            CampaignScreen(
                                missions = all,
                                codes = remember(all, current.mode) { missionGate.listEntries(all, current.mode) },
                                lastCode = missionGate.currentCode(current.mode),
                                mode = current.mode,
                                title = if (current.mode == GameMode.SINGLE_PLAYER_VS_COMPUTER) "Campaign" else "Two against the computer",
                                onStart = { code ->
                                    lastMode = current.mode
                                    screen = missionGate.submit(all, code, current.mode)
                                },
                                onBack = {
                                    if (current.mode == GameMode.DUAL_PLAYER_VS_COMPUTER) {
                                        screen = DesktopShellScreen.LocalGame.also { extendedReturn = it }
                                    } else {
                                        extendedReturn = null
                                        screen = DesktopShellScreen.MainMenu
                                    }
                                },
                            )
                        }
                    }

                    DesktopShellScreen.ExtendedSettings -> ExtendedSettingsPage(
                        options = options,
                        onChange = settings::update,
                        onControls = { screen = DesktopShellScreen.ControlsSetup(next = DesktopShellScreen.ExtendedSettings) },
                        onBack = { screen = DesktopShellScreen.MainMenu },
                    )

                    DesktopShellScreen.ExtendedHelp -> ExtendedHelpPage(
                        onArenaInfo = { screen = DesktopShellScreen.Help(HelpFile.ARENA_INFO) },
                        onKeys = { screen = DesktopShellScreen.ExtendedKeys },
                        onBack = { screen = DesktopShellScreen.MainMenu },
                    )

                    DesktopShellScreen.Replays -> ReplayListScreen(
                        onWatch = { entry -> screen = DesktopShellScreen.Replay(entry.file) },
                        onBack = { screen = DesktopShellScreen.MainMenu },
                    )

                    is DesktopShellScreen.Replay -> {
                        LaunchedEffect(Unit) {
                            if (missions == null) missions = MissionCatalog.load(UserMapStore::loadAll)
                        }
                        val loaded = remember(current.file, missions) {
                            missions?.let { all ->
                                runCatching {
                                    val recording = MatchRecording.fromJson(current.file.readText())
                                    val mission = all.firstOrNull { it.baseName == recording.mission }
                                        ?: error("its map ${recording.mission} is not in the game")
                                    recording to mission
                                }
                            }
                        }
                        loaded?.fold(
                            onSuccess = { (recording, mission) ->
                                ReplayScreen(
                                    recording = recording,
                                    mission = mission,
                                    soundVolume = options.soundVolume,
                                    onClose = { screen = DesktopShellScreen.Replays },
                                )
                            },
                            onFailure = { e ->
                                MessageBarScreen(
                                    message = "Cannot play this replay: ${e.message}",
                                    onDismiss = { screen = DesktopShellScreen.Replays },
                                )
                            },
                        )
                    }

                    is DesktopShellScreen.ControlsSetup -> if (extended) {
                        ExtendedControlsPage(
                            options = options,
                            onChange = settings::update,
                            onBack = { screen = current.next },
                        )
                    } else {
                        ControlsSetupScreen(
                        initial = options.controls,
                        onDone = { changed ->
                            changed?.let { settings.update(settings.options.copy(controls = it)) }
                            screen = current.next
                        },
                        readPadButtons = { platform.gamepads().map { it.buttons } },
                    )
                    }

                    DesktopShellScreen.ExitConfirm -> YesNoScreen(
                        question = "EXIT TO DOS? (Y/N)",
                        onYes = platform::quit,
                        onNo = { screen = DesktopShellScreen.MainMenu },
                    )

                    DesktopShellScreen.MainMenu -> MainMenuScreen(
                        onStartNewGame = {
                            lastMode = null
                            screen = DesktopShellScreen.GameModeSelect
                        },
                        onExit = { screen = DesktopShellScreen.ExitConfirm },
                        onGameOptions = { screen = DesktopShellScreen.Options },
                        onMapEditor = { screen = DesktopShellScreen.EditorOpen },
                        onArenaInfo = { screen = DesktopShellScreen.Help(HelpFile.ARENA_INFO) },
                        onDefaultKeys = { screen = DesktopShellScreen.Help(HelpFile.DEFAULT_KEYS) },
                        onExtended = { settings.update(options.copy(ruleset = Ruleset.EXTENDED)) },
                    )

                    DesktopShellScreen.ExtendedKeys -> ExtendedTextPage(
                        title = "Keys",
                        text = extendedKeysText(options),
                        onClose = { screen = DesktopShellScreen.ExtendedHelp },
                    )

                    is DesktopShellScreen.Help -> TextViewerScreen(
                        text = remember(current.file) { current.file.load() },
                        onClose = { screen = if (extended) DesktopShellScreen.ExtendedHelp else DesktopShellScreen.MainMenu },
                    )

                    DesktopShellScreen.GameModeSelect -> {
                        LaunchedEffect(Unit) {
                            if (missions == null) missions = MissionCatalog.load(UserMapStore::loadAll)
                        }
                        val choose: (GameMode) -> Unit = { mode ->
                            lastMode = mode
                            screen = DesktopShellScreen.CodePrompt(mode)
                        }
                        GameModeMenuScreen(
                            onSelectPlayerVsPlayer = { choose(GameMode.PLAYER_VS_PLAYER) },
                            onSelectSinglePlayer = { choose(GameMode.SINGLE_PLAYER_VS_COMPUTER) },
                            onSelectDualVsComputer = { choose(GameMode.DUAL_PLAYER_VS_COMPUTER) },
                            onCancel = { screen = DesktopShellScreen.MainMenu },
                            selected = lastMode,
                        )
                    }

                    is DesktopShellScreen.CodePrompt -> PromptBarScreen(
                        prompt = MissionCodes.prompt(current.mode),
                        initialText = missionGate.currentCode(current.mode),
                        maxLength = MissionCodes.maxLength(current.mode),
                        onSubmit = { code ->
                            missions?.let { screen = missionGate.submit(it, code, current.mode) }
                        },
                        onCancel = { screen = DesktopShellScreen.CodeList(current.mode) },
                    )

                    is DesktopShellScreen.CodeList -> {
                        val entries = remember(missions, current.mode) {
                            missions?.let { missionGate.listEntries(it, current.mode) }
                        }
                        when {
                            entries == null -> Unit
                            entries.isEmpty() -> MessageBarScreen(
                                message = MissionCodes.emptyListMessage(current.mode),
                                onDismiss = { screen = DesktopShellScreen.GameModeSelect },
                            )
                            else -> PickListScreen(
                                title = MissionCodes.listTitle(current.mode),
                                entries = entries,
                                layout = if (current.mode == GameMode.PLAYER_VS_PLAYER) PickListLayout.MAPS else PickListLayout.PASSWORDS,
                                onSelect = { code ->
                                    screen = missionGate.submit(missions.orEmpty(), code, current.mode)
                                },
                                onCancel = { screen = DesktopShellScreen.GameModeSelect },
                            )
                        }
                    }

                    is DesktopShellScreen.Message -> MessageBarScreen(
                        message = current.text,
                        onDismiss = { screen = current.next },
                    )

                    is DesktopShellScreen.Briefing -> {
                        val onClose = {
                            controls.resetAll()
                            screen = missionGate.afterBriefing(current.mission, current.mode)
                        }
                        if (extended) {
                            val metadata = current.mission.sidecar.metadata
                            ExtendedTextPage(
                                title = metadata.name.ifBlank { current.mission.baseName.uppercase() },
                                text = current.mission.sidecar.missionText.briefing,
                                onClose = onClose,
                                footer = "Enter or a tap starts",
                            )
                        } else {
                            TextViewerScreen(text = current.mission.sidecar.missionText.briefing, onClose = onClose)
                        }
                    }

                    is DesktopShellScreen.TankSelect -> TankSelectScreen(
                        match = current.match,
                        selectMap = missions.orEmpty().firstOrNull { it.baseName.equals("select", ignoreCase = true) },
                        initial = playerTanks,
                        keyFor = { key -> selectionKeyFor(key, settings.options.controls) },
                        players = sessionPlayers(GameMode.PLAYER_VS_PLAYER),
                        padActions = remember(current) {
                            val pads = List(MAX_PLAYERS) { GamepadMenuKeys() }
                            val actions = mapOf(
                                Key.DirectionUp to SelectionKey.UP,
                                Key.DirectionDown to SelectionKey.DOWN,
                                Key.DirectionLeft to SelectionKey.LEFT,
                                Key.DirectionRight to SelectionKey.RIGHT,
                                Key.Enter to SelectionKey.FIRE,
                            );
                            {
                                val now = System.nanoTime()
                                val readings = platform.gamepads()
                                (0 until MAX_PLAYERS).flatMap { player ->
                                    val pad = controls.padIndex(player)?.let(readings::getOrNull) ?: return@flatMap emptyList()
                                    pads[player].step(now, listOf(pad)).mapNotNull { actions[it] }.map { player to it }
                                }
                            }
                        },
                        onChosen = { types ->
                            playerTanks = types
                            controls.resetAll()
                            screen = DesktopShellScreen.Playing(current.match.mission, GameMode.PLAYER_VS_PLAYER, current.match, types)
                        },
                        onCancel = { screen = DesktopShellScreen.GameModeSelect },
                    )

                    is DesktopShellScreen.Playing -> GameplayScreen(
                        mission = current.mission,
                        mode = current.mode,
                        controls = controls,
                        players = sessionPlayers(current.mode),
                        soundVolume = options.soundVolume,
                        windowSize = options.windowSize,
                        showRadar = options.showRadar,
                        smoothMotion = extended && options.smoothMotion,
                        fill = extended && options.fillWindow,
                        wide = extended && options.wideView,
                        ruleset = options.ruleset ?: Ruleset.CLASSIC,
                        cheats = cheats,
                        dual = current.dual?.let { match ->
                            DualSetup(tankTypes = current.tankTypes, raceLaps = match.options.raceLaps, round = match.round, wins = match.wins)
                        },
                        onMissionEnd = { outcome ->
                            controls.resetAll()
                            val match = current.dual
                            screen = if (match == null) {
                                missionGate.missionEnded(current.mission, current.mode, outcome)
                            } else {
                                missionGate.roundEnded(match, outcome?.let { if (it == MissionOutcome.WON) 0 else 1 })
                            }
                        },
                    )

                    is DesktopShellScreen.NetworkPlaying -> GameplayScreen(
                        mission = current.mission,
                        mode = current.mode,
                        controls = controls,
                        players = current.seats.size,
                        network = current.match,
                        seats = current.seats,
                        soundVolume = options.soundVolume,
                        windowSize = options.windowSize,
                        showRadar = options.showRadar,
                        smoothMotion = options.smoothMotion,
                        fill = options.fillWindow,
                        wide = options.wideView,
                        ruleset = Ruleset.EXTENDED,
                        onMissionEnd = { outcome ->
                            controls.resetAll()
                            val session = current.session
                            val lost = (current.match as? RemoteMatchClient)?.endedReason
                            screen = when {
                                // Left with ESC (or the host is gone): the game is over for this machine.
                                outcome == null && lost != null -> {
                                    session.close()
                                    DesktopShellScreen.Message(lost.uppercase(), DesktopShellScreen.NetworkMenu)
                                }
                                outcome == null -> {
                                    session.close()
                                    DesktopShellScreen.NetworkMenu
                                }
                                session is NetworkSession.Hosting -> {
                                    // In player vs player WON means team 1 won the round.
                                    val versus = current.mode == GameMode.PLAYER_VS_PLAYER
                                    val result = session.series!!.roundEnded(
                                        winner = if (versus) (if (outcome == MissionOutcome.WON) 0 else 1) else null,
                                        missionWon = if (versus) null else outcome == MissionOutcome.WON,
                                    )
                                    DesktopShellScreen.NetworkResult(session, result)
                                }
                                else -> DesktopShellScreen.NetworkLobby(session, finished = current.match)
                            }
                        },
                    )

                    // Not recorded: the map may never be saved, so a recording could not be replayed.
                    is DesktopShellScreen.PlayTest -> GameplayScreen(
                        mission = current.mission,
                        mode = current.mode,
                        controls = controls,
                        players = sessionPlayers(current.mode),
                        soundVolume = options.soundVolume,
                        windowSize = options.windowSize,
                        showRadar = options.showRadar,
                        smoothMotion = extended && options.smoothMotion,
                        fill = extended && options.fillWindow,
                        wide = extended && options.wideView,
                        ruleset = options.ruleset ?: Ruleset.CLASSIC,
                        cheats = cheats,
                        dual = if (current.mode == GameMode.PLAYER_VS_PLAYER) DualSetup() else null,
                        record = false,
                        onMissionEnd = {
                            controls.resetAll()
                            screen = current.editing
                        },
                    )

                    is DesktopShellScreen.AfterMission -> if (extended) {
                        ExtendedTextPage(
                            title = if (current.won) "Mission completed" else "Mission failed",
                            text = current.text,
                            onClose = { screen = DesktopShellScreen.GameModeSelect },
                        )
                    } else {
                        TextViewerScreen(text = current.text, onClose = { screen = DesktopShellScreen.GameModeSelect })
                    }

                    DesktopShellScreen.EditorOpen -> EditorOpenScreen(
                        onOpen = { choice -> screen = DesktopShellScreen.Editor(choice, nl.tankarena.sim.map.MapEditor(choice.load())) },
                        onNew = { screen = DesktopShellScreen.EditorNew },
                        onBack = { screen = DesktopShellScreen.MainMenu },
                    )

                    DesktopShellScreen.EditorNew -> NewMapScreen(
                        onCreate = { choice, document -> screen = DesktopShellScreen.Editor(choice, editor = nl.tankarena.sim.map.MapEditor(document, unsaved = true)) },
                        onBack = { screen = DesktopShellScreen.EditorOpen },
                    )

                    is DesktopShellScreen.Editor -> EditorScreen(
                        choice = current.map,
                        editor = current.editor,
                        onPlayTest = { document ->
                            val mission = MissionEntry.of(current.map.baseName, document)
                            val mode = when (mission.sidecar.metadata.modeCompatibility) {
                                GameModeCompatibility.DUAL -> GameMode.PLAYER_VS_PLAYER
                                GameModeCompatibility.DUAL_VS_COMPUTER -> GameMode.DUAL_PLAYER_VS_COMPUTER
                                else -> GameMode.SINGLE_PLAYER_VS_COMPUTER
                            }
                            controls.resetAll()
                            screen = DesktopShellScreen.PlayTest(current, mission, mode)
                        },
                        onClose = {
                            // A saved map may be one the game lists; read the maps again.
                            missions = null
                            screen = DesktopShellScreen.EditorOpen
                        },
                    )
                }
            }
        }
        }
    }
}

/** The second keyboard player plays with player two's keys (the cursor block by default). */
private const val SECOND_KEYBOARD_SET = 1

private fun MatchMode.toGameMode(): GameMode = when (this) {
    MatchMode.SINGLE -> GameMode.SINGLE_PLAYER_VS_COMPUTER
    MatchMode.DUAL -> GameMode.PLAYER_VS_PLAYER
    MatchMode.DUAL_VS_COMPUTER -> GameMode.DUAL_PLAYER_VS_COMPUTER
}

private fun GameMode.toMatchMode(): MatchMode = when (this) {
    GameMode.SINGLE_PLAYER_VS_COMPUTER -> MatchMode.SINGLE
    GameMode.PLAYER_VS_PLAYER -> MatchMode.DUAL
    GameMode.DUAL_PLAYER_VS_COMPUTER -> MatchMode.DUAL_VS_COMPUTER
}

@Composable
private fun GameplayScreen(
    mission: MissionEntry,
    mode: GameMode,
    controls: DesktopControls,
    /** The game options' sound volume (legacy `sound_volume`). */
    soundVolume: Int,
    ruleset: Ruleset,
    cheats: Cheats = Cheats(),
    /** Whether the match is saved as a recording when it ends. */
    record: Boolean = true,
    /** Legacy `win_size` and `show_radar`. */
    windowSize: Int = 0,
    showRadar: Boolean = true,
    /** Extended's presentation: movement between steps, filling the window, a wider view. */
    smoothMotion: Boolean = false,
    fill: Boolean = false,
    wide: Boolean = false,
    /** Player vs player: the round's vehicles and score. */
    dual: DualSetup? = null,
    /** Local players: up to four as teams in Extended (ADR 0027). */
    players: Int = if (mode == GameMode.SINGLE_PLAYER_VS_COMPUTER) 1 else 2,
    /** A network game (ADR 0024): the match hosted here or shown from the host, instead of a local one. */
    network: MatchClient? = null,
    /** In a network game: this machine's players' ids in the match, in order. */
    seats: List<Int>? = null,
    /**
     * Called once the screen has faded out; the outcome is null when the player left with ESC.
     * In player vs player WON means player 1 won the round, LOST player 2.
     */
    onMissionEnd: (MissionOutcome?) -> Unit,
) {
    val platform = LocalGamePlatform.current
    val host: MatchClient = remember(mission.baseName, mode, dual, players, network) {
        // One player at a network machine plays with the single player's keys.
        controls.mode = if (network != null && players == 1) GameMode.SINGLE_PLAYER_VS_COMPUTER else mode
        controls.exitRequested = false
        controls.paused = false
        // Extended: player one has the keyboard and mouse (and the first gamepad when alone); the
        // arrow keys are theirs too unless a second player is on the keyboard.
        if (controls.extended && controls.seats == null) {
            controls.seats = listOf(Seat(keys = null, pad = 0.takeIf { platform.gamepads().isNotEmpty() }, mouse = true))
        }
        controls.mouseArrows = controls.seats?.none { it.keys != null } ?: true
        network ?: LocalMatchClient.fromMap(
            mission.document, mode.toMatchMode(), recordAs = mission.baseName.takeIf { record }, dual = dual ?: DualSetup(),
            ruleset = ruleset, cheats = cheats, players = players,
        )
    }
    val audio = remember { AudioManager(platform.audioBackend()) }
    SideEffect { audio.soundVolume = soundVolume }
    DisposableEffect(audio) { onDispose(audio::dispose) }
    val eventQueues = remember(host) { List(MAX_PLAYERS) { ViewportEventQueue() } }
    val playerIds = seats ?: (0 until players).toList()
    // What the simulation thread last published, and what the screen shows.
    val published = remember(host) { AtomicReference(GameplayView(host.currentServerFrame())) }
    var view by remember(host) { mutableStateOf(published.get()) }
    val serverFrame = view.frame
    val missionEndBanner = view.banner

    DisposableEffect(host) {
        val loop = SimulationLoop(gameplayStep(host, controls, playerIds, eventQueues, audio, published))
        PerfLog.start(mission.baseName)
        loop.start()
        onDispose {
            loop.stop()
            host.dispose()
        }
    }

    // Every frame the screen takes the newest published state; once the game has faded out, the
    // mission ends here, on the UI thread.
    // Extended's smooth motion draws between steps, so it redraws on every frame.
    var frameNanos by remember { mutableLongStateOf(System.nanoTime()) }
    LaunchedEffect(host) {
        while (true) {
            val latest = withFrameNanos { now ->
                PerfLog.frame(now)
                if (smoothMotion) frameNanos = now
                published.get()
            }
            if (latest !== view) view = latest
            if (latest.ended) {
                onMissionEnd(latest.banner)
                break
            }
        }
    }

    val interpolator = remember(host) { StepInterpolator() }
    // A touch screen without a gamepad: player one's virtual controller over the game.
    val touchPad = remember { TouchPad() }
    val touchControls = platform.touchScreen && platform.gamepads().isEmpty()
    SideEffect { controls.touch = touchPad.takeIf { touchControls } }
    Box(modifier = Modifier.fillMaxSize().then(if (controls.extended) Modifier.mouseInput(controls.mouse) else Modifier)) {
    GameplayFrame(
        mission = mission,
        mode = mode,
        serverFrame = if (smoothMotion) interpolator.frame(serverFrame, frameNanos) else serverFrame,
        playerIds = playerIds,
        eventQueues = eventQueues,
        missionEndBanner = missionEndBanner,
        brightness = view.brightness,
        windowSize = windowSize,
        showRadar = showRadar,
        playerOneMessage = controls.playerOneMessage(),
        fill = fill,
        wide = wide,
        mouse = controls.mouse.takeIf { controls.extended },
        mousePlayerId = controls.seats?.indexOfFirst { it.mouse }?.let { playerIds.getOrNull(it) },
    )
    if (touchControls) {
        TouchControls(
            pad = touchPad,
            onPause = {
                // Legacy P: the game stands still; the button again goes on.
                controls.resetAll()
                controls.paused = !controls.paused
            },
            // Legacy ESC: the game ends (with Extended's question first).
            onExit = { controls.exitRequested = true },
        )
    }
    }
}

/** What one simulation tick hands to the screen. */
private data class GameplayView(
    val frame: ServerFrame,
    /** The outcome once the mission has been won or lost (legacy `game.winner`). */
    val banner: MissionOutcome? = null,
    /** Legacy fade: 0 black .. 1 the full palette. */
    val brightness: Float = 0f,
    /** The game has faded out; the screen should end the mission. */
    val ended: Boolean = false,
)

/**
 * One tick of legacy single_game()'s loop, run by [SimulationLoop]: read the controls, advance
 * the match, hand events to the viewports and the sound, and run the end-of-round banner and the
 * fades. start_fade_in() as the game starts, start_fade_out() once the round has ended or ESC was
 * pressed; the game runs on during both.
 */
private fun gameplayStep(
    host: MatchClient,
    controls: DesktopControls,
    playerIds: List<Int>,
    eventQueues: List<ViewportEventQueue>,
    audio: AudioManager,
    published: AtomicReference<GameplayView>,
): () -> Boolean {
    var inputSequence = 0L
    var outcome: MissionOutcome? = null
    var bannerTicksLeft = Long.MAX_VALUE
    var fadeTicks = 0L
    var fadingOut = false
    val fadeTotal = (GAME_FADE_MILLIS / FixedStepClock.MILLIS_PER_TICK).coerceAtLeast(1)
    return step@{
        // Legacy P: the game stands still (the timer does not count) until a key is pressed; not in a network game.
        if (controls.paused && host is LocalMatchClient) return@step true
        for ((local, playerId) in playerIds.withIndex()) {
            host.submitInput(controls.toInputFrame(playerId = local, inputSequence = inputSequence, frame = published.get().frame, seat = playerId))
        }
        inputSequence++
        val tickStart = System.nanoTime()
        val frame = host.tick()
        PerfLog.tick(System.nanoTime() - tickStart, tickStart)
        eventQueues.forEach { it.push(frame.world.events) }
        audio.processEvents(frame.world.events, frame.playerViews)
        if (outcome == null) {
            outcome = frame.world.events.firstNotNullOfOrNull { event ->
                when (event) {
                    is GameEvent.MissionWon -> MissionOutcome.WON
                    is GameEvent.MissionLost -> MissionOutcome.LOST
                    else -> null
                }
            }
            // Legacy game.end_round = tinit(300): the game runs on under the banner for 3 s.
            if (outcome != null) bannerTicksLeft = MISSION_END_BANNER_MILLIS / FixedStepClock.MILLIS_PER_TICK
        } else {
            bannerTicksLeft--
        }
        // A network game whose host left or whose connection broke ends like ESC.
        val lost = (host as? RemoteMatchClient)?.endedReason != null && outcome == null
        if (!fadingOut && (bannerTicksLeft <= 0 || controls.exitRequested || lost)) {
            fadingOut = true
            fadeTicks = fadeTotal - fadeTicks
        }
        fadeTicks = (fadeTicks + 1).coerceAtMost(fadeTotal)
        val brightness = if (fadingOut) 1f - fadeTicks.toFloat() / fadeTotal else fadeTicks.toFloat() / fadeTotal
        val ended = fadingOut && fadeTicks >= fadeTotal
        if (ended) ((host as? LocalMatchClient)?.recorder ?: (host as? HostedMatch)?.recorder)?.let { RecordingStore.save(it.recording()) }
        published.set(GameplayView(frame, outcome, brightness, ended))
        !ended
    }
}


/**
 * Legacy read_controls() (original/src/game/controls.c) in a game: ESC leaves, P pauses until the next key,
 * F11 steps the gamma correction (and tells player one), F12 shows or hides the radar, = and - make
 * the window bigger and smaller, PrtScr saves a screenshot; everything else goes to the players.
 */
private fun handleGlobalKeys(
    event: KeyEvent,
    screen: DesktopShellScreen,
    controls: DesktopControls,
    settings: GameSettings,
    screenshot: () -> Unit,
): Boolean {
    if (screen !is DesktopShellScreen.Playing && screen !is DesktopShellScreen.PlayTest && screen !is DesktopShellScreen.NetworkPlaying) return false
    val down = event.type == KeyEventType.KeyDown
    if (controls.paused) {
        // Legacy wait_key(): any key goes on with the game, and is used up; held keys are let go.
        if (down) {
            controls.paused = false
            controls.resetAll()
        }
        return true
    }
    if (!down) return controls.handle(event)
    val options = settings.options
    when (event.key) {
        // Legacy controls.c: ESC sets game.exit_game, the game fades out and ends.
        Key.Escape -> controls.exitRequested = true
        // A network game does not stand still for one player.
        Key.P -> if (screen !is DesktopShellScreen.NetworkPlaying) {
            controls.resetAll()
            controls.paused = true
        }
        Key.F11 -> {
            val changed = options.nextGamma()
            settings.update(changed)
            controls.showPlayerOneMessage("GAMMA CORRECTION", "LEVEL ${changed.gamma}")
        }
        Key.F12 -> settings.update(options.copy(showRadar = !options.showRadar))
        Key.Equals, Key.Plus, Key.NumPadAdd -> settings.update(options.biggerWindow())
        Key.Minus, Key.NumPadSubtract -> settings.update(options.smallerWindow())
        Key.PrintScreen -> screenshot()
        else -> return controls.handle(event)
    }
    return true
}

/**
 * Legacy grab_screen() (original/src/game/grab.c): the screen as it is, saved as TANK<n> (here a PNG in the
 * data folder's `screenshots`).
 */
private fun saveScreenshot(platform: GamePlatform, settings: GameSettings) {
    val number = settings.options.screenshotNumber
    val file = java.io.File(AppDirs.data, "screenshots/TANK$number.png")
    file.parentFile?.mkdirs()
    if (platform.screenshot(file)) settings.update(settings.options.copy(screenshotNumber = number + 1))
}

/**
 * Legacy select_tanks(): each player moves their marker with their own drive keys and confirms
 * with fire (`keys[i][PLAYER_UP]` ...). Player 1 uses the left-hand keys, player 2 the cursor block.
 */
private fun selectionKeyFor(key: Key, controls: List<PlayerControls>): Pair<Int, SelectionKey>? {
    for ((player, binding) in controls.take(2).map(KeyBinding::of).withIndex()) {
        val action = when (key) {
            binding.forward -> SelectionKey.UP
            binding.reverse -> SelectionKey.DOWN
            binding.turnLeft -> SelectionKey.LEFT
            binding.turnRight -> SelectionKey.RIGHT
            binding.fire -> SelectionKey.FIRE
            else -> null
        }
        if (action != null) return player to action
    }
    return null
}

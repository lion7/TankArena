package com.tankarena.app.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.tankarena.audio.AudioManager
import com.tankarena.audio.DesktopAudioBackend
import com.tankarena.core.FixedStepClock
import com.tankarena.protocol.InputFrame
import com.tankarena.protocol.snapshot.GameEvent
import com.tankarena.protocol.snapshot.ServerFrame
import com.tankarena.render.ViewportEventQueue
import com.tankarena.sim.DualSetup
import com.tankarena.sim.LocalMatchClient
import com.tankarena.sim.MatchMode
import com.tankarena.content.GameModeCompatibility
import com.tankarena.sim.Cheats
import com.tankarena.protocol.snapshot.TankState
import com.tankarena.protocol.snapshot.LockMarkerState
import com.tankarena.ui.compose.menu.PlayerControls
import com.tankarena.ui.compose.menu.DEFAULT_CONTROLS
import com.tankarena.ui.compose.menu.ControlMode
import com.tankarena.ui.compose.menu.ControlAction
import com.tankarena.sim.MatchRecording
import com.tankarena.sim.Ruleset
import com.tankarena.sim.SimulationLoop
import com.tankarena.ui.compose.extended.CampaignScreen
import com.tankarena.ui.compose.extended.ExtendedHomeActions
import com.tankarena.ui.compose.extended.ExtendedControlsPage
import com.tankarena.ui.compose.extended.ExtendedHelpPage
import com.tankarena.ui.compose.extended.ExtendedTextPage
import com.tankarena.ui.compose.extended.RulesetChoicePage
import com.tankarena.ui.compose.extended.ExtendedHomeScreen
import com.tankarena.ui.compose.extended.ExtendedSettingsPage
import com.tankarena.ui.compose.extended.LocalGameScreen
import com.tankarena.ui.compose.menu.RulesetChoiceScreen
import com.tankarena.ui.compose.DesktopShellScreen
import com.tankarena.ui.compose.GAME_FADE_MILLIS
import com.tankarena.ui.compose.GameMode
import com.tankarena.ui.compose.MissionOutcome
import com.tankarena.audio.MusicPlayer
import com.tankarena.content.TankArenaWorld
import com.tankarena.ui.compose.gammaCorrection
import com.tankarena.ui.compose.menu.Credits
import com.tankarena.ui.compose.menu.CreditsScreen
import com.tankarena.ui.compose.menu.FliScreen
import com.tankarena.ui.compose.menu.GameModeMenuScreen
import com.tankarena.ui.compose.menu.GameOptionsScreen
import com.tankarena.ui.compose.menu.GameOptionsStore
import com.tankarena.ui.compose.menu.GameSettings
import com.tankarena.ui.compose.menu.IntroStep
import com.tankarena.ui.compose.menu.HelpFile
import com.tankarena.ui.compose.menu.MainMenuScreen
import com.tankarena.ui.compose.menu.MessageBarScreen
import com.tankarena.ui.compose.menu.MISSION_END_BANNER_MILLIS
import com.tankarena.ui.compose.menu.MissionCatalog
import com.tankarena.ui.compose.menu.MissionCodes
import com.tankarena.ui.compose.menu.MissionEntry
import com.tankarena.ui.compose.menu.MissionGate
import com.tankarena.ui.compose.menu.PasswordStore
import com.tankarena.ui.compose.menu.PickListLayout
import com.tankarena.ui.compose.menu.PickListScreen
import com.tankarena.ui.compose.menu.PromptBarScreen
import com.tankarena.ui.compose.menu.TankSelectScreen
import com.tankarena.ui.compose.menu.TextViewerScreen
import com.tankarena.ui.compose.menu.ControlsSetupScreen
import com.tankarena.ui.compose.menu.YesNoScreen
import com.tankarena.ui.compose.menu.SelectionKey
import game_client.generated.resources.Res
import game_client.generated.resources.pic_tankico
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource

/** `-Dtankarena.debugOverlay=true` draws viewport/camera geometry over the playfield. */

fun main(args: Array<String>) {
    val cheats = Cheats.fromArgs(args)
    runGame(args, cheats)
}

private fun runGame(args: Array<String>, cheats: Cheats) = application {
    val controls = remember { DesktopControls() }
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
    }
    val missionGate = remember { MissionGate(PasswordStore(), settings) }

    // Legacy init_player(): TUNE.MID from the start, BELVED.MID while a night map is played; in
    // a game the music is turned down to 30% (palette.c fades it with the picture).
    val music = remember { MusicPlayer() }
    DisposableEffect(music) { onDispose(music::dispose) }
    val playing = (screen as? DesktopShellScreen.Playing)?.mission ?: (screen as? DesktopShellScreen.PlayTest)?.mission
    val night = playing?.sidecar?.metadata?.world == TankArenaWorld.NIGHT
    LaunchedEffect(night) {
        withContext(Dispatchers.IO) { music.play(if (night) MusicPlayer.NIGHT_TUNE else MusicPlayer.TUNE) }
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
            DesktopShellScreen.TankSelect(com.tankarena.ui.compose.DualMatch(mission, options = settings.options))
        } else {
            DesktopShellScreen.Playing(mission, GameMode.SINGLE_PLAYER_VS_COMPUTER)
        }
    }
    LaunchedEffect(Unit) {
        val seconds = System.getenv("TANKARENA_QUIT_AFTER")?.toLongOrNull() ?: return@LaunchedEffect
        delay(seconds * 1000)
        exitApplication()
    }

    val windowRef = remember { AtomicReference<java.awt.Window?>(null) }
    Window(
        onCloseRequest = ::exitApplication,
        title = "Tank Arena Rewrite",
        // The original's TANKICO picture.
        icon = painterResource(Res.drawable.pic_tankico),
        state = rememberWindowState(placement = WindowPlacement.Maximized),
        onPreviewKeyEvent = { event ->
            handleGlobalKeys(event, screen, controls, settings) { saveScreenshot(windowRef.get(), settings) }
        },
    ) {
        SideEffect { windowRef.set(window) }
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
                        onExit = ::exitApplication,
                    )

                    DesktopShellScreen.ExtendedMenu -> {
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
                                quit = ::exitApplication,
                            ),
                        )
                    }

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
                                onTogether = { screen = DesktopShellScreen.Campaign(GameMode.DUAL_PLAYER_VS_COMPUTER) },
                                onVersus = { map ->
                                    lastMode = GameMode.PLAYER_VS_PLAYER
                                    screen = missionGate.submit(all, map, GameMode.PLAYER_VS_PLAYER)
                                },
                                onBack = {
                                    extendedReturn = null
                                    screen = DesktopShellScreen.MainMenu
                                },
                            )
                        }
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
                        onClassic = {
                            settings.update(options.copy(ruleset = Ruleset.CLASSIC))
                            screen = DesktopShellScreen.MainMenu
                        },
                        onBack = { screen = DesktopShellScreen.MainMenu },
                    )

                    DesktopShellScreen.ExtendedHelp -> ExtendedHelpPage(
                        onArenaInfo = { screen = DesktopShellScreen.Help(HelpFile.ARENA_INFO) },
                        onKeys = { screen = DesktopShellScreen.Help(HelpFile.DEFAULT_KEYS) },
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
                            controls = options.controls,
                            onChange = { settings.update(settings.options.copy(controls = it)) },
                            onBack = { screen = current.next },
                        )
                    } else {
                        ControlsSetupScreen(
                        initial = options.controls,
                        onDone = { changed ->
                            changed?.let { settings.update(settings.options.copy(controls = it)) }
                            screen = current.next
                        },
                    )
                    }

                    DesktopShellScreen.ExitConfirm -> YesNoScreen(
                        question = "EXIT TO DOS? (Y/N)",
                        onYes = ::exitApplication,
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
                                footer = "Enter starts",
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

                    // Not recorded: the map may never be saved, so a recording could not be replayed.
                    is DesktopShellScreen.PlayTest -> GameplayScreen(
                        mission = current.mission,
                        mode = current.mode,
                        controls = controls,
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
                        onOpen = { choice -> screen = DesktopShellScreen.Editor(choice) },
                        onBack = { screen = DesktopShellScreen.MainMenu },
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
    /**
     * Called once the screen has faded out; the outcome is null when the player left with ESC.
     * In player vs player WON means player 1 won the round, LOST player 2.
     */
    onMissionEnd: (MissionOutcome?) -> Unit,
) {
    val host = remember(mission.baseName, mode, dual) {
        controls.mode = mode
        controls.exitRequested = false
        LocalMatchClient.fromMap(mission.document, mode.toMatchMode(), recordAs = mission.baseName.takeIf { record }, dual = dual ?: DualSetup(), ruleset = ruleset, cheats = cheats)
    }
    val audio = remember { AudioManager(DesktopAudioBackend()) }
    SideEffect { audio.soundVolume = soundVolume }
    DisposableEffect(audio) { onDispose(audio::dispose) }
    val eventQueues = remember(host) { List(2) { ViewportEventQueue() } }
    val playerIds = if (mode == GameMode.SINGLE_PLAYER_VS_COMPUTER) listOf(0) else listOf(0, 1)
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
    )
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
    host: LocalMatchClient,
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
        // Legacy P: the game stands still (the timer does not count) until a key is pressed.
        if (controls.paused) return@step true
        for (playerId in playerIds) {
            host.submitInput(controls.toInputFrame(playerId = playerId, inputSequence = inputSequence, frame = published.get().frame))
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
        if (!fadingOut && (bannerTicksLeft <= 0 || controls.exitRequested)) {
            fadingOut = true
            fadeTicks = fadeTotal - fadeTicks
        }
        fadeTicks = (fadeTicks + 1).coerceAtMost(fadeTotal)
        val brightness = if (fadingOut) 1f - fadeTicks.toFloat() / fadeTotal else fadeTicks.toFloat() / fadeTotal
        val ended = fadingOut && fadeTicks >= fadeTotal
        if (ended) host.recorder?.let { RecordingStore.save(it.recording()) }
        published.set(GameplayView(frame, outcome, brightness, ended))
        !ended
    }
}


/**
 * Legacy read_controls() (src/game/controls.c) in a game: ESC leaves, P pauses until the next key,
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
    if (screen !is DesktopShellScreen.Playing && screen !is DesktopShellScreen.PlayTest) return false
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
        Key.P -> {
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
 * Legacy grab_screen() (src/game/grab.c): the window as it is, saved as TANK<n> (here a PNG in
 * `~/.local/share/tankarena/screenshots`).
 */
private fun saveScreenshot(window: java.awt.Window?, settings: GameSettings) {
    val content = (window as? javax.swing.JFrame)?.contentPane ?: return
    try {
        val image = java.awt.Robot().createScreenCapture(java.awt.Rectangle(content.locationOnScreen, content.size))
        val number = settings.options.screenshotNumber
        val file = java.io.File(RecordingStore.directory().parentFile, "screenshots/TANK$number.png")
        file.parentFile.mkdirs()
        javax.imageio.ImageIO.write(image, "png", file)
        settings.update(settings.options.copy(screenshotNumber = number + 1))
    } catch (e: Exception) {
        System.err.println("Could not save a screenshot: ${e.message}")
    }
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

/** One player's keys, in the order of the legacy setup program's key list; null for an empty slot. */
private class KeyBinding(
    val forward: Key?,
    val reverse: Key?,
    val turnLeft: Key?,
    val turnRight: Key?,
    val aimLeft: Key?,
    val aimRight: Key?,
    val fire: Key?,
    val shield: Key?,
    val weaponLeft: Key?,
    val weaponRight: Key?,
) {
    companion object {
        fun of(controls: PlayerControls) = KeyBinding(
            forward = controls.key(ControlAction.UP),
            reverse = controls.key(ControlAction.DOWN),
            turnLeft = controls.key(ControlAction.LEFT),
            turnRight = controls.key(ControlAction.RIGHT),
            aimLeft = controls.key(ControlAction.AIM_LEFT),
            aimRight = controls.key(ControlAction.AIM_RIGHT),
            fire = controls.key(ControlAction.FIRE),
            shield = controls.key(ControlAction.SHIELD),
            weaponLeft = controls.key(ControlAction.WEAPON_LEFT),
            weaponRight = controls.key(ControlAction.WEAPON_RIGHT),
        )

        /** Extended only: a WASD layout for one player, kept from earlier builds. */
        val WASD = KeyBinding(
            forward = Key.W, reverse = Key.S, turnLeft = Key.A, turnRight = Key.D,
            aimLeft = Key.Q, aimRight = Key.E, fire = Key.CtrlLeft, shield = Key.ShiftLeft,
            weaponLeft = Key.R, weaponRight = Key.F,
        )
    }
}

private class HeldKeys {
    var forward = false
    var reverse = false
    var turnLeft = false
    var turnRight = false
    var aimLeft = false
    var aimRight = false
    var fire = false
    var shield = false
    var weaponLeft = false
    var weaponRight = false
    /** One-shot F1–F10 request; cleared once sent. */
    var selectTankId = 0

    fun apply(binding: KeyBinding, key: Key, pressed: Boolean): Boolean {
        when (key) {
            binding.forward -> forward = pressed
            binding.reverse -> reverse = pressed
            binding.turnLeft -> turnLeft = pressed
            binding.turnRight -> turnRight = pressed
            binding.aimLeft -> aimLeft = pressed
            binding.aimRight -> aimRight = pressed
            binding.fire -> fire = pressed
            binding.shield -> shield = pressed
            binding.weaponLeft -> weaponLeft = pressed
            binding.weaponRight -> weaponRight = pressed
            else -> return false
        }
        return true
    }
}

/** Legacy msg_control(): 29 steps of tinit(10), 12 ticks each on legacy steps. */
private const val MESSAGE_STEPS = 29
private const val MESSAGE_STEP_NANOS = 120_000_000L

private val TANK_SELECT_KEYS: List<Key> = listOf(
    Key.F1, Key.F2, Key.F3, Key.F4, Key.F5, Key.F6, Key.F7, Key.F8, Key.F9, Key.F10,
)

/** The keys held down; written on the UI thread, read by the simulation thread. */
private class DesktopControls {
    var mode: GameMode = GameMode.SINGLE_PLAYER_VS_COMPUTER
    /** ESC during a game (legacy `game.exit_game`); read by the simulation thread. */
    @Volatile
    var exitRequested = false

    /** Legacy `game_pause` (P): the game stands still until the next key. */
    @Volatile
    var paused = false

    @Volatile
    private var message: Pair<HudMessage, Long>? = null

    /** Legacy create_message() on player one's window, for keys the shell handles (F11). */
    fun showPlayerOneMessage(line1: String, line2: String) {
        message = HudMessage(line1, line2, MESSAGE_STEPS) to System.nanoTime()
    }

    /** The shell's message as it counts down: one step every 12 ticks (legacy msg_control()). */
    fun playerOneMessage(): HudMessage? {
        val (shown, since) = message ?: return null
        val steps = MESSAGE_STEPS - ((System.nanoTime() - since) / MESSAGE_STEP_NANOS).toInt()
        return if (steps > 0) shown.copy(steps = steps) else null
    }
    private val players = List(2) { HeldKeys() }

    /** The players' keys and modes (legacy TANK.CFG), set by the shell. */
    @Volatile
    var settings: List<PlayerControls> = DEFAULT_CONTROLS

    /** Extended also takes WASD for a single player. */
    @Volatile
    var extended: Boolean = false

    /**
     * Legacy `keys[]`: a single player uses player two's keys (KEYS.HLP: "player two, or player for
     * single player game"); two players have their own.
     */
    private fun controlsFor(playerId: Int): PlayerControls? = when {
        mode == GameMode.SINGLE_PLAYER_VS_COMPUTER -> if (playerId == 0) settings.getOrNull(1) else null
        else -> settings.getOrNull(playerId)
    }

    private fun bindingsFor(playerId: Int): List<KeyBinding> {
        val own = controlsFor(playerId)?.let(KeyBinding::of) ?: return emptyList()
        return if (extended && mode == GameMode.SINGLE_PLAYER_VS_COMPUTER) listOf(own, KeyBinding.WASD) else listOf(own)
    }

    @Synchronized
    fun handle(event: KeyEvent): Boolean {
        val pressed = event.type == KeyEventType.KeyDown
        val tankId = TANK_SELECT_KEYS.indexOf(event.key) + 1
        if (tankId > 0) {
            // Legacy read_controls(): F1–F10 pick a tank for `game.id[id_select]`. In two players vs
            // computer `id_select` is 1 (the keypad player) and 0 while Alt is held.
            if (pressed && mode != GameMode.PLAYER_VS_PLAYER) {
                val player = when {
                    mode != GameMode.DUAL_PLAYER_VS_COMPUTER -> 0
                    event.isAltPressed -> 0
                    else -> 1
                }
                players[player].selectTankId = tankId
            }
            return true
        }
        var handled = false
        for (playerId in players.indices) {
            for (binding in bindingsFor(playerId)) {
                if (players[playerId].apply(binding, event.key, pressed)) handled = true
            }
        }
        return handled
    }

    @Synchronized
    fun toInputFrame(playerId: Int, inputSequence: Long, frame: ServerFrame? = null): InputFrame {
        val p = players[playerId]
        val selectTankId = p.selectTankId
        p.selectTankId = 0
        val relative = InputFrame(
            playerId = playerId,
            inputSequence = inputSequence,
            forward = p.forward,
            reverse = p.reverse,
            turnLeft = p.turnLeft,
            turnRight = p.turnRight,
            aimLeft = p.aimLeft,
            aimRight = p.aimRight,
            firePrimary = p.fire,
            shield = p.shield,
            cycleWeaponLeft = p.weaponLeft,
            cycleWeaponRight = p.weaponRight,
            selectTankId = selectTankId,
        )
        if (controlsFor(playerId)?.mode != ControlMode.DIRECT || frame == null) return relative
        val view = frame.playerViews.firstOrNull { it.playerId == playerId } ?: return relative
        // Legacy control_tank(): while the rocket marker is out the keys always steer it as usual.
        val markerOut = view.lockMarker?.let { it.state != LockMarkerState.LAUNCHED } == true
        val tank = frame.world.actors.firstOrNull { it is TankState && it.actorId == view.controlledActorId } as TankState?
        if (markerOut || tank == null) return relative
        return directControls(relative, tank.bodyDirection, tank.turretDirection)
    }

    @Synchronized
    fun resetAll() {
        players.forEach { p ->
            p.forward = false
            p.reverse = false
            p.turnLeft = false
            p.turnRight = false
            p.aimLeft = false
            p.aimRight = false
            p.fire = false
            p.shield = false
            p.weaponLeft = false
            p.weaponRight = false
            p.selectTankId = 0
        }
    }
}

/**
 * Legacy copy_controls_direct() (src/game/controls.c:339-402): the drive keys point the way to go.
 * The vehicle turns the short way towards it and drives forward (never in reverse); with nothing
 * pressed it stops. With an aim key held the keys point the turret instead, and the drive is left
 * as it was.
 */
internal fun directControls(keys: InputFrame, bodyDirection: Int, turretDirection: Int): InputFrame {
    val x = (if (keys.turnRight) 100 else 0) - (if (keys.turnLeft) 100 else 0)
    val y = (if (keys.reverse) 100 else 0) - (if (keys.forward) 100 else 0)
    val aiming = keys.aimLeft || keys.aimRight
    if (x == 0 && y == 0) {
        return keys.copy(forward = false, reverse = false, turnLeft = false, turnRight = false, aimLeft = false, aimRight = false)
    }
    val wanted = com.tankarena.core.LegacyDirections.fromAngle(x, y)
    // Legacy go_left_or_right(): left when the other way round is shorter.
    fun turnLeft(actual: Int) = ((actual - wanted) and 15) < 8
    return if (aiming) {
        val turn = wanted != turretDirection
        keys.copy(
            forward = false, reverse = false, turnLeft = false, turnRight = false,
            aimLeft = turn && turnLeft(turretDirection),
            aimRight = turn && !turnLeft(turretDirection),
        )
    } else {
        val turn = wanted != bodyDirection
        keys.copy(
            forward = true, reverse = false,
            turnLeft = turn && turnLeft(bodyDirection),
            turnRight = turn && !turnLeft(bodyDirection),
        )
    }
}


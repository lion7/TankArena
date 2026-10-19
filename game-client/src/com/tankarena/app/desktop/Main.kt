package com.tankarena.app.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.tankarena.audio.AudioManager
import com.tankarena.audio.DesktopAudioBackend
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.core.FixedStepClock
import com.tankarena.protocol.InputFrame
import com.tankarena.protocol.snapshot.GameEvent
import com.tankarena.protocol.snapshot.PlayerView
import com.tankarena.protocol.snapshot.TankState
import com.tankarena.protocol.snapshot.ServerFrame
import com.tankarena.render.kubriko.TankArenaViewport
import com.tankarena.render.kubriko.ViewportEventQueue
import com.tankarena.render.kubriko.ViewportGeometry
import com.tankarena.render.kubriko.legacyMapWindows
import com.tankarena.sim.kubriko.server.DualSetup
import com.tankarena.sim.kubriko.server.LocalMatchClient
import com.tankarena.sim.kubriko.server.MatchMode
import com.tankarena.ui.compose.DesktopShellScreen
import com.tankarena.ui.compose.GAME_FADE_MILLIS
import com.tankarena.ui.compose.GameMode
import com.tankarena.ui.compose.LegacyScreen
import com.tankarena.ui.compose.LegacyVideoMode
import com.tankarena.ui.compose.MissionOutcome
import com.tankarena.ui.compose.hud.HudOverlay
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
import com.tankarena.ui.compose.menu.MissionEndBanner
import com.tankarena.ui.compose.menu.YouWinBanner
import com.tankarena.ui.compose.menu.MissionEntry
import com.tankarena.ui.compose.menu.MissionGate
import com.tankarena.ui.compose.menu.PanelPattern
import com.tankarena.ui.compose.menu.PasswordStore
import com.tankarena.ui.compose.menu.PickListLayout
import com.tankarena.ui.compose.menu.PickListScreen
import com.tankarena.ui.compose.menu.PromptBarScreen
import com.tankarena.ui.compose.menu.TankSelectScreen
import com.tankarena.ui.compose.menu.TextViewerScreen
import com.tankarena.ui.compose.menu.SelectionKey
import game_client.generated.resources.Res
import game_client.generated.resources.pic_tankico
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource

/** `-Dtankarena.debugOverlay=true` draws viewport/camera geometry over the playfield. */
private val showDebugOverlay: Boolean = System.getProperty("tankarena.debugOverlay") == "true"

fun main() = application {
    val controls = remember { DesktopControls() }
    var screen by remember { mutableStateOf<DesktopShellScreen>(DesktopShellScreen.Intro(IntroStep.LOGO)) }
    // Legacy menu(): the game mode menu opens on its first entry, and keeps the chosen one
    // while games are played from it.
    var lastMode by remember { mutableStateOf<GameMode?>(null) }
    // Legacy `pl_tanks`: the vehicles last picked on the selection screen.
    var playerTanks by remember { mutableStateOf(listOf(0, 0)) }
    var missions by remember { mutableStateOf<List<MissionEntry>?>(null) }
    // Legacy TANK.CFG: the game options, saved whenever they change.
    val settings = remember { GameSettings(GameOptionsStore()) }
    val options = settings.options
    val missionGate = remember { MissionGate(PasswordStore(), settings) }

    // Legacy init_player(): TUNE.MID from the start, BELVED.MID while a night map is played; in
    // a game the music is turned down to 30% (palette.c fades it with the picture).
    val music = remember { MusicPlayer() }
    DisposableEffect(music) { onDispose(music::dispose) }
    val playing = screen as? DesktopShellScreen.Playing
    val night = playing?.mission?.sidecar?.metadata?.world == TankArenaWorld.NIGHT
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
        val all = MissionCatalog.load().also { missions = it }
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

    Window(
        onCloseRequest = ::exitApplication,
        title = "Tank Arena Rewrite",
        // The original's TANKICO picture.
        icon = painterResource(Res.drawable.pic_tankico),
        state = rememberWindowState(placement = WindowPlacement.Maximized),
        onPreviewKeyEvent = { event ->
            handleGlobalKeys(event, screen, controls)
        },
    ) {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize().gammaCorrection(options.gamma), color = Color.Black) {
                when (val current = screen) {
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
                        onLeave = { screen = DesktopShellScreen.MainMenu },
                    )

                    DesktopShellScreen.MainMenu -> MainMenuScreen(
                        onStartNewGame = {
                            lastMode = null
                            screen = DesktopShellScreen.GameModeSelect
                        },
                        onExit = ::exitApplication,
                        onGameOptions = { screen = DesktopShellScreen.Options },
                        onArenaInfo = { screen = DesktopShellScreen.Help(HelpFile.ARENA_INFO) },
                        onDefaultKeys = { screen = DesktopShellScreen.Help(HelpFile.DEFAULT_KEYS) },
                    )

                    is DesktopShellScreen.Help -> TextViewerScreen(
                        text = remember(current.file) { current.file.load() },
                        onClose = { screen = DesktopShellScreen.MainMenu },
                    )

                    DesktopShellScreen.GameModeSelect -> {
                        LaunchedEffect(Unit) {
                            if (missions == null) missions = MissionCatalog.load()
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

                    is DesktopShellScreen.Briefing -> TextViewerScreen(
                        text = current.mission.sidecar.missionText.briefing,
                        onClose = {
                            controls.resetAll()
                            screen = missionGate.afterBriefing(current.mission, current.mode)
                        },
                    )

                    is DesktopShellScreen.TankSelect -> TankSelectScreen(
                        match = current.match,
                        selectMap = missions.orEmpty().firstOrNull { it.baseName.equals("select", ignoreCase = true) },
                        initial = playerTanks,
                        keyFor = ::selectionKeyFor,
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

                    is DesktopShellScreen.AfterMission -> TextViewerScreen(
                        text = current.text,
                        onClose = { screen = DesktopShellScreen.GameModeSelect },
                    )

                    DesktopShellScreen.Editor -> MainMenuScreen(
                        onStartNewGame = { screen = DesktopShellScreen.GameModeSelect },
                        onExit = ::exitApplication,
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
        LocalMatchClient.fromSceneJson(mission.sceneJson, mission.sidecar, mode.toMatchMode(), recordAs = mission.baseName, dual = dual ?: DualSetup())
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
    LaunchedEffect(host) {
        while (true) {
            val latest = withFrameNanos { frameNanos ->
                PerfLog.frame(frameNanos)
                published.get()
            }
            if (latest !== view) view = latest
            if (latest.ended) {
                onMissionEnd(latest.banner)
                break
            }
        }
    }

    val worldWidth = mission.sidecar.metadata.widthTiles * LEGACY_TILE_SIZE
    val worldHeight = mission.sidecar.metadata.heightTiles * LEGACY_TILE_SIZE
    val views = playerIds.mapNotNull { id -> serverFrame.playerViews.firstOrNull { it.playerId == id } }
    val windows = legacyMapWindows(views.size.coerceAtLeast(1), worldWidth, worldHeight)
    val night = mission.sidecar.metadata.night

    LegacyScreen(mode = LegacyVideoMode.GAME, brightness = view.brightness) {
        val screen = this
        // Legacy set_palette() with `nuke_total` brightens the whole screen, panel included.
        Box(modifier = Modifier.fillMaxSize().nukeFlash(serverFrame.world.flash)) {
            screen.PanelPattern()
            views.forEachIndexed { index, view ->
                val window = windows[index]
                // Legacy write_screen(): the losing player's window turns grey (not at night).
                val lost = !night && if (mode == GameMode.PLAYER_VS_PLAYER) view.hud.roundLost else missionEndBanner == MissionOutcome.LOST
                Box(
                    modifier = with(screen) { Modifier.legacyBounds(window.left, window.top, window.width, window.height) }
                        .grayscale(lost),
                ) {
                    PlayerPane(
                        mission = mission,
                        serverFrame = serverFrame,
                        playerView = view,
                        events = eventQueues[index],
                        worldWidth = worldWidth,
                        worldHeight = worldHeight,
                        playfieldWidth = window.width,
                        playfieldHeight = window.height,
                        playerVsPlayer = mode == GameMode.PLAYER_VS_PLAYER,
                        // Legacy write_arrow(): only player-vs-player points at the other player.
                        opponent = if (mode == GameMode.PLAYER_VS_PLAYER) {
                            val otherId = views.getOrNull(1 - index)?.controlledActorId
                            serverFrame.world.actors.firstOrNull { it is TankState && it.actorId == otherId } as TankState?
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Legacy write_winner_1p in every window; player vs player write_winner_2p's
                    // "YOU WIN" in the winner's window only.
                    val banner = missionEndBanner
                    if (mode == GameMode.PLAYER_VS_PLAYER) {
                        if (view.hud.roundWon) YouWinBanner(playfieldWidth = window.width, night = mission.sidecar.metadata.night)
                    } else if (banner != null) {
                        MissionEndBanner(outcome = banner, playfieldWidth = window.width, night = mission.sidecar.metadata.night)
                    }
                }
            }
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
        for (playerId in playerIds) {
            host.submitInput(controls.toInputFrame(playerId = playerId, inputSequence = inputSequence))
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

@Composable
private fun PlayerPane(
    mission: MissionEntry,
    serverFrame: ServerFrame,
    playerView: PlayerView,
    events: ViewportEventQueue,
    worldWidth: Int,
    worldHeight: Int,
    playfieldWidth: Int,
    playfieldHeight: Int,
    playerVsPlayer: Boolean,
    modifier: Modifier,
    opponent: TankState? = null,
) {
    var viewportRect by remember { mutableStateOf(Rect.Zero) }
    var viewportGeometry by remember { mutableStateOf<ViewportGeometry?>(null) }
    Box(modifier = modifier) {
        TankArenaViewport(
            sidecar = mission.sidecar,
            serverFrame = serverFrame,
            playerId = playerView.playerId,
            worldWidth = worldWidth,
            worldHeight = worldHeight,
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    viewportRect = coordinates.boundsInParent()
                },
            events = events,
            playfieldWidth = playfieldWidth,
            playfieldHeight = playfieldHeight,
            onGeometryChanged = { viewportGeometry = it },
        )
        val geometry = viewportGeometry
        if (geometry != null && viewportRect != Rect.Zero) {
            val sceneWidth = playfieldWidth.toFloat()
            val sceneHeight = playfieldHeight.toFloat()
            val scale = viewportRect.width / sceneWidth
            HudOverlay(
                hudState = playerView.hud,
                playerView = playerView,
                playfield = visiblePlayfield(geometry, viewportRect, sceneWidth, sceneHeight),
                scale = scale,
                playerVsPlayer = playerVsPlayer,
                night = mission.sidecar.metadata.night,
                opponent = opponent?.takeIf { !it.cloaked },
                ownCloaked = serverFrame.world.actors.any { it is TankState && it.actorId == playerView.controlledActorId && it.cloaked },
                viewLeft = (geometry.cameraCenterX - sceneWidth / 2f - geometry.renderOffsetX).coerceAtLeast(0f),
                viewTop = (geometry.cameraCenterY - sceneHeight / 2f - geometry.renderOffsetY).coerceAtLeast(0f),
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (showDebugOverlay) {
            ViewportDebugOverlay(
                playerView = playerView,
                geometry = viewportGeometry,
                viewportRect = viewportRect,
                sceneWidth = playfieldWidth.toFloat(),
                sceneHeight = playfieldHeight.toFloat(),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Draws the content without colour (legacy `b_convert(scr, gray, ...)` for a lost mission). */
private fun Modifier.grayscale(enabled: Boolean): Modifier = if (!enabled) this else drawWithContent {
    val paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
    drawIntoCanvas { it.saveLayer(Rect(Offset.Zero, size), paint) }
    drawContent()
    drawIntoCanvas { it.restore() }
}

/**
 * Legacy set_palette() with `nuke_total`: while an A-bomb goes off every colour is brightened by
 * up to 63 of 63 steps, which adds that much white to the picture.
 */
private fun Modifier.nukeFlash(level: Int): Modifier = if (level <= 0) this else drawWithContent {
    drawContent()
    val add = (level.coerceAtMost(63) / 63f)
    drawRect(color = Color(add, add, add), blendMode = androidx.compose.ui.graphics.BlendMode.Plus)
}

/**
 * The part of the viewport that shows the map, in pane pixels. Legacy maps smaller than the
 * 640×400 playfield are shown in a window of the map's own size, and the status display sits in
 * that window's corners.
 */
private fun visiblePlayfield(geometry: ViewportGeometry, viewportRect: Rect, sceneWidth: Float, sceneHeight: Float): Rect {
    val scale = viewportRect.width / sceneWidth
    val sceneLeft = geometry.cameraCenterX - sceneWidth / 2f
    val sceneTop = geometry.cameraCenterY - sceneHeight / 2f
    val left = maxOf(geometry.renderOffsetX, sceneLeft)
    val top = maxOf(geometry.renderOffsetY, sceneTop)
    val right = minOf(geometry.renderOffsetX + geometry.worldWidth, sceneLeft + sceneWidth)
    val bottom = minOf(geometry.renderOffsetY + geometry.worldHeight, sceneTop + sceneHeight)
    return Rect(
        left = viewportRect.left + (left - sceneLeft) * scale,
        top = viewportRect.top + (top - sceneTop) * scale,
        right = viewportRect.left + (right - sceneLeft) * scale,
        bottom = viewportRect.top + (bottom - sceneTop) * scale,
    )
}

@Composable
private fun ViewportDebugOverlay(
    playerView: PlayerView,
    geometry: ViewportGeometry?,
    viewportRect: Rect,
    sceneWidth: Float,
    sceneHeight: Float,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                color = Color(0xFF00D7FF),
                style = Stroke(width = 1f),
            )
            if (viewportRect != Rect.Zero) {
                drawRect(
                    color = Color(0xFFFFD54F),
                    topLeft = viewportRect.topLeft,
                    size = viewportRect.size,
                    style = Stroke(width = 2f),
                )
            }
            if (geometry != null && viewportRect != Rect.Zero) {
                val viewportSceneWidth = sceneWidth
                val viewportSceneHeight = sceneHeight
                val sceneLeft = geometry.cameraCenterX - viewportSceneWidth / 2f
                val sceneTop = geometry.cameraCenterY - viewportSceneHeight / 2f
                val scaleX = viewportRect.width / viewportSceneWidth
                val scaleY = viewportRect.height / viewportSceneHeight
                val worldLeft = viewportRect.left + (geometry.renderOffsetX - sceneLeft) * scaleX
                val worldTop = viewportRect.top + (geometry.renderOffsetY - sceneTop) * scaleY
                val worldWidthPx = geometry.worldWidth * scaleX
                val worldHeightPx = geometry.worldHeight * scaleY
                drawRect(
                    color = Color(0xFF4CAF50),
                    topLeft = Offset(worldLeft, worldTop),
                    size = androidx.compose.ui.geometry.Size(worldWidthPx, worldHeightPx),
                    style = Stroke(width = 2f),
                )
                val cameraCenter = Offset(
                    x = viewportRect.left + viewportRect.width / 2f,
                    y = viewportRect.top + viewportRect.height / 2f,
                )
                drawLine(
                    color = Color(0xFFFF5252),
                    start = Offset(cameraCenter.x - 8f, cameraCenter.y),
                    end = Offset(cameraCenter.x + 8f, cameraCenter.y),
                    strokeWidth = 2f,
                )
                drawLine(
                    color = Color(0xFFFF5252),
                    start = Offset(cameraCenter.x, cameraCenter.y - 8f),
                    end = Offset(cameraCenter.x, cameraCenter.y + 8f),
                    strokeWidth = 2f,
                )
            }
        }
        if (geometry != null) {
            Text(
                text = buildString {
                    append("viewport=")
                    append(viewportRect.width.toInt())
                    append("x")
                    append(viewportRect.height.toInt())
                    append(" scene=")
                    append(geometry.sceneWidth)
                    append("x")
                    append(geometry.sceneHeight)
                    append(" world=")
                    append(geometry.worldWidth)
                    append("x")
                    append(geometry.worldHeight)
                    append(" offset=")
                    append(geometry.renderOffsetX.toInt())
                    append(",")
                    append(geometry.renderOffsetY.toInt())
                    append(" camera=")
                    append(geometry.cameraCenterX.toInt())
                    append(",")
                    append(geometry.cameraCenterY.toInt())
                },
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .background(Color(0xCC101010))
                    .padding(6.dp),
            )
        }
    }
}

private fun handleGlobalKeys(
    event: KeyEvent,
    screen: DesktopShellScreen,
    controls: DesktopControls,
): Boolean {
    return when (screen) {
        is DesktopShellScreen.Playing -> {
            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                // Legacy controls.c: ESC sets game.exit_game, the game fades out and ends.
                controls.exitRequested = true
                true
            } else {
                controls.handle(event)
            }
        }
        else -> false
    }
}

/**
 * Legacy select_tanks(): each player moves their marker with their own drive keys and confirms
 * with fire (`keys[i][PLAYER_UP]` ...). Player 1 uses the left-hand keys, player 2 the cursor block.
 */
private fun selectionKeyFor(key: Key): Pair<Int, SelectionKey>? {
    for ((player, binding) in listOf(0 to KeyBinding.PLAYER_ONE, 1 to KeyBinding.CURSOR_BLOCK)) {
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

/** One player's keys, in the order of the legacy setup program's key list. */
private class KeyBinding(
    val forward: Key,
    val reverse: Key,
    val turnLeft: Key,
    val turnRight: Key,
    val aimLeft: Key,
    val aimRight: Key,
    val fire: Key,
    val shield: Key,
    val weaponLeft: Key,
    val weaponRight: Key,
) {
    companion object {
        /** DATA/KEYS.HLP "Player one": K , M . drive, Z fire, A shield, S/D weapon, X/C turret. */
        val PLAYER_ONE = KeyBinding(
            forward = Key.K, reverse = Key.Comma, turnLeft = Key.M, turnRight = Key.Period,
            aimLeft = Key.X, aimRight = Key.C, fire = Key.Z, shield = Key.A,
            weaponLeft = Key.S, weaponRight = Key.D,
        )

        /** DATA/KEYS.HLP "Player two (or player for single player game)": the grey cursor block. */
        val CURSOR_BLOCK = KeyBinding(
            forward = Key.DirectionUp, reverse = Key.DirectionDown, turnLeft = Key.DirectionLeft, turnRight = Key.DirectionRight,
            aimLeft = Key.MoveEnd, aimRight = Key.PageDown, fire = Key.Delete, shield = Key.Insert,
            weaponLeft = Key.MoveHome, weaponRight = Key.PageUp,
        )

        /** Modern single-player layout kept from earlier builds. */
        val WASD = KeyBinding(
            forward = Key.W, reverse = Key.S, turnLeft = Key.A, turnRight = Key.D,
            aimLeft = Key.Q, aimRight = Key.E, fire = Key.CtrlLeft, shield = Key.ShiftLeft,
            weaponLeft = Key.R, weaponRight = Key.F,
        )
    }
}

private class PlayerControls {
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

private val TANK_SELECT_KEYS: List<Key> = listOf(
    Key.F1, Key.F2, Key.F3, Key.F4, Key.F5, Key.F6, Key.F7, Key.F8, Key.F9, Key.F10,
)

/** The keys held down; written on the UI thread, read by the simulation thread. */
private class DesktopControls {
    var mode: GameMode = GameMode.SINGLE_PLAYER_VS_COMPUTER
    /** ESC during a game (legacy `game.exit_game`); read by the simulation thread. */
    @Volatile
    var exitRequested = false
    private val players = List(2) { PlayerControls() }

    /** Single player uses the legacy cursor block (or WASD); two players split the keyboard. */
    private fun bindingsFor(playerId: Int): List<KeyBinding> = when {
        mode == GameMode.SINGLE_PLAYER_VS_COMPUTER -> if (playerId == 0) listOf(KeyBinding.CURSOR_BLOCK, KeyBinding.WASD) else emptyList()
        playerId == 0 -> listOf(KeyBinding.PLAYER_ONE)
        else -> listOf(KeyBinding.CURSOR_BLOCK)
    }

    @Synchronized
    fun handle(event: KeyEvent): Boolean {
        val pressed = event.type == KeyEventType.KeyDown
        val tankId = TANK_SELECT_KEYS.indexOf(event.key) + 1
        if (tankId > 0) {
            // Legacy controls.c: F1–F10 switch player one's tank, Alt+F-key player two's in co-op.
            if (pressed && mode != GameMode.PLAYER_VS_PLAYER) {
                val player = if (event.isAltPressed && mode == GameMode.DUAL_PLAYER_VS_COMPUTER) 1 else 0
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
    fun toInputFrame(playerId: Int, inputSequence: Long): InputFrame {
        val p = players[playerId]
        val selectTankId = p.selectTankId
        p.selectTankId = 0
        return InputFrame(
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

package com.tankarena.ui.compose.menu

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.layout.ContentScale
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.protocol.snapshot.HudState
import com.tankarena.protocol.snapshot.PlayerView
import com.tankarena.protocol.snapshot.ServerFrame
import com.tankarena.protocol.snapshot.TankState
import com.tankarena.render.kubriko.LegacySpriteCatalog
import com.tankarena.render.kubriko.TankArenaViewport
import com.tankarena.sim.LegacyVehicles
import com.tankarena.sim.LocalMatchClient
import com.tankarena.sim.MatchMode
import com.tankarena.sim.VehicleKind
import com.tankarena.ui.compose.DualMatch
import com.tankarena.ui.compose.GAME_FADE_MILLIS
import com.tankarena.ui.compose.LegacyScreenScope
import com.tankarena.ui.compose.legacy.LegacyColors
import com.tankarena.ui.compose.legacy.LegacyFonts
import com.tankarena.ui.compose.legacy.Mode13View
import java.util.concurrent.atomic.AtomicReference
import org.jetbrains.compose.resources.imageResource

/** A key of the selection screen, already sorted by player. */
enum class SelectionKey { UP, DOWN, LEFT, RIGHT, FIRE }

/** Legacy select_tanks(): the eight vehicles players can pick, four across and two down. */
private const val CHOICES = 8
private const val WINDOW = 60

/** Legacy `MAPC_*` (src/include/define.h). */
private const val MAPC_TANK = 1
private const val MAPC_CHOP = 2
private const val MAPC_PLANE = 4
private const val MAPC_RACE = 8

/** Legacy select_tanks() border colours: player 1, player 2, both. */
private const val BORDER_P1 = 143
private const val BORDER_P2 = 127
private const val BORDER_BOTH = 64

/**
 * Legacy select_tanks(): which of the eight vehicles a map allows. [losersTank] is the game
 * option (legacy `lose`): the losers tank is there for a player 1 or 3 rounds behind.
 */
internal fun allowedTanks(mapContents: Int, wins: List<Int>, losersTank: Int = 0): List<Boolean> = List(CHOICES) { tank ->
    when (tank) {
        in 0..5 -> when (LegacyVehicles.spec(tank).kind) {
            VehicleKind.TANK -> mapContents and MAPC_TANK != 0
            VehicleKind.CHOPPER -> mapContents and MAPC_CHOP != 0
            VehicleKind.PLANE -> mapContents and MAPC_PLANE != 0
            else -> false
        }
        6 -> mapContents and MAPC_RACE != 0
        // The losers tank: only with the option on and one player far enough behind.
        else -> mapContents and MAPC_TANK != 0 && losersTank > 0 && kotlin.math.abs(wins[0] - wins[1]) >= losersTank * 2 - 1
    }
}

/** Legacy select_tanks(): a vehicle the map does not allow is swapped for the first one it does. */
internal fun autoSelect(chosen: List<Int>, allowed: List<Boolean>): List<Int> = chosen.map { tank ->
    if (tank in 0 until CHOICES && allowed[tank]) tank else (0 until 7).firstOrNull { allowed[it] } ?: tank
}

/**
 * Legacy select_tanks() (src/game/seltank.c), before every player-vs-player round: the SELECT
 * map plays live behind eight 60×60 windows, one on each vehicle. Each player moves a coloured
 * frame with their own drive keys and confirms with fire (the losers tank only when behind);
 * Enter confirms both, ESC leaves the match. Vehicles the map has no room for are grey.
 */
@Composable
fun TankSelectScreen(
    match: DualMatch,
    selectMap: MissionEntry?,
    initial: List<Int>,
    keyFor: (Key) -> Pair<Int, SelectionKey>?,
    onChosen: (List<Int>) -> Unit,
    onCancel: () -> Unit,
) {
    val allowed = remember(match) { allowedTanks(match.mission.sidecar.metadata.mapContents, match.wins, match.options.losersTank) }
    var chosen by remember(match) { mutableStateOf(autoSelect(initial, allowed)) }
    var confirmed by remember(match) { mutableIntStateOf(0) }
    var cancelled by remember(match) { mutableStateOf(false) }

    // The SELECT map runs as in a player-vs-player game: its vehicles are all computer driven.
    val host = remember(selectMap) { selectMap?.let { LocalMatchClient.fromSceneJson(it.sceneJson, it.sidecar, MatchMode.DUAL) } }
    val published = remember(host) { AtomicReference(host?.currentServerFrame()) }
    var frame by remember(host) { mutableStateOf(published.get()) }
    DisposableEffect(host) {
        if (host == null) return@DisposableEffect onDispose { }
        val loop = com.tankarena.app.desktop.SimulationLoop {
            published.set(host.tick())
            true
        }
        loop.start()
        onDispose {
            loop.stop()
            host.dispose()
        }
    }
    LaunchedEffect(host) {
        while (true) {
            val latest = withFrameNanos { published.get() }
            if (latest !== frame) frame = latest
        }
    }

    // Legacy start_fade_in() / start_fade_out(): 0.6 s each.
    val brightness = remember(match) { androidx.compose.animation.core.Animatable(0f) }
    val leaving = confirmed == 3 || cancelled
    LaunchedEffect(leaving) {
        val spec = androidx.compose.animation.core.tween<Float>(GAME_FADE_MILLIS, easing = androidx.compose.animation.core.LinearEasing)
        if (!leaving) {
            brightness.animateTo(1f, spec)
        } else {
            brightness.animateTo(0f, spec)
            if (cancelled) onCancel() else onChosen(chosen)
        }
    }

    fun canConfirm(player: Int): Boolean {
        val tank = chosen[player]
        return allowed[tank] && (tank != CHOICES - 1 || match.wins[player] < match.wins[player xor 1])
    }

    Mode13View(
        brightness = brightness.value,
        onKey = { event ->
            if (leaving) return@Mode13View true
            when (event.key) {
                Key.Enter, Key.NumPadEnter -> if (canConfirm(0) && canConfirm(1)) confirmed = 3
                Key.Escape -> cancelled = true
                else -> {
                    val (player, action) = keyFor(event.key) ?: return@Mode13View false
                    if (confirmed and (1 shl player) == 0) {
                        val tank = chosen[player]
                        val next = when (action) {
                            SelectionKey.UP -> tank - 4
                            SelectionKey.DOWN -> tank + 4
                            SelectionKey.LEFT -> tank - 1
                            SelectionKey.RIGHT -> tank + 1
                            SelectionKey.FIRE -> {
                                if (canConfirm(player)) confirmed = confirmed or (1 shl player)
                                tank
                            }
                        }
                        chosen = chosen.toMutableList().also { it[player] = next.coerceIn(0, CHOICES - 1) }
                    }
                }
            }
            true
        },
        overlay = {
            val current = frame
            for (tank in 0 until CHOICES) {
                val left = 16 + 76 * (tank % 4)
                val top = 26 + 86 * (tank / 4)
                val state = current?.world?.actors?.firstOrNull { it is TankState && it.tankType == tank } as TankState?
                if (current != null && state != null && selectMap != null) {
                    val border = when {
                        chosen[0] == tank && chosen[1] == tank -> BORDER_BOTH
                        chosen[0] == tank -> BORDER_P1
                        chosen[1] == tank -> BORDER_P2
                        else -> -1
                    }
                    VehicleWindow(selectMap, current, state, tank, left, top, gray = !allowed[tank], border = border)
                }
                // Legacy m13_add_image(..., TANKICO): a player who has confirmed.
                if (chosen[0] == tank && confirmed and 1 != 0) TankIcon(left - 14, top - 11)
                if (chosen[1] == tank && confirmed and 2 != 0) TankIcon(left + 30, top - 11)
            }
        },
    ) {
        panel()
        writeText(100, 5, LegacyColors.HIGH, "SELECT YOUR TANKS:")
        for (tank in 0 until CHOICES) {
            val name = LegacyVehicles.spec(tank).name
            writeText(46 + 76 * (tank % 4) - name.length * 3, 91 + 86 * (tank / 4), LegacyColors.HIGH, name)
        }
    }
}

/** One 60×60 window on a vehicle of the SELECT map (its top-left corner 13 px up and left). */
@Composable
private fun LegacyScreenScope.VehicleWindow(
    selectMap: MissionEntry,
    frame: ServerFrame,
    state: TankState,
    tank: Int,
    left: Int,
    top: Int,
    gray: Boolean,
    border: Int,
) {
    val worldWidth = selectMap.sidecar.metadata.widthTiles * LEGACY_TILE_SIZE
    val worldHeight = selectMap.sidecar.metadata.heightTiles * LEGACY_TILE_SIZE
    val view = PlayerView(
        playerId = tank,
        controlledActorId = null,
        cameraCenterX = state.x + 1,
        cameraCenterY = state.y + 1,
        hud = HudState(),
    )
    val side = x(WINDOW)
    val stretch = pixelHeight / pixelWidth
    Box(modifier = Modifier.legacyBounds(left, top, WINDOW, WINDOW).wrapContentSize(Alignment.TopStart, unbounded = true)) {
        // Mode 13h pixels are taller than wide: draw the square view and stretch it.
        Box(
            modifier = Modifier
                .requiredSize(side, side)
                .graphicsLayer {
                    scaleY = stretch
                    transformOrigin = TransformOrigin(0f, 0f)
                }
                .gray(gray),
        ) {
            TankArenaViewport(
                sidecar = selectMap.sidecar,
                serverFrame = frame.copy(playerViews = listOf(view)),
                playerId = tank,
                worldWidth = worldWidth,
                worldHeight = worldHeight,
                modifier = Modifier.fillMaxSize(),
                playfieldWidth = WINDOW,
                playfieldHeight = WINDOW,
            )
            if (border >= 0) {
                val color = Color(0xFF000000.toInt() or LegacyFonts.gamePalette[border])
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val unit = size.width / WINDOW
                    val bar = 2 * unit
                    drawRect(color, Offset.Zero, Size(size.width, bar))
                    drawRect(color, Offset(0f, size.height - bar), Size(size.width, bar))
                    drawRect(color, Offset.Zero, Size(bar, size.height))
                    drawRect(color, Offset(size.width - bar, 0f), Size(bar, size.height))
                }
            }
        }
    }
}

@Composable
private fun LegacyScreenScope.TankIcon(left: Int, top: Int) {
    val resource = LegacySpriteCatalog.shared.findResource("TANKICO") ?: return
    Image(
        bitmap = imageResource(resource),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        filterQuality = FilterQuality.None,
        modifier = Modifier.legacyBounds(left, top, LEGACY_TILE_SIZE, LEGACY_TILE_SIZE),
    )
}

/** Legacy `b_convert(scr, gray, ...)`: a vehicle the map does not allow is shown grey. */
private fun Modifier.gray(enabled: Boolean): Modifier = if (!enabled) this else drawWithContent {
    val paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
    drawIntoCanvas { it.saveLayer(Rect(Offset.Zero, size), paint) }
    drawContent()
    drawIntoCanvas { it.restore() }
}

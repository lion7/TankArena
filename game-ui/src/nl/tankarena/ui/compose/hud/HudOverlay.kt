package nl.tankarena.ui.compose.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import nl.tankarena.content.LEGACY_TILE_SIZE
import nl.tankarena.protocol.Team
import nl.tankarena.protocol.snapshot.HudState
import nl.tankarena.protocol.snapshot.MayhemHud
import nl.tankarena.protocol.snapshot.PlayerView
import nl.tankarena.protocol.snapshot.RadarColor
import nl.tankarena.protocol.snapshot.TankState
import nl.tankarena.protocol.snapshot.RadarContactKind
import nl.tankarena.render.LegacySpriteCatalog
import nl.tankarena.ui.compose.legacy.LegacyColors
import nl.tankarena.ui.compose.legacy.LegacyFonts
import kotlin.math.floor
import org.jetbrains.compose.resources.imageResource

/** Legacy `cmap[15]`: the messages and the ammo count; also the radar's friends. */
private val WHITE = paletteColor(LegacyColors.HIGH)

/** A colour of the game palette, exactly, so that the night table finds it. */
private fun paletteColor(index: Int) = Color(0xFF000000.toInt() or LegacyFonts.gamePalette[index])

/** Legacy `num_weapon`. */
private const val WEAPON_COUNT = 12
private const val FLAME_WEAPON = 2
/** The HUD's picture names, made once. */
private object HudNames {
    val METERS = listOf("METER2A", "METER2B", "METER3A", "METER3B")
    val SMALL_ICONS = (1..15).map { "ICON$it" }
    val LARGE_ICONS = (1..15).map { "XCON$it" }
    val ARROWS = (1..8).map { "ARROW$it" }
    val FLAG_ICONS = listOf("VLAG3", "VLAG4")
    val PRODUCTS = (0 until 15).map { nl.tankarena.render.productSpriteName(it) }
    /** The meters' numbers 0..99, drawn every frame. */
    val NUMBERS = Array(100) { it.toString() }
    private val faces = HashMap<Int, List<String>>()

    fun faces(vehicleType: Int): List<String> = faces.getOrPut(vehicleType) { (1..5).map { "FACE${vehicleType + 1}-$it" } }
}

/** Legacy radar (original/src/game/radar.c): 15 tiles around the player, `res_x / 6` across, 4 px blips. */
private const val RADAR_RANGE_TILES = 15
private const val RADAR_RADIUS = 640 / 6 / 2
private const val RADAR_BLIP = RADAR_RADIUS / RADAR_RANGE_TILES + 1

/**
 * The legacy in-playfield status display (`write_status()` in original/src/world/status.c), drawn with the
 * original pictures at their original pixel offsets:
 * - top left `METER2A/B`: armor and fuel percentages (write_bars);
 * - top right `METER3A/B`: good and evil goal counters and the tank id (write_1p_info);
 * - bottom left: one `ICONn` per available weapon with `SELECT` over the current one, and
 *   bottom right the large `XCONn` with its ammo (write_weapons);
 * - the radar's blips near the bottom-right corner (write_radar).
 *
 * [playfield] is the visible map area inside this composable in pixels, and [scale] the pixels
 * per legacy pixel.
 */
@Composable
fun HudOverlay(
    hudState: HudState,
    playerView: PlayerView,
    playfield: Rect,
    scale: Float,
    modifier: Modifier = Modifier,
    /**
     * Legacy write_status() for DUAL: no goal counters (write_1p_info), but the pad's lives
     * (write_lives) or, in a flag hunt, the flags picked up (write_flags).
     */
    playerVsPlayer: Boolean = false,
    /** Legacy `map.night`: the light switch shows as a 13th weapon. */
    night: Boolean = false,
    /** Player vs player: the other player's vehicle, which an arrow points at when it is out of view. */
    opponent: TankState? = null,
    /** Legacy write_arrow(): no arrow while this player's own vehicle is cloaked. */
    ownCloaked: Boolean = false,
    /** World position of the playfield's top-left corner (legacy `ssx`, `ssy`). */
    viewLeft: Float = 0f,
    viewTop: Float = 0f,
    /** Legacy `show_radar` (F12). */
    showRadar: Boolean = true,
    /** No vehicle (legacy `game.tk == NULL`): only the goal counters and the picked id. */
    goalCountersOnly: Boolean = false,
) {
    val sprites = LegacySpriteCatalog.shared
    @Composable
    fun picture(name: String): ImageBitmap? = sprites.findResource(name)?.let { imageResource(it) }
    // The HUD recomposes every tick: the names are made once (see HudNames), only looked up here.
    val meters = HudNames.METERS.map { picture(it) }
    val smallIcons = HudNames.SMALL_ICONS.map { picture(it) }
    val largeIcons = HudNames.LARGE_ICONS.map { picture(it) }
    val select = picture("SELECT")
    val arrows = HudNames.ARROWS.map { picture(it) }
    val tankIcon = picture("TANKICO")
    // Legacy VLAGICON: VLAG3 for one flag, VLAG4 for two.
    val flagIcons = HudNames.FLAG_ICONS.map { picture(it) }
    // Legacy `tank[n].face[0..4]`: FACE<vehicle>-1..5 (5 for a dead pilot).
    val faces = HudNames.faces(hudState.vehicleType).map { picture(it) }
    val products = HudNames.PRODUCTS.map { picture(it) }

    // [layer] null draws everything; at night the dark and the lit pictures go to separate layers.
    val draw: DrawScope.(layer: NightLayer?) -> Unit = draw@{ layer ->
        if (playfield.width <= 0f || scale <= 0f) return@draw
        val hud = HudPainter(this, playfield, scale, lit = layer != NightLayer.DARK, dark = layer != NightLayer.LIT)
        val width = playfield.width / scale
        val height = playfield.height / scale

        if (goalCountersOnly) {
            hud.picture(meters[2], width - 2 * LEGACY_TILE_SIZE, 0f)
            hud.picture(meters[3], width - LEGACY_TILE_SIZE, 0f)
            hud.number(width - 59, 7f, hudState.missionProgress)
            hud.number(width - 38, 7f, hudState.missionProgressBad)
            hud.number(width - 17, 7f, hudState.tankId.coerceAtLeast(0))
            return@draw
        }

        hudState.mayhem?.let { mayhem ->
            drawMayhemStatus(hud, mayhem, hudState, products, height)
            return@draw
        }

        // write_bars()
        hud.picture(meters[0], 0f, 0f)
        hud.picture(meters[1], LEGACY_TILE_SIZE.toFloat(), 0f)
        hud.number(7f, 7f, percent(hudState.armor, hudState.maxArmor))
        hud.number(28f, 7f, percent(hudState.fuel, hudState.maxFuel))

        // write_lives() / write_flags()
        if (playerVsPlayer) {
            if (hudState.flagHunt) {
                if (hudState.flags > 0) hud.darkPicture(flagIcons[(hudState.flags - 1).coerceAtMost(flagIcons.size - 1)], width - 25, -8f)
            } else if (hudState.lives != PAD_LIVES) {
                for (i in 0 until hudState.lives - 1) hud.picture(tankIcon, width - LEGACY_TILE_SIZE - i * 20, 0f)
            }
        }

        // write_1p_info()
        if (!playerVsPlayer) {
            hud.picture(meters[2], width - 2 * LEGACY_TILE_SIZE, 0f)
            hud.picture(meters[3], width - LEGACY_TILE_SIZE, 0f)
            hud.number(width - 59, 7f, hudState.missionProgress)
            hud.number(width - 38, 7f, hudState.missionProgressBad)
            hud.number(width - 17, 7f, hudState.tankId.coerceAtLeast(0))
        }

        // write_weapons()
        var x = 0f
        for (weapon in 0 until WEAPON_COUNT) {
            val available = hudState.currentWeapon == weapon ||
                hudState.ammo.getOrElse(weapon) { 0 } != 0 ||
                (weapon == FLAME_WEAPON && hudState.hasFlamer)
            if (!available) continue
            hud.darkPicture(smallIcons.getOrNull(weapon), x, height - 11)
            if (hudState.currentWeapon == weapon) hud.picture(select, x, height - 11)
            x += 9f
        }
        if (night) {
            hud.darkPicture(smallIcons[LIGHT_ICON], x, height - 11)
            if (hudState.currentWeapon == LIGHT_WEAPON) hud.picture(select, x, height - 11)
        }
        val largeIcon = if (hudState.currentWeapon == LIGHT_WEAPON) LIGHT_ICON else hudState.currentWeapon
        hud.picture(largeIcons.getOrNull(largeIcon), width - 24, height - 27)
        if (hudState.currentWeapon != FLAME_WEAPON && hudState.currentWeapon != LIGHT_WEAPON) {
            val ammo = hudState.ammo.getOrElse(hudState.currentWeapon) { 0 }.toString()
            hud.text(width - ammo.length * 6 - 2, height - 8, ammo, WHITE)
        }

        if (showRadar && !hudState.radarHidden) hud.radar(playerView, width, height)

        // write_arrow(): an arrow at the edge of the view points at the other player.
        if (opponent != null && !ownCloaked) {
            arrowFor(opponent.x - 16 - viewLeft, opponent.y - 16 - viewTop, width, height)?.let { (index, x, y) ->
                hud.picture(arrows[index], x, y)
            }
        }

        // write_message(): the pilot's face talks (speech[]) while the two lines show; in their
        // last 16 steps the lines blink.
        val steps = hudState.messageSteps
        if (steps > 0) {
            val face = if (hudState.alive) SPEECH[(MESSAGE_STEPS - steps).coerceIn(0, SPEECH.size - 1)] else 4
            hud.picture(faces[face], 5f, height - 11 - 5 - LEGACY_TILE_SIZE)
            if (steps > 16 || steps % 2 == 1) {
                hud.text(10f + LEGACY_TILE_SIZE, height - 11 - LEGACY_TILE_SIZE, hudState.messageLine1, WHITE)
                hud.text(10f + LEGACY_TILE_SIZE, height - 11 - LEGACY_TILE_SIZE + 10, hudState.messageLine2, WHITE)
            }
        }
    }

    if (!night) {
        Canvas(modifier = modifier) { draw(null) }
    } else {
        val darkEffect = rememberNightHudEffect(lit = false)
        val litEffect = rememberNightHudEffect(lit = true)
        Box(modifier = modifier) {
            Canvas(Modifier.matchParentSize().graphicsLayer { renderEffect = darkEffect }) { draw(NightLayer.DARK) }
            Canvas(Modifier.matchParentSize().graphicsLayer { renderEffect = litEffect }) { draw(NightLayer.LIT) }
        }
    }
}

/**
 * Legacy write_status() in supermarket mayhem (original/src/world/status.c): products needed, time left and
 * cash at the top left, what was bought along the bottom, and only the message's second line, with
 * no face. There is no radar.
 */
private fun drawMayhemStatus(
    hud: HudPainter,
    mayhem: MayhemHud,
    hudState: HudState,
    products: List<ImageBitmap?>,
    height: Float,
) {
    val hundredths = mayhem.ticksLeft
    hud.text(2f, 2f, "PRODUCTS NEEDED: ${mayhem.productsNeeded}", WHITE)
    hud.text(2f, 9f, "TIME LEFT: %d:%02d.%02d".format(hundredths / 6000, hundredths / 100 % 60, hundredths % 100), WHITE)
    hud.text(2f, 16f, "CASH: $${mayhem.cash}", WHITE)
    // write_products()
    mayhem.counts.forEachIndexed { type, count ->
        hud.picture(products.getOrNull(type), type * 20f - 6, height - 33)
        if (count != 0) hud.text(type * 20f + 8, height - 7, count.toString(), WHITE)
    }
    // write_supermarket_message()
    val steps = hudState.messageSteps
    if (steps > 16 || steps % 2 == 1) hud.text(5f, height - 35, hudState.messageLine2, WHITE)
}

/** Legacy load_pictures() at night: pictures without `night_vis` stay in the dark half of NIGHT.PAL. */
private enum class NightLayer { DARK, LIT }

/**
 * Legacy write_arrow(): which of ARROW1..8 to show and where, for another vehicle whose top-left
 * corner is at ([x], [y]) relative to a view of [width]×[height]; null while it is in view.
 */
internal fun arrowFor(x: Float, y: Float, width: Float, height: Float): Triple<Int, Float, Float>? {
    val b = LEGACY_TILE_SIZE
    val middleX = (width - b) / 2
    val middleY = (height - b) / 2
    return when {
        x + b > width -> when {
            y < 0 -> Triple(1, width - b, 0f)
            y + b < height -> Triple(2, width - b, middleY)
            else -> Triple(3, width - b, height - b)
        }
        x > 0 -> when {
            y < 0 -> Triple(0, middleX, 0f)
            y + b > height -> Triple(4, middleX, height - b)
            else -> null
        }
        else -> when {
            y < 0 -> Triple(7, 0f, 0f)
            y + b < height -> Triple(6, 0f, middleY)
            else -> Triple(5, 0f, height - b)
        }
    }
}

/** Legacy `speech[]` (original/src/world/status.c): the pilot's face picture for each step of a message. */
private val SPEECH = intArrayOf(0, 1, 2, 3, 3, 2, 2, 3, 2, 1, 2, 3, 2, 0, 0, 1, 2, 3, 3, 2, 1, 2, 3, 2, 1, 3, 2, 0, 0, 0)
private const val MESSAGE_STEPS = 29
/** Legacy `num_weapon`: the light switch, drawn with ICON15/XCON15. */
private const val LIGHT_WEAPON = 12
private const val LIGHT_ICON = 14

/** Legacy make_player(): `ply.lives = 4`; write_lives() shows nothing while all four are left. */
private const val PAD_LIVES = 4

/** Legacy write_number(): numbers above 99 show as 99, single digits are shifted right by 3. */
private fun percent(value: Int, max: Int): Int =
    if (max <= 0) value.coerceAtLeast(0) else (value.coerceAtLeast(0) * 100 / max)

/**
 * Draws the status display's pieces. With [lit] false only the dark pictures are drawn, with
 * [dark] false only the rest (the two night layers); by day both are true.
 */
private class HudPainter(
    private val scope: DrawScope,
    private val playfield: Rect,
    private val scale: Float,
    private val lit: Boolean = true,
    private val dark: Boolean = true,
) {
    private fun point(x: Float, y: Float) = Offset(playfield.left + x * scale, playfield.top + y * scale)

    /** A picture marked `night_vis`, which stays in the lit half of the night palette. */
    fun picture(image: ImageBitmap?, x: Float, y: Float) {
        if (lit) draw(image, x, y)
    }

    /** A picture without `night_vis` (the small weapon icons, the flags), dark at night. */
    fun darkPicture(image: ImageBitmap?, x: Float, y: Float) {
        if (dark) draw(image, x, y)
    }

    private fun draw(image: ImageBitmap?, x: Float, y: Float) {
        image ?: return
        val origin = point(x, y)
        scope.drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset(origin.x.toInt(), origin.y.toInt()),
            dstSize = IntSize((image.width * scale).toInt(), (image.height * scale).toInt()),
            filterQuality = FilterQuality.None,
        )
    }

    fun number(x: Float, y: Float, value: Int) {
        val shown = value.coerceIn(0, 99)
        text(if (shown < 10) x + 3 else x, y, HudNames.NUMBERS[shown], METER_TEXT)
    }

    /** Legacy b_write_text(): the 5×5 font, 6 pixels per character, only the set pixels. */
    fun text(x: Float, y: Float, value: String, color: Color) {
        if (!lit) return
        value.forEachIndexed { index, char ->
            val code = char.code
            if (code >= 128) return@forEachIndexed
            for (row in 0 until 5) {
                val bits = LegacyFonts.small[code * 5 + row].toInt()
                for (column in 0 until 6) {
                    if (bits and (0x80 ushr column) == 0) continue
                    val topLeft = point(x + index * LegacyFonts.SMALL_PITCH + column, y + row)
                    val bottomRight = point(x + index * LegacyFonts.SMALL_PITCH + column + 1, y + row + 1)
                    // Whole device pixels, so neighbouring font pixels do not leave seams.
                    val left = floor(topLeft.x)
                    val top = floor(topLeft.y)
                    scope.drawRect(color, Offset(left, top), Size(floor(bottomRight.x) - left, floor(bottomRight.y) - top))
                }
            }
        }
    }

    /** Legacy write_radar(): blips for everything within 15 tiles, centred near the bottom-right corner. */
    fun radar(view: PlayerView, width: Float, height: Float) {
        if (!lit) return
        val centreX = width - RADAR_BLIP - RADAR_RADIUS
        val centreY = height - RADAR_BLIP - RADAR_RADIUS
        val range = RADAR_RANGE_TILES * LEGACY_TILE_SIZE
        for (contact in view.radar) {
            val dx = contact.approximateX - view.viewerX
            val dy = contact.approximateY - view.viewerY
            if (dx.toLong() * dx + dy.toLong() * dy >= range.toLong() * range) continue
            val color = when (contact.color) {
                RadarColor.SELF -> RADAR_BLACK
                RadarColor.ROCKET -> RADAR_YELLOW
                RadarColor.ENEMY -> RADAR_RED
                RadarColor.FRIEND -> WHITE
            }
            val origin = point(centreX + dx * RADAR_RADIUS / range.toFloat(), centreY + dy * RADAR_RADIUS / range.toFloat())
            scope.drawRect(color, topLeft = origin, size = Size(RADAR_BLIP * scale, RADAR_BLIP * scale))
        }
    }

    private companion object {
        /** Legacy write_number(): `cmap[13]`. */
        val METER_TEXT = paletteColor(LegacyColors.STATUS_NUMBER)

        /** Legacy write_radar() colours (original/src/game/radar.c). */
        val RADAR_BLACK = paletteColor(0)
        val RADAR_RED = paletteColor(95)
        val RADAR_YELLOW = paletteColor(64)
    }
}

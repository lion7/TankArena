package com.tankarena.ui.compose.hud

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.protocol.Team
import com.tankarena.protocol.snapshot.HudState
import com.tankarena.protocol.snapshot.PlayerView
import com.tankarena.protocol.snapshot.TankState
import com.tankarena.protocol.snapshot.RadarContactKind
import com.tankarena.render.kubriko.LegacySpriteCatalog
import com.tankarena.ui.compose.legacy.LegacyColors
import com.tankarena.ui.compose.legacy.LegacyFonts
import kotlin.math.floor
import org.jetbrains.compose.resources.imageResource

/** Legacy `num_weapon`. */
private const val WEAPON_COUNT = 12
private const val FLAME_WEAPON = 2
/** Legacy radar (src/game/radar.c): 15 tiles around the player, `res_x / 6` across, 4 px blips. */
private const val RADAR_RANGE_TILES = 15
private const val RADAR_RADIUS = 640 / 6 / 2
private const val RADAR_BLIP = RADAR_RADIUS / RADAR_RANGE_TILES + 1

/**
 * The legacy in-playfield status display (`write_status()` in src/world/status.c), drawn with the
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
    /** Player-vs-player halves have no goal counters (legacy DUAL skips write_1p_info). */
    compact: Boolean = false,
    /** Legacy `map.night`: the light switch shows as a 13th weapon. */
    night: Boolean = false,
    /** Player vs player: the other player's vehicle, which an arrow points at when it is out of view. */
    opponent: TankState? = null,
    /** Legacy write_arrow(): no arrow while this player's own vehicle is cloaked. */
    ownCloaked: Boolean = false,
    /** World position of the playfield's top-left corner (legacy `ssx`, `ssy`). */
    viewLeft: Float = 0f,
    viewTop: Float = 0f,
) {
    val sprites = LegacySpriteCatalog.shared
    @Composable
    fun picture(name: String): ImageBitmap? = sprites.findResource(name)?.let { imageResource(it) }
    val meters = listOf("METER2A", "METER2B", "METER3A", "METER3B").map { picture(it) }
    val smallIcons = (1..15).map { picture("ICON$it") }
    val largeIcons = (1..15).map { picture("XCON$it") }
    val select = picture("SELECT")
    val arrows = (1..8).map { picture("ARROW$it") }
    // Legacy `tank[n].face[0..4]`: FACE<vehicle>-1..5 (5 for a dead pilot).
    val faces = (1..5).map { picture("FACE${hudState.vehicleType + 1}-$it") }

    Canvas(modifier = modifier) {
        if (playfield.width <= 0f || scale <= 0f) return@Canvas
        val hud = HudPainter(this, playfield, scale)
        val width = playfield.width / scale
        val height = playfield.height / scale

        // write_bars()
        hud.picture(meters[0], 0f, 0f)
        hud.picture(meters[1], LEGACY_TILE_SIZE.toFloat(), 0f)
        hud.number(7f, 7f, percent(hudState.armor, hudState.maxArmor))
        hud.number(28f, 7f, percent(hudState.fuel, hudState.maxFuel))

        // write_1p_info()
        if (!compact) {
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
            hud.picture(smallIcons.getOrNull(weapon), x, height - 11)
            if (hudState.currentWeapon == weapon) hud.picture(select, x, height - 11)
            x += 9f
        }
        if (night) {
            hud.picture(smallIcons[LIGHT_ICON], x, height - 11)
            if (hudState.currentWeapon == LIGHT_WEAPON) hud.picture(select, x, height - 11)
        }
        val largeIcon = if (hudState.currentWeapon == LIGHT_WEAPON) LIGHT_ICON else hudState.currentWeapon
        hud.picture(largeIcons.getOrNull(largeIcon), width - 24, height - 27)
        if (hudState.currentWeapon != FLAME_WEAPON && hudState.currentWeapon != LIGHT_WEAPON) {
            val ammo = hudState.ammo.getOrElse(hudState.currentWeapon) { 0 }.toString()
            hud.text(width - ammo.length * 6 - 2, height - 8, ammo, Color.White)
        }

        hud.radar(playerView, width, height)

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
                hud.text(10f + LEGACY_TILE_SIZE, height - 11 - LEGACY_TILE_SIZE, hudState.messageLine1, Color.White)
                hud.text(10f + LEGACY_TILE_SIZE, height - 11 - LEGACY_TILE_SIZE + 10, hudState.messageLine2, Color.White)
            }
        }
    }
}

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

/** Legacy `speech[]` (src/world/status.c): the pilot's face picture for each step of a message. */
private val SPEECH = intArrayOf(0, 1, 2, 3, 3, 2, 2, 3, 2, 1, 2, 3, 2, 0, 0, 1, 2, 3, 3, 2, 1, 2, 3, 2, 1, 3, 2, 0, 0, 0)
private const val MESSAGE_STEPS = 29
/** Legacy `num_weapon`: the light switch, drawn with ICON15/XCON15. */
private const val LIGHT_WEAPON = 12
private const val LIGHT_ICON = 14

/** Legacy write_number(): numbers above 99 show as 99, single digits are shifted right by 3. */
private fun percent(value: Int, max: Int): Int =
    if (max <= 0) value.coerceAtLeast(0) else (value.coerceAtLeast(0) * 100 / max)

private class HudPainter(
    private val scope: DrawScope,
    private val playfield: Rect,
    private val scale: Float,
) {
    private fun point(x: Float, y: Float) = Offset(playfield.left + x * scale, playfield.top + y * scale)

    fun picture(image: ImageBitmap?, x: Float, y: Float) {
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
        text(if (shown < 10) x + 3 else x, y, shown.toString(), METER_TEXT)
    }

    /** Legacy b_write_text(): the 5×5 font, 6 pixels per character, only the set pixels. */
    fun text(x: Float, y: Float, value: String, color: Color) {
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
        val self = view.radar.firstOrNull { it.actorId == view.controlledActorId } ?: return
        val centreX = width - RADAR_BLIP - RADAR_RADIUS
        val centreY = height - RADAR_BLIP - RADAR_RADIUS
        val range = RADAR_RANGE_TILES * LEGACY_TILE_SIZE
        for (contact in view.radar) {
            val dx = contact.approximateX - self.approximateX
            val dy = contact.approximateY - self.approximateY
            if (dx.toLong() * dx + dy.toLong() * dy >= range.toLong() * range) continue
            val color = when {
                contact === self -> Color.Black
                contact.kind == RadarContactKind.PROJECTILE -> RADAR_YELLOW
                contact.team == Team.ENEMY -> RADAR_RED
                else -> Color.White
            }
            val origin = point(centreX + dx * RADAR_RADIUS / range.toFloat(), centreY + dy * RADAR_RADIUS / range.toFloat())
            scope.drawRect(color, topLeft = origin, size = Size(RADAR_BLIP * scale, RADAR_BLIP * scale))
        }
    }

    private companion object {
        /** Legacy write_number(): `cmap[13]`. */
        val METER_TEXT = Color(0xFF000000.toInt() or LegacyFonts.gamePalette[LegacyColors.STATUS_NUMBER])
        val RADAR_RED = Color(0xFFFF2020)
        val RADAR_YELLOW = Color(0xFFFFE030)
    }
}

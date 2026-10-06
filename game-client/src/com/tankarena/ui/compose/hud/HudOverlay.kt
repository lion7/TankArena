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
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.protocol.Team
import com.tankarena.protocol.snapshot.HudState
import com.tankarena.protocol.snapshot.PlayerView
import com.tankarena.protocol.snapshot.RadarContactKind
import com.tankarena.render.kubriko.LegacySpriteCatalog
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
) {
    val sprites = LegacySpriteCatalog.shared
    @Composable
    fun picture(name: String): ImageBitmap? = sprites.findResource(name)?.let { imageResource(it) }
    val meters = listOf("METER2A", "METER2B", "METER3A", "METER3B").map { picture(it) }
    val smallIcons = (1..15).map { picture("ICON$it") }
    val largeIcons = (1..15).map { picture("XCON$it") }
    val select = picture("SELECT")
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier) {
        if (playfield.width <= 0f || scale <= 0f) return@Canvas
        val hud = HudPainter(this, textMeasurer, playfield, scale)
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
        hud.picture(largeIcons.getOrNull(hudState.currentWeapon), width - 24, height - 27)
        if (hudState.currentWeapon != FLAME_WEAPON) {
            val ammo = hudState.ammo.getOrElse(hudState.currentWeapon) { 0 }.toString()
            hud.text(width - ammo.length * 6 - 2, height - 8, ammo, Color.White)
        }

        hud.radar(playerView, width, height)
    }
}

/** Legacy write_number(): numbers above 99 show as 99, single digits are shifted right by 3. */
private fun percent(value: Int, max: Int): Int =
    if (max <= 0) value.coerceAtLeast(0) else (value.coerceAtLeast(0) * 100 / max)

private class HudPainter(
    private val scope: DrawScope,
    private val textMeasurer: TextMeasurer,
    private val playfield: Rect,
    private val scale: Float,
) {
    private val textStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = with(scope) { (7f * scale).toSp() },
        letterSpacing = 0.sp,
    )

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

    fun text(x: Float, y: Float, value: String, color: Color) {
        val layout = textMeasurer.measure(value, textStyle.copy(color = color))
        // The legacy 5x7 font sits on its top line; centre the glyph box on that line.
        scope.drawText(layout, topLeft = point(x, y) - Offset(0f, layout.size.height * 0.15f))
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
        val METER_TEXT = Color(0xFFD0D0D0)
        val RADAR_RED = Color(0xFFFF2020)
        val RADAR_YELLOW = Color(0xFFFFE030)
    }
}

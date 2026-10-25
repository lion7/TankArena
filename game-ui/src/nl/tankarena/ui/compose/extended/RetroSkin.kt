package nl.tankarena.ui.compose.extended

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import nl.tankarena.render.imageFromArgb
import nl.tankarena.ui.compose.legacy.IndexedBitmap
import nl.tankarena.ui.compose.legacy.LegacyColors
import nl.tankarena.ui.compose.legacy.LegacyFonts
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Extended's look: the original's menus (original/src/game/menu.c, prompt.c) at window resolution.
 * The panel pattern, the brighter band, black boxes and the original's fonts, every legacy pixel a
 * whole number of window pixels ([LocalRetroPixel]), never smoothed.
 */
object RetroColors {
    fun palette(index: Int): Color = Color(0xFF000000.toInt() or LegacyFonts.gamePalette[index and 0xFF])

    /** `FNT_HIGH`: titles and the selected entry. */
    val high = palette(LegacyColors.HIGH)

    /** `FNT_NORM`: the other entries. */
    val normal = palette(LegacyColors.NORMAL)

    /** Running text: briefings, details. */
    val text = palette(11)
    val dim = palette(8)
    val disabled = palette(4)

    /** A sand yellow of the game palette: headings in a details box, warnings. */
    val accent = palette(66)
    val black = palette(LegacyColors.BLACK)

    /** `read_string`'s cursor block. */
    val cursor = palette(LegacyColors.CURSOR)
}

/** Window pixels per legacy pixel on an Extended page. */
val LocalRetroPixel = compositionLocalOf { 2 }

/**
 * Window pixels per legacy pixel for a page of [widthPx] × [heightPx]: about 400 legacy rows high,
 * but the big font at least [MIN_GLYPH_DP] high on a dense (phone) screen, and never fewer than 240
 * rows or 320 columns.
 */
fun retroPixel(widthPx: Int, heightPx: Int, density: Float): Int {
    val byHeight = floor(heightPx / 400f).toInt()
    val readable = ceil(MIN_GLYPH_DP * density / LegacyFonts.BIG_HEIGHT).toInt()
    val largest = max(1, min(heightPx / 240, widthPx / 320))
    return max(byHeight, readable).coerceIn(1, largest)
}

private const val MIN_GLYPH_DP = 16f

/** The original's two fonts, at the page's pixel size times [scale]. */
enum class RetroFont(val advance: Int, val lineHeight: Int, val scale: Int) {
    /** `big_font` (8×16): entries and running text. */
    BIG(LegacyFonts.BIG_WIDTH, LegacyFonts.BIG_HEIGHT, 1),

    /** The big font twice as large: page titles, as big as on the 320×200 menus. */
    TITLE(LegacyFonts.BIG_WIDTH, LegacyFonts.BIG_HEIGHT, 2),

    /** The 5×5 font, doubled: hints, subtitles and footers. */
    SMALL(LegacyFonts.SMALL_PITCH, 7, 2),
}

/** Code page 437 from 128 on: what the big font has above ASCII. */
internal const val CP437_HIGH =
    "ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜ¢£¥₧ƒáíóúñÑªº¿⌐¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐" +
        "└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀αßΓπΣσµτΦΘΩδ∞φε∩≡±≥≤⌠⌡÷≈°∙·√ⁿ²■ "

/** Characters the fonts lack, written with what they have. */
private val REPLACEMENTS = mapOf(
    '…' to "...", '–' to "-", '—' to "-", '‘' to "'", '’' to "'", '“' to "\"", '”' to "\"",
    '✓' to "√", '×' to "x", '→' to "->", '←' to "<-",
)

/** [text] as the character codes of [font]; unknown characters become '?'. */
internal fun retroCodes(text: String, font: RetroFont): String = buildString {
    for (char in text) {
        val replaced = REPLACEMENTS[char] ?: char.toString()
        for (c in replaced) {
            val code = when {
                c == '\n' -> '\n'.code
                c.code in 32..126 -> c.code
                font != RetroFont.SMALL && CP437_HIGH.indexOf(c) >= 0 -> 128 + CP437_HIGH.indexOf(c)
                // The 5×5 font has nothing above ASCII; a separating dot becomes a dash.
                c == '·' -> '-'.code
                else -> '?'.code
            }
            append(if (font == RetroFont.SMALL) code.toChar().uppercaseChar() else code.toChar())
        }
    }
}

/**
 * [codes] in lines of at most [columns] characters, broken at spaces (a word longer than a line is
 * cut), and at most [maxLines] lines; a cut last line ends in "...".
 */
internal fun retroLines(codes: String, columns: Int, maxLines: Int = Int.MAX_VALUE): List<String> {
    val width = columns.coerceAtLeast(1)
    val lines = mutableListOf<String>()
    for (paragraph in codes.split('\n')) {
        var line = StringBuilder()
        for (word in paragraph.split(' ')) {
            var rest = word
            while (true) {
                val room = if (line.isEmpty()) width else width - line.length - 1
                if (rest.length <= room) {
                    if (line.isNotEmpty()) line.append(' ')
                    line.append(rest)
                    break
                }
                if (line.isNotEmpty()) {
                    lines += line.toString()
                    line = StringBuilder()
                    continue
                }
                lines += rest.take(width)
                rest = rest.drop(width)
            }
        }
        lines += line.toString()
    }
    if (lines.size <= maxLines) return lines
    val kept = lines.take(maxLines).toMutableList()
    kept[kept.lastIndex] = kept.last().take((width - 3).coerceAtLeast(0)).trimEnd() + "..."
    return kept
}

/** [lines] drawn in [font] and [color] at one pixel per legacy pixel, each line centred if [centred]. */
private fun renderLines(lines: List<String>, font: RetroFont, color: Color, centred: Boolean): ImageBitmap? {
    val columns = lines.maxOfOrNull { it.length } ?: 0
    if (columns == 0) return null
    val width = columns * font.advance
    val height = lines.size * font.lineHeight
    val argb = IntArray(width * height)
    val ink = color.toArgb()
    for ((row, line) in lines.withIndex()) {
        var x = if (centred) (columns - line.length) * font.advance / 2 else 0
        val top = row * font.lineHeight
        for (char in line) {
            drawGlyph(argb, width, x, top, char.code, font, ink)
            x += font.advance
        }
    }
    return imageFromArgb(width, height, argb)
}

private fun drawGlyph(argb: IntArray, width: Int, x: Int, y: Int, code: Int, font: RetroFont, ink: Int) {
    if (font == RetroFont.SMALL) {
        if (code !in 0 until 128) return
        for (row in 0 until 5) {
            val bits = LegacyFonts.small[code * 5 + row].toInt()
            for (column in 0 until 6) if (bits and (0x80 ushr column) != 0) argb[x + column + (y + row + 1) * width] = ink
        }
    } else {
        val glyph = code - LegacyFonts.BIG_FIRST
        if (glyph !in 0 until LegacyFonts.big.size / LegacyFonts.BIG_HEIGHT) return
        for (row in 0 until LegacyFonts.BIG_HEIGHT) {
            val bits = LegacyFonts.big[glyph * LegacyFonts.BIG_HEIGHT + row].toInt()
            for (column in 0 until LegacyFonts.BIG_WIDTH) if (bits and (0x80 ushr column) != 0) argb[x + column + (y + row) * width] = ink
        }
    }
}

/** What a [RetroText] measured, read when it draws. */
private class RetroTextCache {
    var key: Any? = null
    var image by mutableStateOf<ImageBitmap?>(null)
    var offsetX by mutableStateOf(0)
}

/**
 * [text] in one of the original's fonts, wrapped at spaces to the width it gets ([maxLines] at most).
 * [centred] centres the lines and takes the whole width.
 */
@Composable
fun RetroText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = RetroColors.text,
    font: RetroFont = RetroFont.BIG,
    maxLines: Int = Int.MAX_VALUE,
    centred: Boolean = false,
) {
    val unit = LocalRetroPixel.current * font.scale
    val cache = remember { RetroTextCache() }
    Layout(
        modifier = modifier.drawBehind {
            val image = cache.image ?: return@drawBehind
            drawImage(
                image = image,
                dstOffset = IntOffset(cache.offsetX, 0),
                dstSize = IntSize(image.width * unit, image.height * unit),
                filterQuality = FilterQuality.None,
            )
        },
    ) { _, constraints ->
        val codes = retroCodes(text, font)
        val columns = if (constraints.hasBoundedWidth) constraints.maxWidth / (font.advance * unit) else Int.MAX_VALUE
        val lines = retroLines(codes, columns, maxLines)
        val key = listOf(lines, font, color, centred)
        if (cache.key != key) {
            cache.key = key
            cache.image = renderLines(lines, font, color, centred)
        }
        val contentWidth = (lines.maxOfOrNull { it.length } ?: 0) * font.advance * unit
        val width = if (centred && constraints.hasBoundedWidth) constraints.maxWidth else contentWidth.coerceIn(constraints.minWidth, constraints.maxWidth)
        cache.offsetX = if (centred) (width - contentWidth) / 2 else 0
        val height = (lines.size * font.lineHeight * unit).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {}
    }
}

/** A screen of [content] with [LocalRetroPixel] chosen for its size (Extended pages do this themselves). */
@Composable
fun RetroScaled(modifier: Modifier = Modifier, contentAlignment: Alignment = Alignment.TopStart, content: @Composable BoxScope.() -> Unit) {
    BoxWithConstraints(modifier = modifier, contentAlignment = contentAlignment) {
        val pixel = retroPixel(constraints.maxWidth, constraints.maxHeight, LocalDensity.current.density)
        CompositionLocalProvider(LocalRetroPixel provides pixel) { content() }
    }
}

/** Legacy pixels as layout units on this page. */
@Composable
fun retroDp(legacy: Int): Dp = with(LocalDensity.current) { (legacy * LocalRetroPixel.current).toDp() }

/** The panel tile through the game palette, and brighter by `BAR_ADD` (legacy `m13_add_bar()`). */
private object PanelTiles {
    private fun tile(add: Int): ImageBitmap? {
        val panel = IndexedBitmap.panel ?: return null
        val argb = IntArray(panel.width * panel.height) { i ->
            0xFF shl 24 or LegacyFonts.gamePalette[(panel[i % panel.width, i / panel.width] + add) and 0xFF]
        }
        return imageFromArgb(panel.width, panel.height, argb)
    }

    val plain: ImageBitmap? by lazy { tile(0) }
    val bright: ImageBitmap? by lazy { tile(LegacyColors.BAR_ADD) }
}

/**
 * Legacy `write_panel()`: [tile] across the area from its top-left corner, clipped to it. The
 * pattern is drawn [PANEL_SCALE] times as coarse as the text, as large as on the 320×200 screens.
 */
private fun DrawScope.tiles(tile: ImageBitmap?, pixel: Int, fallback: Color) {
    if (tile == null) {
        drawRect(fallback)
        return
    }
    val width = tile.width * pixel * PANEL_SCALE
    val height = tile.height * pixel * PANEL_SCALE
    clipRect {
        var y = 0
        while (y < size.height) {
            var x = 0
            while (x < size.width) {
                drawImage(tile, dstOffset = IntOffset(x, y), dstSize = IntSize(width, height), filterQuality = FilterQuality.None)
                x += width
            }
            y += height
        }
    }
}

private const val PANEL_SCALE = 2

/** The panel pattern behind the content. */
@Composable
fun Modifier.retroPanel(): Modifier {
    val pixel = LocalRetroPixel.current
    return drawBehind { tiles(PanelTiles.plain, pixel, RetroColors.palette(3)) }
}

/**
 * The frame of legacy `menu()` and the prompts: a band of the brighter panel (`m13_add_bar()`),
 * [band] legacy pixels wide, around a black box.
 */
@Composable
fun Modifier.retroFrame(band: Int): Modifier {
    val pixel = LocalRetroPixel.current
    return drawBehind {
        tiles(PanelTiles.bright, pixel, RetroColors.palette(8))
        val inset = (band * pixel).toFloat()
        drawRect(RetroColors.black, topLeft = Offset(inset, inset), size = Size(size.width - 2 * inset, size.height - 2 * inset))
    }
}

/** A [retroFrame]d box with [padding] legacy pixels inside the black. */
@Composable
fun RetroBox(modifier: Modifier = Modifier, band: Int = 5, padding: Int = 8, content: @Composable BoxScope.() -> Unit) {
    Box(modifier = modifier.retroFrame(band).padding(retroDp(band + padding)), content = content)
}

/**
 * A line to type in (legacy `read_string()`): the text in the big font on black, with the block
 * cursor while focused. The field underneath is a real text field, so the soft keyboard and every
 * input method work. Enter (or the keyboard's Done) calls [onDone].
 */
@Composable
fun RetroTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        RetroText(label, color = RetroColors.dim, font = RetroFont.SMALL)
        RetroBox(modifier = Modifier.fillMaxWidth(), band = 2, padding = 4) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                // The real text is drawn invisibly; the retro one is drawn over it.
                textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
                cursorBrush = SolidColor(Color.Transparent),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                modifier = Modifier
                    .onFocusChanged { focused = it.isFocused }
                    .onPreviewKeyEvent { event ->
                        val enter = event.key == Key.Enter || event.key == Key.NumPadEnter
                        if (enter && event.type == KeyEventType.KeyDown) onDone()
                        enter
                    },
                decorationBox = { inner ->
                    Box {
                        inner()
                        Row {
                            RetroText(value, color = RetroColors.high, maxLines = 1)
                            if (focused && enabled) CursorBlock()
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun CursorBlock() {
    Canvas(modifier = Modifier.size(retroDp(LegacyFonts.BIG_WIDTH), retroDp(LegacyFonts.BIG_HEIGHT))) {
        drawRect(RetroColors.cursor)
    }
}

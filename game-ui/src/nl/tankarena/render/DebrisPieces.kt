package nl.tankarena.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import nl.tankarena.protocol.snapshot.DebrisKind
import nl.tankarena.render.actor.SpriteActor
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Legacy `pp[]` (src/world/parts.c) for the parts that only show: how many pieces, how far (min +
 * rnd(spread) px) and how fast (min + rnd(spread) px/s) they fly.
 */
internal enum class DebrisProperties(
    val pieces: Int,
    val minDistance: Int,
    val distanceSpread: Int,
    val minSpeed: Int,
    val speedSpread: Int,
) {
    PIXEL(10, 10, 40, 30, 60),
    MORTAR(20, 40, 30, 50, 60),
    PARTS(10, 50, 20, 30, 20),
    ;

    companion object {
        fun of(kind: DebrisKind) = when (kind) {
            DebrisKind.PIXEL -> PIXEL
            DebrisKind.MORTAR -> MORTAR
            DebrisKind.PARTS -> PARTS
        }
    }
}

/** One flying piece: a black pixel, or for PARTS a piece of wall (PARTA..F, 5 pictures each). */
internal class DebrisPiece(
    val properties: DebrisProperties,
    var x: Float,
    var y: Float,
    private val angle: Double,
    private val speed: Float,
    var distanceLeft: Float,
    /** PARTS: which of PARTA..PARTF. */
    val partLetter: Char,
    /** PARTS: the tick of its next puff of smoke. */
    var smokeTick: Long,
) {
    val maxDistance: Float get() = (properties.minDistance + properties.distanceSpread).toFloat()

    /** Legacy write_parts(): `pic + 4 - d * 5 / (min_d + rand_d)`, later pictures as it slows down. */
    val frame: Int get() = (4 - (distanceLeft * 5 / maxDistance).toInt()).coerceIn(0, 4)

    /** Moves one tick; false once it has flown its distance. */
    fun advance(): Boolean {
        val d = speed / 100f
        x += (cos(angle) * d).toFloat()
        y -= (sin(angle) * d).toFloat()
        distanceLeft -= d
        return distanceLeft >= 0f
    }

    companion object {
        /** Legacy create_parts(): pieces from within 5 px of ([x], [y]), each its own way. */
        fun throwFrom(kind: DebrisKind, x: Int, y: Int, tick: Long, random: Random): List<DebrisPiece> {
            val p = DebrisProperties.of(kind)
            return List(p.pieces) {
                DebrisPiece(
                    properties = p,
                    x = (x - 5 + random.nextInt(10)).toFloat(),
                    y = (y - 5 + random.nextInt(10)).toFloat(),
                    angle = 2 * Math.PI * random.nextInt(360) / 360,
                    speed = (p.minSpeed + random.nextInt(p.speedSpread)).toFloat(),
                    distanceLeft = (p.minDistance + random.nextInt(p.distanceSpread)).toFloat(),
                    partLetter = 'A' + random.nextInt(6),
                    smokeTick = tick + 30 + random.nextInt(30),
                )
            }
        }
    }
}

/** Legacy write_parts() for PT_PIXEL and PT_MORTAR: one pixel in palette colour 0, above everything. */
internal class PixelDrawable(position: Offset) : SceneDrawable {
    override val body: RenderBody = RenderBody(position, Size(1f, 1f))
    override val layerIndex: Int = 4

    override fun DrawScope.draw() {
        drawRect(Color.Black, Offset.Zero, Size(1f, 1f))
    }
}

/** What draws a piece: a pixel, or a sprite for a piece of wall. */
internal class DebrisVisual(val piece: DebrisPiece, val pixel: PixelDrawable?, val sprite: SpriteActor?)

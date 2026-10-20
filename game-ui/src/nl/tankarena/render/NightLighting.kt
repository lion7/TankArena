package nl.tankarena.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RenderEffect
import nl.tankarena.content.LEGACY_TILE_SIZE
import nl.tankarena.content.MapSceneSidecar
import nl.tankarena.content.PictureCatalog
import nl.tankarena.protocol.snapshot.B52State
import nl.tankarena.protocol.snapshot.LightCone
import nl.tankarena.protocol.snapshot.TankState
import nl.tankarena.protocol.snapshot.WorldSnapshot
import nl.tankarena.ui.resources.Res
import nl.tankarena.ui.resources.allDrawableResources
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.getDrawableResourceBytes
import org.jetbrains.compose.resources.getSystemResourceEnvironment
import kotlinx.coroutines.runBlocking

/**
 * The night missions as the original draws them (src/game/night.c, under.c): the whole view is
 * remapped through NIGHT.LNK into the dark half of NIGHT.PAL, except where a light falls — the
 * headlight cones of the vehicles (light_tank()), the glow of standing street lamps (top-layer
 * LIGHT_SRC pictures) and the blinking lights of the B52s — which use the lit half. Colours that
 * link straight into the lit half (fire, flames) glow on their own.
 *
 * The lights are drawn into a mask the size of the visible scene each frame; a runtime shader
 * looks every pixel up in `night_lut.png` (see NightAssetsExtractor), dark or lit by the mask.
 */
@OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
internal class NightLighting(
    private val sprites: LegacySpriteCatalog,
) {
    private val lutResource: DrawableResource? = Res.allDrawableResources["night_lut"]
    private val coneResources: List<DrawableResource?> = CONE_NAMES.map { Res.allDrawableResources["light_${it.lowercase()}"] }
    private val shader = LayerShader(SKSL)
    private var mask: ImageBitmap? = null
    private var masked = false
    private var lutLoaded = false

    /** Draw this frame's lights into the mask (scene pixels of the visible area). */
    fun update(world: WorldSnapshot, snapshot: RuntimeSnapshot, playfieldWidth: Int, playfieldHeight: Int, extra: MutableSet<DrawableResource>) {
        val bitmap = mask?.takeIf { it.width == playfieldWidth && it.height == playfieldHeight }
            ?: ImageBitmap(playfieldWidth, playfieldHeight).also { mask = it }
        val canvas = Canvas(bitmap)
        canvas.drawRect(0f, 0f, playfieldWidth.toFloat(), playfieldHeight.toFloat(), Paint().apply { blendMode = BlendMode.Clear })
        val geometry = snapshot.geometry
        // World point → mask point.
        val shiftX = snapshot.renderOffsetX - (geometry.cameraCenterX - playfieldWidth / 2f)
        val shiftY = snapshot.renderOffsetY - (geometry.cameraCenterY - playfieldHeight / 2f)
        snapshot.sidecar?.let { drawLamps(canvas, it, snapshot, shiftX, shiftY, playfieldWidth, playfieldHeight, extra) }
        for (state in world.actors) {
            when (state) {
                is TankState -> state.lights.forEach { drawCone(canvas, it, shiftX, shiftY) }
                is B52State -> if (state.alive && state.lightOn && !state.wreck) drawB52Lights(canvas, state, shiftX, shiftY)
                else -> Unit
            }
        }
        shader.image("lights", bitmap, decal = true)
        masked = true
    }

    /**
     * The render effect for a layer of [width]×[height] pixels showing the [playfieldWidth]×
     * [playfieldHeight] scene, or null while the colour table is still loading.
     */
    fun filter(width: Float, height: Float, playfieldWidth: Int, playfieldHeight: Int): RenderEffect? {
        if (!lutLoaded) {
            val lut = lutResource?.let(::image) ?: return null
            shader.image("lut", lut)
            lutLoaded = true
        }
        if (!masked || width <= 0f || height <= 0f) return null
        shader.uniform("maskScale", playfieldWidth / width, playfieldHeight / height)
        return shader.effect()
    }

    /** Legacy top_layer(): a standing lamp (LIGHT_SRC) lights the mask with its third picture. */
    private fun drawLamps(
        canvas: Canvas,
        sidecar: MapSceneSidecar,
        snapshot: RuntimeSnapshot,
        shiftX: Float,
        shiftY: Float,
        playfieldWidth: Int,
        playfieldHeight: Int,
        extra: MutableSet<DrawableResource>,
    ) {
        val tiles = sidecar.tileLayers ?: return
        val world = sidecar.metadata.world
        val width = sidecar.metadata.widthTiles
        val height = sidecar.metadata.heightTiles
        val firstX = ((-shiftX) / LEGACY_TILE_SIZE).toInt().coerceAtLeast(0)
        val firstY = ((-shiftY) / LEGACY_TILE_SIZE).toInt().coerceAtLeast(0)
        val lastX = ((playfieldWidth - shiftX) / LEGACY_TILE_SIZE).toInt().coerceAtMost(width - 1)
        val lastY = ((playfieldHeight - shiftY) / LEGACY_TILE_SIZE).toInt().coerceAtMost(height - 1)
        for (y in firstY..lastY) for (x in firstX..lastX) {
            val index = x + y * width
            val changed = snapshot.changedTiles[index]
            val top = changed?.top ?: tiles.top[index]
            if (top < 0 || PictureCatalog.legacyRow(world, top)?.type != "LIGHT_SRC") continue
            if (!(changed?.solidStanding ?: initiallyStanding(world, tiles.solid[index], top))) continue
            if (!(changed?.topIntact ?: true)) continue
            val glow = PictureCatalog.pictureNames(world, top)?.getOrNull(2) ?: continue
            val resource = sprites.findResource(glow) ?: continue
            extra += resource
            val image = image(resource) ?: continue
            canvas.drawImage(image, Offset(x * LEGACY_TILE_SIZE + shiftX, y * LEGACY_TILE_SIZE + shiftY), LIGHT_PAINT)
        }
    }

    private val images = HashMap<DrawableResource, ImageBitmap?>()

    /**
     * The colour table and light pictures, read straight from the resources once. Not through
     * the sprite manager: the first sync runs before the viewport is shown, and a picture asked
     * for then fails to load and is never loaded again.
     */
    private fun image(resource: DrawableResource): ImageBitmap? = images.getOrPut(resource) {
        runCatching {
            val bytes = runBlocking { getDrawableResourceBytes(getSystemResourceEnvironment(), resource) }
            decodeImage(bytes)
        }.getOrNull()
    }

    /** Legacy write_bundle(): one of five cone pictures per size, mirrored for the other directions. */
    private fun drawCone(canvas: Canvas, cone: LightCone, shiftX: Float, shiftY: Float) {
        val d = cone.direction.mod(16)
        val pattern = when {
            d <= 4 -> d
            d <= 8 -> 8 - d
            d <= 11 -> d - 8
            else -> 16 - d
        }
        val index = cone.size.coerceIn(0, 2) * 5 + pattern
        val image = coneResources[index]?.let(::image) ?: return
        val (sx, sy, offx, offy) = CONES[index]
        val x = cone.x + shiftX
        val y = cone.y + shiftY
        val flipH = d >= 9
        val flipV = d in 5..11
        val left = if (flipH) x + offx - sx else x - offx
        val top = when {
            d <= 4 || d >= 12 -> y - offy
            else -> y + offy - (sy - 1)
        }
        canvas.save()
        canvas.translate(left + if (flipH) sx else 0, top + if (flipV) sy else 0)
        canvas.scale(if (flipH) -1f else 1f, if (flipV) -1f else 1f)
        canvas.drawImage(image, Offset.Zero, LIGHT_PAINT)
        canvas.restore()
    }

    /** Legacy write_b52(): with its lights on, two lamps `B52L` at the wing tips. */
    private fun drawB52Lights(canvas: Canvas, state: B52State, shiftX: Float, shiftY: Float) {
        val image = sprites.findResource(B52_LIGHT)?.let(::image) ?: return
        val d = state.heading.mod(4)
        for (wing in B52_WINGS) {
            val (lx, ly) = wing[d]
            canvas.drawImage(image, Offset(state.x - 48 + lx + shiftX, state.y - 48 + ly + shiftY), LIGHT_PAINT)
        }
    }

    companion object {
        /** The night shader for a light mask and colour table; the mask is [scaleX]×[scaleY] of the layer. */
        internal fun nightShader(lights: ImageBitmap, lut: ImageBitmap, scaleX: Float, scaleY: Float): LayerShader =
            LayerShader(SKSL).apply {
                uniform("maskScale", scaleX, scaleY)
                image("lights", lights, decal = true)
                image("lut", lut)
            }

        private val CONE_NAMES = listOf("LLIGHT", "MLIGHT", "SLIGHT").flatMap { size -> (1..5).map { "$size$it" } }

        /** Legacy `b[]` in src/game/night.c: size and light origin of each cone picture. */
        private val CONES: List<List<Int>> = listOf(
            listOf(75, 150, 37, 149), listOf(100, 150, 0, 149), listOf(150, 150, 0, 149), listOf(150, 100, 0, 99), listOf(150, 75, 0, 37),
            listOf(29, 76, 14, 75), listOf(39, 71, 0, 70), listOf(58, 58, 0, 57), listOf(71, 39, 0, 38), listOf(76, 29, 0, 14),
            listOf(13, 61, 6, 60), listOf(28, 56, 0, 55), listOf(46, 46, 0, 45), listOf(56, 28, 0, 27), listOf(61, 13, 0, 6),
        )

        private const val B52_LIGHT = "B52L"

        /** Legacy `li[2][4][2]` in src/objects/b52.c, per heading. */
        private val B52_WINGS: List<List<Pair<Int, Int>>> = listOf(
            listOf(1 to 49, 16 to 1, 1 to 16, 49 to 1),
            listOf(65 to 49, 16 to 65, 65 to 16, 49 to 65),
        )

        private val LIGHT_PAINT = Paint()

        /**
         * Quantise each pixel to its 6-bit palette colour and look it up in the 1024×512 table:
         * blue picks one of 64 slices of 64×64 (8 per row), the left half holds the dark night
         * colours and the right half the lit ones.
         */
        internal const val SKSL = """
            uniform shader content;
            uniform shader lights;
            uniform shader lut;
            uniform float2 maskScale;

            half4 main(float2 p) {
                half4 c = content.eval(p);
                if (c.a < 0.5) return c;
                float3 v = floor(floor(clamp(float3(c.rgb / c.a), 0.0, 1.0) * 255.0 + 0.5) / 4.0);
                float lit = lights.eval(p * maskScale).a;
                float sliceX = mod(v.b, 8.0);
                float sliceY = floor(v.b / 8.0);
                float2 cell = float2(sliceX * 64.0 + v.r + (lit > 0.5 ? 512.0 : 0.0), sliceY * 64.0 + v.g) + 0.5;
                half3 night = lut.eval(cell).rgb;
                return half4(night * c.a, c.a);
            }
        """
    }
}

package nl.tankarena.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.getDrawableResourceBytes
import org.jetbrains.compose.resources.getSystemResourceEnvironment

/**
 * Where something is drawn, in scene pixels: a box of [size] placed so that its [pivot] (by default
 * its centre) sits at [position].
 */
internal class RenderBody(
    var position: Offset,
    val size: Size,
    val pivot: Offset = Offset(size.width / 2f, size.height / 2f),
)

/** Something a [RenderScene] draws. */
internal interface SceneDrawable {
    val body: RenderBody

    /** Lower layers are drawn first. */
    val layerIndex: Int

    /** Within a layer, higher values are drawn first (further back); equal values keep the order they were added in. */
    val drawingOrder: Float get() = 0f

    /** Drawing is cut off at the body's box. */
    val shouldClip: Boolean get() = true

    /** Draws in the body's own coordinates: (0, 0) is the box's top-left corner. */
    fun DrawScope.draw()
}

/**
 * The things one player window shows, in the order they were added, drawn through a camera: the
 * camera centre is the middle of the window and scene pixels are scaled to fill its width.
 */
internal class RenderScene {
    private val drawables = ArrayList<SceneDrawable>()

    fun add(drawable: SceneDrawable) {
        if (drawable !in drawables) drawables += drawable
    }

    fun remove(drawable: SceneDrawable) {
        drawables -= drawable
    }

    fun remove(removed: Collection<SceneDrawable>) {
        if (removed.isEmpty()) return
        val doomed = removed.toHashSet()
        drawables.removeAll { it in doomed }
    }

    private val visible = ArrayList<SceneDrawable>()

    /** Draws everything inside the camera's view onto this canvas, which shows [sceneWidth] scene pixels across. */
    fun draw(scope: DrawScope, cameraX: Float, cameraY: Float, sceneWidth: Float) = with(scope) {
        if (size.width <= 0f || size.height <= 0f) return@with
        val scale = size.width / sceneWidth
        val halfWidth = size.width / (scale * 2f)
        val halfHeight = size.height / (scale * 2f)
        val left = cameraX - halfWidth
        val top = cameraY - halfHeight
        val right = cameraX + halfWidth
        val bottom = cameraY + halfHeight
        // One list for every frame, sorted in place (stable, as before): drawing makes no garbage.
        val ordered = visible
        ordered.clear()
        for (drawable in drawables) {
            val box = drawable.body
            val boxLeft = box.position.x - box.pivot.x
            val boxTop = box.position.y - box.pivot.y
            if (boxLeft <= right && boxTop <= bottom && boxLeft + box.size.width >= left && boxTop + box.size.height >= top) {
                ordered += drawable
            }
        }
        ordered.sortWith(DRAWING_ORDER)
        withTransform({
            translate(left = size.width / 2f - cameraX, top = size.height / 2f - cameraY)
            scale(scaleX = scale, scaleY = scale, pivot = Offset(cameraX, cameraY))
        }) {
            val canvas = drawContext.canvas
            val transform = drawContext.transform
            for (drawable in ordered) {
                val box = drawable.body
                canvas.save()
                transform.translate(left = box.position.x - box.pivot.x, top = box.position.y - box.pivot.y)
                if (drawable.shouldClip) transform.clipRect(0f, 0f, box.size.width, box.size.height)
                with(drawable) { draw() }
                canvas.restore()
            }
        }
    }
}

/**
 * The extracted legacy pictures, decoded once on first use and shared by every window. Loading is
 * synchronous, so a picture is there the first time it is drawn.
 */
class PictureCache private constructor() {
    private val images = HashMap<DrawableResource, ImageBitmap?>()

    @OptIn(ExperimentalResourceApi::class, InternalResourceApi::class)
    fun get(resource: DrawableResource): ImageBitmap? = synchronized(images) {
        images.getOrPut(resource) {
            runCatching {
                val bytes = runBlocking { getDrawableResourceBytes(getSystemResourceEnvironment(), resource) }
                decodeImage(bytes)
            }.getOrNull()
        }
    }

    fun preload(resources: Collection<DrawableResource>) {
        for (resource in resources) get(resource)
    }

    companion object {
        val shared: PictureCache by lazy { PictureCache() }
    }
}

/** By layer, then highest [SceneDrawable.drawingOrder] first (`+ 0f`: -0 and 0 are the same height). */
private val DRAWING_ORDER = Comparator<SceneDrawable> { a, b ->
    if (a.layerIndex != b.layerIndex) a.layerIndex.compareTo(b.layerIndex) else (b.drawingOrder + 0f).compareTo(a.drawingOrder + 0f)
}

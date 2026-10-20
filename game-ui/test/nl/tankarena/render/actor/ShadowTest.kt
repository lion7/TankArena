package nl.tankarena.render.actor

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Legacy b_add_shadow(): under the picture the screen turns to the grey of its brightness, at ~57 %. */
class ShadowTest {

    @Test
    fun `a shadow greys and darkens what lies under the picture and nothing beside it`() {
        val background = Color(0.2f, 0.6f, 0.9f)
        val target = ImageBitmap(4, 1)
        val picture = ImageBitmap(2, 1)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(picture), Size(2f, 1f)) { drawRect(Color.Red) }
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(target), Size(4f, 1f)) {
            drawRect(background)
            drawLegacyShadow(picture, 0, 0)
        }
        val pixels = target.toPixelMap()
        val shadowed = pixels[0, 0]
        assertEquals(background, pixels[3, 0], "outside the picture nothing changes")
        assertTrue(kotlin.math.abs(shadowed.red - shadowed.green) < 0.02f && kotlin.math.abs(shadowed.green - shadowed.blue) < 0.02f, "grey: $shadowed")
        val luma = 0.3f * background.red + 0.59f * background.green + 0.11f * background.blue
        assertTrue(shadowed.red in luma * 0.45f..luma * 0.7f, "about 57 % of the luma ${luma}: $shadowed")
    }
}

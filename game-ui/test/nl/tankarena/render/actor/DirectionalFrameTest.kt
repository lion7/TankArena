package nl.tankarena.render.actor

import kotlin.test.Test
import kotlin.test.assertEquals

/** Legacy b_add_layer_d() (src/world/under.c): five pictures, mirrored for the other eleven directions. */
class DirectionalFrameTest {

    @Test
    fun `north to east use the pictures as they are`() {
        for (direction in 0..4) assertEquals(DirectionalFrame(direction, false, false), directionalFrame(direction))
    }

    @Test
    fun `the other directions mirror them`() {
        // South-east is north-east upside down, south-west both ways, north-west left-right.
        assertEquals(DirectionalFrame(2, flipHorizontal = false, flipVertical = true), directionalFrame(6))
        assertEquals(DirectionalFrame(0, flipHorizontal = false, flipVertical = true), directionalFrame(8))
        assertEquals(DirectionalFrame(2, flipHorizontal = true, flipVertical = true), directionalFrame(10))
        assertEquals(DirectionalFrame(4, flipHorizontal = true, flipVertical = true), directionalFrame(12))
        assertEquals(DirectionalFrame(2, flipHorizontal = true, flipVertical = false), directionalFrame(14))
        assertEquals(directionalFrame(1), directionalFrame(17))
    }
}

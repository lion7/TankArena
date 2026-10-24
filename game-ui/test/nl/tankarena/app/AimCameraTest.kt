package nl.tankarena.app

import androidx.compose.ui.geometry.Offset
import nl.tankarena.protocol.snapshot.HudState
import nl.tankarena.protocol.snapshot.LockMarker
import nl.tankarena.protocol.snapshot.LockMarkerState
import nl.tankarena.protocol.snapshot.PlayerView
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AimCameraTest {
    /** A 640×400 view on a big map, the vehicle at (2000, 2000). */
    private fun view(marker: LockMarker?) = PlayerView(
        playerId = 0,
        controlledActorId = 1,
        cameraCenterX = marker?.x ?: 2000,
        cameraCenterY = marker?.y ?: 2000,
        hud = HudState(),
        viewerX = 2000,
        viewerY = 2000,
        lockMarker = marker,
    )

    private fun aiming(x: Int, y: Int) = view(LockMarker(x, y, LockMarkerState.SEARCHING))

    private fun AimCamera.at(view: PlayerView, cursor: Offset?, frame: Int) =
        follow(view, cursor, 320f, 200f, 4000, 4000, frame * 16_000_000L)

    @Test
    fun `the view holds still while the cursor is away from the edge`() {
        val camera = AimCamera()
        assertNull(camera.at(view(null), null, 0))
        repeat(60) { frame ->
            assertEquals(Offset(2000f, 2000f), camera.at(aiming(2100 + frame, 2050), Offset(100f + frame, 50f), frame + 1))
        }
    }

    @Test
    fun `the cursor at the edge scrolls the view that way`() {
        val camera = AimCamera()
        camera.at(aiming(2000, 2000), Offset.Zero, 0)
        var at = Offset.Zero
        for (frame in 1..10) at = camera.at(aiming(2310, 2000), Offset(310f, 0f), frame)!!
        assertTrue(at.x > 2000f, "scrolled right: $at")
        assertEquals(2000f, at.y)
    }

    @Test
    fun `the view goes no further from the vehicle than the marker can`() {
        val camera = AimCamera()
        var at = Offset.Zero
        for (frame in 0..1000) at = camera.at(aiming(2000, 1500), Offset(0f, -199f), frame)!!
        assertTrue(hypot(at.x - 2000f, at.y - 2000f) <= 450.5f, "within range: $at")
    }

    @Test
    fun `a marker steered out of the view brings the view along`() {
        val camera = AimCamera()
        camera.at(aiming(2000, 2000), null, 0)
        val at = camera.at(aiming(2400, 2000), null, 1)!!
        assertEquals(2400f - 320f + 16f, at.x)
    }

    @Test
    fun `after the launch the view stays, then the sim's camera takes over`() {
        val camera = AimCamera()
        camera.at(aiming(2000, 2000), null, 0)
        val aimed = camera.at(aiming(2400, 2000), null, 1)
        assertEquals(aimed, camera.at(view(LockMarker(2400, 2000, LockMarkerState.LAUNCHED)), Offset(319f, 0f), 2))
        assertNull(camera.at(view(null), null, 3))
    }
}

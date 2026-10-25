package nl.tankarena.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import nl.tankarena.protocol.snapshot.GameEvent
import nl.tankarena.protocol.snapshot.HudState
import nl.tankarena.protocol.snapshot.PlayerView
import nl.tankarena.protocol.snapshot.ServerFrame
import nl.tankarena.protocol.snapshot.TankState
import nl.tankarena.protocol.snapshot.WorldSnapshot
import nl.tankarena.ui.compose.menu.TouchSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Extended's touch controller: where it goes, how its sticks read, what its weapon buttons show. */
class TouchControlsTest {
    // A Pixel 7 held sideways, the 4:3 picture in the middle with a strip on each side.
    private val width = 2400f
    private val height = 1080f
    private val density = 2.75f
    private val picture = Rect(480f, 0f, 1920f, 1080f)

    private fun TouchLayout.spot(role: TouchRole) = spots.first { it.role == role }

    @Test
    fun `beside the picture the move stick and the aim cluster sit in the strips`() {
        val layout = touchLayout(width, height, density, picture, TouchSettings(), nextVehicle = true)
        assertEquals(240f, layout.moveHome.x, "in the middle of the left strip")
        assertTrue(layout.aimHome.x > picture.right, "the aim stick in the right strip")
        assertEquals(60 * density, layout.stick, "60 dp")
        for (spot in layout.spots) {
            assertTrue(spot.radius * 2 >= 48 * density - 1, "${spot.role} is at least 48 dp across")
            assertTrue(spot.centre.x - spot.radius >= 0 && spot.centre.x + spot.radius <= width, "${spot.role} on the screen")
            assertTrue(spot.centre.y - spot.radius >= 0 && spot.centre.y + spot.radius <= height, "${spot.role} on the screen")
        }
        for (a in layout.spots) for (b in layout.spots) {
            if (a !== b) assertTrue((a.centre - b.centre).getDistance() >= a.radius + b.radius, "${a.role} and ${b.role} apart")
        }
        assertTrue(layout.spot(TouchRole.PAUSE).centre.x > picture.right, "PAUSE beside the picture, not over its status")
    }

    @Test
    fun `without room beside the picture the controls go to the corners`() {
        val layout = touchLayout(1440f, 1080f, density, Rect(0f, 0f, 1440f, 1080f), TouchSettings(), nextVehicle = false)
        assertEquals(12 * density + layout.stick, layout.moveHome.x)
        assertTrue(layout.spots.all { it.centre.x + it.radius <= 1440f })
        assertTrue(layout.spots.none { it.role == TouchRole.NEXT }, "no NEXT without another vehicle")
    }

    @Test
    fun `left-handed mirrors it and the size setting scales it, within the screen's height`() {
        val left = touchLayout(width, height, density, picture, TouchSettings(leftHanded = true), nextVehicle = false)
        assertEquals(width - 240f, left.moveHome.x)
        assertTrue(left.aimHome.x < picture.left)
        assertTrue(left.onMoveSide(Offset(2000f, 500f)) && !left.onMoveSide(Offset(100f, 500f)))
        val big = touchLayout(width, height, density, picture, TouchSettings(size = 150), nextVehicle = false)
        assertEquals(height * 0.17f, big.stick, "capped by the height")
    }

    @Test
    fun `a stick does nothing inside its dead zone and drives at full strength past it`() {
        val centre = Offset(100f, 100f)
        assertNull(stickPush(centre, centre + Offset(10f, 0f), 100f))
        assertEquals(StickPush(100, 0, 0.2f), stickPush(centre, centre + Offset(20f, 0f), 100f))
        assertEquals(1f, stickPush(centre, centre + Offset(0f, -250f), 100f)!!.reach)
        assertTrue(stickPush(centre, centre + Offset(0f, -80f), 100f)!!.reach >= AIM_FIRES_AT, "pushed far: the aim stick fires")
    }

    @Test
    fun `the weapon buttons show the weapon they switch to`() {
        // Machine gun (0) and rockets (4) with ammo, the flamethrower (2) built in.
        val hud = HudState(currentWeapon = 0, ammo = listOf(50, 0, 0, 0, 3), hasFlamer = true)
        assertEquals(2, neighbourWeapon(hud, night = false, step = 1))
        assertEquals(4, neighbourWeapon(hud, night = false, step = -1))
        assertEquals(12, neighbourWeapon(hud, night = true, step = -1), "the light switch on a night map")
        assertNull(neighbourWeapon(HudState(currentWeapon = 0, ammo = listOf(50)), night = false, step = 1), "nothing else to switch to")
    }

    @Test
    fun `player one feels a hit, but not twice in a moment`() {
        val buzzes = mutableListOf<Int>()
        val haptics = Haptics { millis, _ -> buzzes += millis }.apply { enabled = true }
        fun frame(vararg events: GameEvent) = ServerFrame(
            tick = 1,
            world = WorldSnapshot(tick = 1, actors = listOf(TankState(actorId = 7, x = 0, y = 0)), events = events.toList()),
            playerViews = listOf(PlayerView(playerId = 0, controlledActorId = 7, cameraCenterX = 0, cameraCenterY = 0, hud = HudState())),
        )
        haptics.step(frame(GameEvent.DamageTaken(7, 5)), 0)
        haptics.step(frame(GameEvent.DamageTaken(7, 5)), 0)
        haptics.step(frame(GameEvent.DamageTaken(8, 5)), 0)
        assertEquals(1, buzzes.size)
        haptics.step(frame(GameEvent.TankDestroyed(7)), 0)
        assertEquals(2, buzzes.size, "losing the vehicle always buzzes")
    }
}

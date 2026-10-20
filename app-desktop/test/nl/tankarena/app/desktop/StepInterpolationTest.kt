package nl.tankarena.app.desktop

import nl.tankarena.protocol.Team
import nl.tankarena.protocol.snapshot.HudState
import nl.tankarena.protocol.snapshot.PlayerView
import nl.tankarena.protocol.snapshot.ServerFrame
import nl.tankarena.protocol.snapshot.TankState
import nl.tankarena.protocol.snapshot.WorldSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

class StepInterpolationTest {
    private fun frame(tick: Long, x: Int, cameraX: Int = x) = ServerFrame(
        tick = tick,
        world = WorldSnapshot(tick = tick, actors = listOf(TankState(actorId = 1, team = Team.PLAYER, tankType = 0, x = x, y = 100))),
        playerViews = listOf(PlayerView(playerId = 0, controlledActorId = 1, cameraCenterX = cameraX, cameraCenterY = 100, hud = HudState())),
    )

    @Test
    fun `between two steps things are shown part of the way`() {
        val mid = interpolate(frame(1, 100), frame(4, 120), 0.5f)
        assertEquals(110, (mid.world.actors.single() as TankState).x)
        assertEquals(110, mid.playerViews.single().cameraCenterX)
    }

    @Test
    fun `a jump is shown where it lands`() {
        val wrapped = interpolate(frame(1, 10), frame(4, 3000), 0.5f)
        assertEquals(3000, (wrapped.world.actors.single() as TankState).x)
    }

    @Test
    fun `the interpolator starts a new step when the world moves on`() {
        val interpolator = StepInterpolator()
        interpolator.frame(frame(1, 100), now = 0)
        interpolator.frame(frame(2, 100), now = 10_000_000)
        val next = interpolator.frame(frame(4, 130), now = 30_000_000)
        assertEquals(100, (next.world.actors.single() as TankState).x, "just arrived: still where it was")
        val later = interpolator.frame(frame(5, 130), now = 30_000_000 + STEP_NANOS / 3)
        assertEquals(110, (later.world.actors.single() as TankState).x)
    }
}

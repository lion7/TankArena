package com.tankarena.sim.kubriko

import com.pandulapeter.kubriko.Kubriko
import com.pandulapeter.kubriko.actor.Actor
import com.pandulapeter.kubriko.helpers.ManualTickSource
import com.pandulapeter.kubriko.helpers.TickSource
import com.pandulapeter.kubriko.manager.ActorManager
import com.pandulapeter.kubriko.manager.Manager
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerrainSlideManagerTest {

    @Test
    fun `onUpdate invokes applyPendingResolutions on each Resolvable actor with configured bounds`() {
        val resolvable = RecordingResolvable()
        val actorManager = ActorManager.newInstance(
            initialActors = listOf(resolvable),
            shouldPutFarAwayActorsToSleep = false,
        )
        val slideManager = TerrainSlideManager(
            worldWidthPixels = 640,
            worldHeightPixels = 480,
        )
        val tickSource = TickSource.manual() as ManualTickSource
        val kubriko = Kubriko.newInstance(
            actorManager,
            slideManager,
            tickSource = tickSource,
        )
        kubriko.initialize()
        awaitActor(actorManager, resolvable)

        tickSource.tick(33)
        tickSource.tick(16)

        assertEquals(2, resolvable.callCount, "TerrainSlideManager.onUpdate should commit resolutions once per tick")
        assertTrue(resolvable.calls.all { it == 640 to 480 }, "resolutions must receive configured world bounds")

        kubriko.dispose()
    }

    @Test
    fun `non-Resolvable actors are ignored`() {
        val nonResolvable = PlainActor()
        val resolvable = RecordingResolvable()
        val actorManager = ActorManager.newInstance(
            initialActors = listOf(nonResolvable, resolvable),
            shouldPutFarAwayActorsToSleep = false,
        )
        val slideManager = TerrainSlideManager(worldWidthPixels = 320, worldHeightPixels = 240)
        val tickSource = TickSource.manual() as ManualTickSource
        val kubriko = Kubriko.newInstance(actorManager, slideManager, tickSource = tickSource)
        kubriko.initialize()
        awaitActor(actorManager, resolvable)
        awaitActor(actorManager, nonResolvable)

        tickSource.tick(33)

        assertEquals(1, resolvable.callCount)
        kubriko.dispose()
    }

    @Test
    fun `registration order after a preceding Manager is preserved`() {
        val calls = mutableListOf<String>()
        val earlier = RecordingManager("earlier", calls)
        val resolvable = object : Resolvable {
            override fun applyPendingResolutions(worldWidthPixels: Int, worldHeightPixels: Int) {
                calls += "slide"
            }
        }
        val actorManager = ActorManager.newInstance(
            initialActors = listOf(resolvable),
            shouldPutFarAwayActorsToSleep = false,
        )
        val slideManager = TerrainSlideManager(worldWidthPixels = 100, worldHeightPixels = 100)
        val tickSource = TickSource.manual() as ManualTickSource
        val kubriko = Kubriko.newInstance(
            actorManager,
            earlier,
            slideManager,
            tickSource = tickSource,
        )
        kubriko.initialize()
        awaitActor(actorManager, resolvable)

        tickSource.tick(33)

        assertEquals(listOf("earlier", "slide"), calls)
        kubriko.dispose()
    }

    private fun awaitActor(actorManager: ActorManager, actor: Actor) = runBlocking {
        withTimeout(2_000) {
            while (actor !in actorManager.allActors.value) {
                delay(5)
            }
        }
    }
}

private class RecordingResolvable : Resolvable {
    var callCount: Int = 0
        private set
    val calls: MutableList<Pair<Int, Int>> = mutableListOf()

    override fun applyPendingResolutions(worldWidthPixels: Int, worldHeightPixels: Int) {
        callCount += 1
        calls += worldWidthPixels to worldHeightPixels
    }
}

private class PlainActor : Actor

private class RecordingManager(
    private val label: String,
    private val sink: MutableList<String>,
) : Manager() {
    override fun onUpdate(deltaTimeInMilliseconds: Int) {
        sink += label
    }
}

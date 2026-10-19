package com.tankarena.sim

import com.tankarena.sim.map.ShippedMaps
import com.tankarena.protocol.InputFrame
import java.lang.management.ManagementFactory
import kotlin.test.Test

/**
 * Not a test: how long the simulation takes per tick on every shipped map. Runs only with
 * `TICK_BENCH=1 ./kotlin test -m game-server`; `TICK_BENCH_MAPS=begin1,suburbs` limits the maps.
 * The player drives and fires all the time so there is something going on.
 */
class TickBenchmarkTest {

    @Test
    fun benchmark() {
        System.getenv("TICK_BENCH") ?: return
        val only = System.getenv("TICK_BENCH_MAPS")?.split(',')?.map { it.trim().lowercase() }?.toSet()
        val ticks = System.getenv("TICK_BENCH_TICKS")?.toIntOrNull() ?: 3000
        val scenes = ShippedMaps.loadAll().filter { only == null || it.baseName.lowercase() in only }
        val gcBeans = ManagementFactory.getGarbageCollectorMXBeans()
        fun gcMillis() = gcBeans.sumOf { it.collectionTime.coerceAtLeast(0) }
        // Warm up the JIT on one map first.
        scenes.firstOrNull()?.let { run(it.document, 1500) }
        val rows = scenes.map { scene ->
            val gcBefore = gcMillis()
            val loadStart = System.nanoTime()
            val client = LocalMatchClient.fromMap(scene.document)
            val loadMillis = (System.nanoTime() - loadStart) / 1e6
            val times = LongArray(ticks)
            for (i in 0 until ticks) {
                client.submitInput(input(i))
                val start = System.nanoTime()
                client.tick()
                times[i] = System.nanoTime() - start
            }
            val actors = client.currentServerFrameOrNull()
            client.dispose()
            times.sort()
            Row(
                name = scene.baseName,
                loadMillis = loadMillis,
                meanMicros = times.average() / 1e3,
                p99Micros = times[(ticks * 99) / 100] / 1e3,
                maxMicros = times.last() / 1e3,
                over10ms = times.count { it > 10_000_000 },
                gcMillis = gcMillis() - gcBefore,
                actors = actors,
            )
        }
        println("TICKBENCH map            load ms  mean µs   p99 µs    max µs  >10ms  gc ms  actors")
        for (r in rows.sortedByDescending { it.meanMicros }) {
            println(
                "TICKBENCH %-14s %8.0f %8.0f %8.0f %9.0f %6d %6d %7d".format(
                    r.name, r.loadMillis, r.meanMicros, r.p99Micros, r.maxMicros, r.over10ms, r.gcMillis, r.actors,
                ),
            )
        }
    }

    private fun run(map: com.tankarena.sim.map.MapDocument, ticks: Int) {
        val client = LocalMatchClient.fromMap(map)
        repeat(ticks) {
            client.submitInput(input(it))
            client.tick()
        }
        client.dispose()
    }

    private fun input(tick: Int) = InputFrame(
        playerId = 0,
        inputSequence = tick.toLong(),
        forward = (tick / 200) % 2 == 0,
        reverse = false,
        turnLeft = (tick / 50) % 4 == 0,
        turnRight = false,
        aimLeft = false,
        aimRight = false,
        firePrimary = true,
        shield = false,
        cycleWeaponLeft = false,
        cycleWeaponRight = tick % 400 == 0,
        selectTankId = 0,
    )

    private fun LocalMatchClient.currentServerFrameOrNull(): Int =
        runCatching { currentServerFrame().world.actors.size }.getOrDefault(-1)

    private data class Row(
        val name: String,
        val loadMillis: Double,
        val meanMicros: Double,
        val p99Micros: Double,
        val maxMicros: Double,
        val over10ms: Int,
        val gcMillis: Long,
        val actors: Int,
    )
}

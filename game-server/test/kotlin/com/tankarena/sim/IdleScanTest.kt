package com.tankarena.sim

import com.tankarena.content.GameModeCompatibility
import com.tankarena.content.ShippedScenes
import kotlin.test.Test

/**
 * Not a test: how every single-player map ends with nobody at the controls. Runs only with
 * `IDLE_SCAN=<ticks> ./kotlin test -m game-server`; prints `IDLE` lines.
 */
class IdleScanTest {
    @Test
    fun scan() {
        val ticks = System.getenv("IDLE_SCAN")?.toIntOrNull() ?: return
        for (scene in ShippedScenes.loadAll()) {
            if (scene.sidecar.metadata.modeCompatibility == GameModeCompatibility.DUAL) continue
            val match = runCatching { Match.fromSceneJson(scene.sceneJson, scene.sidecar, MatchMode.SINGLE).also { it.initialize() } }
                .getOrElse { println("IDLE ${scene.baseName} failed: $it"); continue }
            var t = 0
            while (t < ticks && match.mission == Match.MissionStatus.IN_PROGRESS) { match.tick(); t++ }
            println("IDLE %-10s %-11s at %5d  good %3d bad %3d".format(scene.baseName, match.mission, t, match.goalProgressGood, match.goalProgressBad))
            match.dispose()
        }
    }
}

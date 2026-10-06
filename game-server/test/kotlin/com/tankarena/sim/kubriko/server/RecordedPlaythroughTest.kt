package com.tankarena.sim.kubriko.server

import com.tankarena.content.ShippedScenes
import com.tankarena.protocol.InputFrame
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Recorded playthroughs as regression tests. The desktop client saves every finished mission
 * under `~/.local/share/tankarena/recordings`; copied into `testResources/recordings`, each one
 * is replayed here on the headless server and must end exactly as it did when it was played.
 */
class RecordedPlaythroughTest {

    @Test
    fun `every committed recording replays to the outcome it was played to`() {
        val recordings = File("testResources/recordings").listFiles { file -> file.extension == "json" }
            ?.sortedBy { it.name }
            .orEmpty()
        for (file in recordings) {
            val recording = MatchRecording.fromJson(file.readText())
            assertEquals(
                GAME_RULES_VERSION,
                recording.rulesVersion,
                "${file.name} was played under game rules v${recording.rulesVersion}; the rules are now v$GAME_RULES_VERSION. " +
                    "Play the mission again and replace the recording.",
            )
            val expected = assertNotNull(recording.result, "${file.name} has no recorded result")
            val scene = assertNotNull(ShippedScenes.load(recording.mission), "${file.name}: unknown mission ${recording.mission}")
            val replayed = MatchReplay.play(recording, scene.sceneJson, scene.sidecar)
            assertEquals(expected, replayed, "${file.name} replayed to a different outcome")
        }
    }

    @Test
    fun `a match recorded through the local client replays identically`() {
        val scene = assertNotNull(ShippedScenes.load("begin2"))
        val client = LocalMatchClient.fromSceneJson(scene.sceneJson, scene.sidecar, MatchMode.SINGLE, recordAs = "begin2")
        // Turn a little, then drive straight into BEGIN2's pit ring.
        for (tick in 0 until 600) {
            client.submitInput(InputFrame(playerId = 0, inputSequence = tick.toLong(), turnRight = tick in 100 until 110, forward = tick >= 120))
            client.tick()
        }
        val recording = assertNotNull(client.recorder).recording()
        client.dispose()

        val played = assertNotNull(recording.result, "the player should have fallen into the pit")
        assertEquals(false, played.won)
        assertTrue(recording.inputs.size < 10, "inputs are stored as changes only, got ${recording.inputs.size}")
        val roundTripped = MatchRecording.fromJson(recording.toJson())
        assertEquals(played, MatchReplay.play(roundTripped, scene.sceneJson, scene.sidecar))
    }
}

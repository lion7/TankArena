package com.tankarena.app.editor

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Enforces ADR 0009's "Boundary rule": :game-editor must not pull in runtime simulation
 * subsystems from :game-server. The editor edits scene state; it must never depend on
 * collision resolution, terrain slide, or mission evaluation. Violations would let
 * authoring drag in headless-server semantics that would then need to evolve in lockstep
 * with the editor.
 */
class EditorBoundaryGuardTest {

    private val forbiddenSymbols = listOf(
        "com.pandulapeter.kubriko.collision.CollisionManager",
        "com.tankarena.sim.kubriko.TerrainSlideManager",
        "com.tankarena.sim.kubriko.server.ServerMatchPrototype",
    )

    @Test
    fun `editor sources do not reference simulation-only subsystems`() {
        val editorSourceRoot = locateEditorSourceRoot()
        val violations = mutableListOf<String>()

        editorSourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val text = file.readText()
                for (symbol in forbiddenSymbols) {
                    if (text.contains(symbol)) {
                        violations += "${file.relativeTo(editorSourceRoot).path} references $symbol"
                    }
                }
            }

        assertTrue(
            violations.isEmpty(),
            "editor must not depend on simulation-only :game-server subsystems:\n" +
                violations.joinToString("\n"),
        )
    }

    private fun locateEditorSourceRoot(): File {
        val candidates = sequenceOf(
            "../game-editor/src",
            "game-editor/src",
        )
        for (candidate in candidates) {
            val dir = File(candidate).absoluteFile
            if (dir.isDirectory) return dir
        }
        fail("could not locate game-editor/src from working directory ${File(".").absolutePath}")
    }
}

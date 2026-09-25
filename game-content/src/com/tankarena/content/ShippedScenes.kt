package com.tankarena.content

import kotlinx.serialization.json.Json

/** Classpath directory holding the committed `scene_*.json` + `metadata_*.json` pairs. */
const val SCENES_RESOURCE_DIR: String = "scenes"

/** One base name per line; written by `tools-mapconv scene-all` because classpath resources cannot be listed. */
const val SCENE_INDEX_FILE_NAME: String = "index.txt"

/** A shipped mission: the Kubriko scene JSON plus its parsed sidecar. */
data class ShippedScene(
    val baseName: String,
    val sceneJson: String,
    val sidecar: MapSceneSidecar,
)

/**
 * Loads the committed scenes from the classpath. This is the runtime content path; legacy
 * `.MAP` files are only read by `tools-mapconv` when the scenes are regenerated (ADR 0003).
 */
object ShippedScenes {

    private val json = Json { ignoreUnknownKeys = true }

    fun baseNames(classLoader: ClassLoader = defaultClassLoader()): List<String> =
        readResource(classLoader, "$SCENES_RESOURCE_DIR/$SCENE_INDEX_FILE_NAME")
            ?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toList()
            .orEmpty()

    fun load(baseName: String, classLoader: ClassLoader = defaultClassLoader()): ShippedScene? {
        val scene = readResource(classLoader, "$SCENES_RESOURCE_DIR/scene_$baseName.json") ?: return null
        val sidecarText = readResource(classLoader, "$SCENES_RESOURCE_DIR/metadata_$baseName.json") ?: return null
        return ShippedScene(baseName, scene, parseSidecar(sidecarText))
    }

    fun loadAll(classLoader: ClassLoader = defaultClassLoader()): List<ShippedScene> =
        baseNames(classLoader).mapNotNull { load(it, classLoader) }

    fun parseSidecar(text: String): MapSceneSidecar {
        val sidecar = json.decodeFromString(MapSceneSidecar.serializer(), text)
        require(sidecar.schemaVersion <= SCENE_SCHEMA_VERSION) {
            "Scene sidecar schemaVersion=${sidecar.schemaVersion} is newer than the runtime " +
                "schemaVersion=$SCENE_SCHEMA_VERSION; refusing to load."
        }
        return sidecar
    }

    private fun readResource(classLoader: ClassLoader, path: String): String? =
        classLoader.getResourceAsStream(path)?.use { it.readBytes().decodeToString() }

    private fun defaultClassLoader(): ClassLoader =
        Thread.currentThread().contextClassLoader ?: ShippedScenes::class.java.classLoader
}

package nl.tankarena.sim.map

import nl.tankarena.content.MapSceneSidecar

/** Classpath folder holding the shipped `<name>.tamap` files and their index. */
const val MAPS_RESOURCE_DIR: String = "maps"

/** One base name per line, written by `tools-mapconv import-all` (classpath folders cannot be listed). */
const val MAP_INDEX_FILE_NAME: String = "index.txt"

/** A map that ships with the game. */
class ShippedMap(val baseName: String, val document: MapDocument) {
    /** Header, texts and layers; matches get fresh objects through [nl.tankarena.sim.Match.fromMap]. */
    val sidecar: MapSceneSidecar by lazy { MapConversion.sidecarOf(document) }
}

/** The shipped maps from the classpath (`game-content/resources/maps`), read once. */
object ShippedMaps {
    private val loaded = HashMap<String, ShippedMap?>()

    fun baseNames(classLoader: ClassLoader = defaultClassLoader()): List<String> =
        readResource(classLoader, "$MAPS_RESOURCE_DIR/$MAP_INDEX_FILE_NAME")
            ?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toList()
            .orEmpty()

    fun load(baseName: String, classLoader: ClassLoader = defaultClassLoader()): ShippedMap? = synchronized(loaded) {
        loaded.getOrPut(baseName) {
            readResource(classLoader, "$MAPS_RESOURCE_DIR/$baseName.$MAP_FILE_EXTENSION")
                ?.let { ShippedMap(baseName, MapFile.read(it)) }
        }
    }

    fun loadAll(classLoader: ClassLoader = defaultClassLoader()): List<ShippedMap> =
        baseNames(classLoader).mapNotNull { load(it, classLoader) }

    private fun readResource(classLoader: ClassLoader, path: String): String? =
        classLoader.getResourceAsStream(path)?.use { it.readBytes().decodeToString() }

    private fun defaultClassLoader(): ClassLoader =
        Thread.currentThread().contextClassLoader ?: ShippedMaps::class.java.classLoader
}

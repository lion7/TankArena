package com.tankarena.render.kubriko

import com.pandulapeter.kubriko.Kubriko
import com.pandulapeter.kubriko.manager.Manager
import com.pandulapeter.kubriko.sprites.SpriteManager
import org.jetbrains.compose.resources.DrawableResource

internal class ScenePreloadManager(
    isLoggingEnabled: Boolean = false,
    instanceNameForLogging: String? = null,
) : Manager(
    isLoggingEnabled = isLoggingEnabled,
    instanceNameForLogging = instanceNameForLogging,
) {
    private val spriteManager by manager<SpriteManager>()
    private val pending = LinkedHashSet<DrawableResource>()
    private var initialized = false

    override fun onInitialize(kubriko: Kubriko) {
        initialized = true
        if (pending.isNotEmpty()) {
            spriteManager.preload(pending.toList())
            pending.clear()
        }
    }

    override fun onDispose() {
        initialized = false
        pending.clear()
    }

    fun requestPreload(resources: Collection<DrawableResource>) {
        if (resources.isEmpty()) return
        if (initialized) {
            spriteManager.preload(resources)
        } else {
            pending.addAll(resources)
        }
    }
}

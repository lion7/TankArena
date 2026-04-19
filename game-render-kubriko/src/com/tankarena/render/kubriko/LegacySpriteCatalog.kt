package com.tankarena.render.kubriko

import com.tankarena.content.LegacyResourceNaming
import com.tankarena.content.LegacySpriteResources
import game_render_kubriko.generated.resources.Res
import game_render_kubriko.generated.resources.allDrawableResources
import org.jetbrains.compose.resources.DrawableResource

/**
 * Bridges legacy sprite names to Compose Multiplatform [DrawableResource]
 * handles. The PNGs are emitted at build time by `tools-mapconv
 * extract-pictures-png` into `composeResources/drawable/`, and at runtime
 * Compose generates `Res.allDrawableResources` keyed by the lower-case
 * file name (without extension).
 */
internal class LegacySpriteCatalog private constructor(
    private val resourcesByLegacyName: Map<String, DrawableResource>,
) {
    fun findResource(legacyName: String): DrawableResource? {
        return resourcesByLegacyName[legacyName]
    }

    companion object {
        @OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
        val shared: LegacySpriteCatalog by lazy {
            val drawables = Res.allDrawableResources
            val mapping = LinkedHashMap<String, DrawableResource>(drawables.size)
            for (legacyName in LegacySpriteResources.availableLegacyNames) {
                val key = LegacyResourceNaming.safeKey(legacyName)
                val resource = drawables[key] ?: continue
                mapping[legacyName] = resource
            }
            LegacySpriteCatalog(mapping)
        }
    }
}

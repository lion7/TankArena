package com.tankarena.content

data class LegacyPictureEntry(
    val index: Int,
    val primaryName: String,
)

class LegacyWorldPictureCatalog internal constructor(
    private val names: List<String>,
) {
    val pictureCount: Int
        get() = names.size

    val entries: List<LegacyPictureEntry> by lazy {
        names.mapIndexed { index, name ->
            LegacyPictureEntry(index = index, primaryName = name)
        }
    }

    fun entryAt(index: Int): LegacyPictureEntry? = entries.getOrNull(index)

    fun findByName(name: String): LegacyPictureEntry? {
        return entries.firstOrNull { it.primaryName == name }
    }

    fun entriesForFamily(familyKey: String): List<LegacyPictureEntry> {
        return entries.filter { legacyPictureFamilyKey(it.primaryName) == familyKey }
    }
}

object LegacyPictureCatalog {
    fun forWorld(world: TankArenaWorld): LegacyWorldPictureCatalog = when (world) {
        TankArenaWorld.DESERT -> LegacyWorldPictureCatalog(GeneratedLegacyPictureCatalog.desertNames)
        TankArenaWorld.TEMPERATE -> LegacyWorldPictureCatalog(GeneratedLegacyPictureCatalog.temperateNames)
        TankArenaWorld.CITY -> LegacyWorldPictureCatalog(GeneratedLegacyPictureCatalog.cityNames)
        TankArenaWorld.NIGHT -> LegacyWorldPictureCatalog(GeneratedLegacyPictureCatalog.nightNames)
    }
}

internal fun legacyPictureFamilyKey(name: String): String {
    val cleaned = name
        .trim()
        .trimStart('@', '_', '^', '~')
        .substringBefore('.')

    val withoutTrailingDigits = cleaned.replace(Regex("""\d+$"""), "")
    return withoutTrailingDigits.ifBlank { cleaned.ifBlank { "UNKNOWN" } }
}

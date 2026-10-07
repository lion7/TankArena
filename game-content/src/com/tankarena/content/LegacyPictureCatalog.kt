package com.tankarena.content

/**
 * One row of the legacy `pc{world}` arrays from `src/data/pictures.c`.
 * Each row owns up to three picture names (intact / damaged / dead) so the
 * renderer can swap variants without re-reading the legacy source.
 */
data class LegacyPictureRecord(
    val nameVariants: List<String>,
) {
    val primaryName: String get() = nameVariants.first()
    fun nameForVariant(variant: LegacyPictureVariant): String {
        // The legacy engine falls back through the variant list when later
        // entries are missing (e.g. tiles that never become "dead").
        return nameVariants.getOrNull(variant.ordinal) ?: nameVariants.last()
    }
}

enum class LegacyPictureVariant {
    INTACT,
    DAMAGED,
    DEAD,
}


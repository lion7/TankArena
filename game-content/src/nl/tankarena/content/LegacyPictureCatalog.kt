package nl.tankarena.content

/**
 * One row of the legacy `pc{world}` arrays from `original/src/data/pictures.c`.
 * Each row owns up to three picture names (intact / damaged / dead) so the
 * renderer can swap variants without re-reading the legacy source.
 */
data class LegacyPictureRecord(
    val nameVariants: List<String>,
) {
    val primaryName: String get() = nameVariants.first()
    /**
     * The picture for [variant], or null when the row has none: legacy load_pictures() leaves that
     * `ptr[]` NULL and the drawing routines skip it, so a destroyed structure without a ruined
     * picture simply disappears.
     */
    fun nameForVariant(variant: LegacyPictureVariant): String? =
        nameVariants.getOrNull(variant.ordinal)?.takeIf { it.isNotEmpty() }
}

enum class LegacyPictureVariant {
    INTACT,
    DAMAGED,
    DEAD,
}


package com.tankarena.content

/**
 * Encodes legacy picture names (uppercase ASCII with the punctuation
 * `@_~^.-`) into Compose Multiplatform resource keys. Compose only accepts
 * `[a-z0-9_]` in resource file names so we use single-underscore escapes
 * that round-trip without colliding with literal characters.
 *
 * Mapping:
 *  - `A`..`Z`  ? lowercased
 *  - `0`..`9`  ? kept
 *  - `@`       ? `_a` (At-prefix variant in `pictures.c`)
 *  - `_`       ? `_u` (Underscore-prefix variant)
 *  - `~`       ? `_t` (Tilde-prefix variant)
 *  - `^`       ? `_c` (Caret-prefix variant)
 *  - `.`       ? `_d` (Dot)
 *  - `-`       ? `_h` (Hyphen)
 *
 * Result is prefixed with `pic_` so every key is a valid Kotlin identifier
 * and shares a recognisable namespace inside `composeResources/drawable/`.
 */
object LegacyResourceNaming {
    const val RESOURCE_PREFIX: String = "pic_"

    fun safeKey(legacyName: String): String {
        val builder = StringBuilder(RESOURCE_PREFIX.length + legacyName.length * 2)
        builder.append(RESOURCE_PREFIX)
        for (character in legacyName) {
            when {
                character in 'a'..'z' -> builder.append(character)
                character in 'A'..'Z' -> builder.append(character.lowercaseChar())
                character in '0'..'9' -> builder.append(character)
                character == '@' -> builder.append("_a")
                character == '_' -> builder.append("_u")
                character == '~' -> builder.append("_t")
                character == '^' -> builder.append("_c")
                character == '.' -> builder.append("_d")
                character == '-' -> builder.append("_h")
                else -> builder.append("_x").append(character.code.toString(16).padStart(2, '0'))
            }
        }
        return builder.toString()
    }
}

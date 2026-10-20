package nl.tankarena.ui.compose.menu

import androidx.compose.ui.input.key.Key
import nl.tankarena.content.GameModeCompatibility
import nl.tankarena.ui.compose.GameMode

/** Outcome of checking a typed or picked mission code / map name. */
sealed interface CodeResolution {
    data class Accepted(val mission: MissionEntry) : CodeResolution

    /** [message] is the legacy `message_key` text shown before returning to the mode menu. */
    data class Rejected(val message: String) : CodeResolution
}

/**
 * The mission-code gate in front of every game mode: legacy `single_game` (src/game/single.c),
 * `dual_v_computer_game` (src/game/dualvc.c) and `dual_game` (src/game/dual.c).
 *
 * The computer modes ask for a mission code, the player-vs-player mode for a map file name.
 */
object MissionCodes {
    /** `read_string(..., single_code, 20)`. */
    const val MAX_CODE_LENGTH: Int = 20

    /** `read_string(..., dual_map_name, 8)`: a DOS file name without extension. */
    const val MAX_MAP_NAME_LENGTH: Int = 8

    /** Legacy `check_suitable` (src/game/mapfiles.c). */
    const val NOT_SUITABLE: String = "THIS MAP IS NOT SUITABLE FOR THIS MODE"

    /** Legacy `select_password_from_list` with an empty `PASSWD.DAT`. */
    const val NO_PASSWORDS_KNOWN: String = "NO PASSWORDS KNOWN"

    /** Legacy `select_from_list` without a single dual map. */
    const val NO_MAPS_FOUND: String = "NO MAPS FOUND"

    /** Title of the legacy `select_password_from_list` panel. */
    const val KNOWN_PASSWORDS_TITLE: String = "KNOWN PASSWORDS"

    /** Title of the legacy `select_from_list` panel. */
    const val SELECT_MAP_TITLE: String = "SELECT MAP"

    /** The prompt bar text, ending in the space before the typed text. */
    fun prompt(mode: GameMode): String = when (mode) {
        GameMode.PLAYER_VS_PLAYER -> "ENTER MAP (ESC = LIST): "
        else -> "MISSION CODE (ESC = LIST): "
    }

    fun maxLength(mode: GameMode): Int =
        if (mode == GameMode.PLAYER_VS_PLAYER) MAX_MAP_NAME_LENGTH else MAX_CODE_LENGTH

    /** Initial values of the legacy statics `single_code`, `dual_v_computer_code` and `dual_map_name`. */
    fun defaultCode(mode: GameMode): String = when (mode) {
        GameMode.SINGLE_PLAYER_VS_COMPUTER -> "BEGIN1"
        GameMode.DUAL_PLAYER_VS_COMPUTER -> "AIR STRIKE"
        GameMode.PLAYER_VS_PLAYER -> "FLAGISLE"
    }

    fun invalidCodeMessage(mode: GameMode): String = when (mode) {
        GameMode.SINGLE_PLAYER_VS_COMPUTER -> "INVALID CODE, TRY \"BEGIN1\" FOR FIRST LEVEL."
        GameMode.DUAL_PLAYER_VS_COMPUTER -> "INVALID CODE, TRY \"AIR STRIKE\"."
        GameMode.PLAYER_VS_PLAYER -> "FILE NOT FOUND"
    }

    /**
     * Finds the mission for [input]. The computer modes compare the code exactly with every map's
     * `this_mission_code` (the legacy `strcmp` loop over the `MAPS` directory); player vs player opens
     * the map file by name, which DOS matches case-insensitively. A found map that the mode
     * cannot load is rejected like legacy `check_suitable`.
     *
     * Several maps share a code (`LAVA 2P`, `20X20WET`); the legacy loop took whichever the
     * directory listed first, here a map that suits the mode wins.
     */
    fun resolve(missions: List<MissionEntry>, input: String, mode: GameMode): CodeResolution {
        val wanted = input.trim()
        val matches = if (wanted.isEmpty()) {
            emptyList()
        } else if (mode == GameMode.PLAYER_VS_PLAYER) {
            missions.filter { it.baseName.equals(wanted, ignoreCase = true) }
        } else {
            missions.filter { it.sidecar.metadata.missionCode.trim() == wanted }
        }
        if (matches.isEmpty()) return CodeResolution.Rejected(invalidCodeMessage(mode))
        val suitable = matches.playableIn(mode).firstOrNull()
            ?: return CodeResolution.Rejected(NOT_SUITABLE)
        return CodeResolution.Accepted(suitable)
    }

    /**
     * Legacy `search_passwd` + `select_password_from_list` (src/game/listcod.c, passwd.c): the
     * codes the player reached, followed by every map with a public password and every
     * dual-vs-computer map.
     *
     * Like the original, every code is listed in every mode (a code for another mode is then refused
     * with "THIS MAP IS NOT SUITABLE"); with [onlyForMode] (Extended, ADR 0020) only codes that start
     * a mission in [mode] are offered.
     */
    fun knownPasswords(
        missions: List<MissionEntry>,
        reached: List<String>,
        mode: GameMode,
        onlyForMode: Boolean = false,
    ): List<String> {
        val public = missions
            .filter {
                it.sidecar.metadata.publicPassword ||
                    it.sidecar.metadata.modeCompatibility == GameModeCompatibility.DUAL_VS_COMPUTER
            }
            .map { it.sidecar.metadata.missionCode.trim() }
        return (reached.map { it.trim() } + public)
            .filter { it.isNotEmpty() }
            .distinct()
            .filter { !onlyForMode || resolve(missions, it, mode) is CodeResolution.Accepted }
    }

    /** Legacy `select_from_list` in DUAL mode: "don't care" and dual maps, sorted by file name. */
    fun selectableMaps(missions: List<MissionEntry>): List<String> = missions
        .playableIn(GameMode.PLAYER_VS_PLAYER)
        .map { it.baseName.uppercase() }
        .distinct()
        .sortedWith(String.CASE_INSENSITIVE_ORDER)

    /** The list behind the prompt's ESC: known passwords, or map names for player vs player. */
    fun listEntries(missions: List<MissionEntry>, reached: List<String>, mode: GameMode, onlyForMode: Boolean = false): List<String> =
        if (mode == GameMode.PLAYER_VS_PLAYER) selectableMaps(missions) else knownPasswords(missions, reached, mode, onlyForMode)

    fun listTitle(mode: GameMode): String =
        if (mode == GameMode.PLAYER_VS_PLAYER) SELECT_MAP_TITLE else KNOWN_PASSWORDS_TITLE

    fun emptyListMessage(mode: GameMode): String =
        if (mode == GameMode.PLAYER_VS_PLAYER) NO_MAPS_FOUND else NO_PASSWORDS_KNOWN
}

/**
 * Legacy `read_string` (src/game/prompt.c) reads scan codes, not characters: the letter rows,
 * the digit row and the space bar, always as upper case on a QWERTY layout.
 */
private val PROMPT_KEYS: Map<Key, Char> = buildMap {
    val letters = listOf(
        Key.A, Key.B, Key.C, Key.D, Key.E, Key.F, Key.G, Key.H, Key.I, Key.J, Key.K, Key.L, Key.M,
        Key.N, Key.O, Key.P, Key.Q, Key.R, Key.S, Key.T, Key.U, Key.V, Key.W, Key.X, Key.Y, Key.Z,
    )
    letters.forEachIndexed { index, key -> put(key, 'A' + index) }
    val digits = listOf(Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)
    digits.forEachIndexed { index, key -> put(key, '0' + index) }
    put(Key.Spacebar, ' ')
}

/** The character legacy `read_string` types for [key], or `null` when the key types nothing. */
fun promptCharFor(key: Key): Char? = PROMPT_KEYS[key]

/** Legacy `read_string`: appends [typed] while the text is shorter than [maxLength]. */
fun String.typeInPrompt(typed: Char, maxLength: Int): String {
    val c = typed.uppercaseChar()
    val allowed = c in 'A'..'Z' || c in '0'..'9' || c == ' '
    return if (allowed && length < maxLength) this + c else this
}

/** Legacy `read_string`: backspace removes the last character. */
fun String.backspaceInPrompt(): String = dropLast(1)

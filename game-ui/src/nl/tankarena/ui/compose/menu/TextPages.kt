package nl.tankarena.ui.compose.menu

/** The help files of the legacy main menu, bundled from `original/DATA/` as client resources. */
enum class HelpFile(val resourceName: String) {
    /** Legacy `arena_info` (original/src/game/menu.c): `original/DATA/TANK.HLP`. */
    ARENA_INFO("TANK.HLP"),

    /** Legacy `keys_info` (original/src/game/menu.c): `original/DATA/KEYS.HLP`. */
    DEFAULT_KEYS("KEYS.HLP"),
    ;

    fun load(): String = try {
        HelpFile::class.java.classLoader?.getResourceAsStream("help/$resourceName")
            ?.use { it.readBytes().decodeToString() }
            .orEmpty()
    } catch (e: Exception) {
        System.err.println("Cannot read help file $resourceName: ${e.message}")
        ""
    }
}

/**
 * Paging rules of legacy `view_file` (original/src/game/prompt.c): the text is shown in upper case,
 * [VISIBLE_LINES] lines at a time, and the cursor keys move it by [SCROLL_STEP] lines.
 */
object TextPages {
    /** `num_lines`. */
    const val VISIBLE_LINES: Int = 22

    /** `num_lines / 2 - 1`. */
    const val SCROLL_STEP: Int = 10

    /** Splits [text] into upper-case display lines, without trailing blank lines. */
    fun lines(text: String): List<String> = text
        .replace("\r", "")
        .replace("\t", "    ")
        .uppercase()
        .split('\n')
        .dropLastWhile { it.isBlank() }

    /** The first line shown after scrolling [delta] lines from [top]; the last page stays full. */
    fun scroll(top: Int, delta: Int, lineCount: Int): Int =
        (top + delta).coerceIn(0, maxTop(lineCount))

    fun maxTop(lineCount: Int): Int = (lineCount - VISIBLE_LINES).coerceAtLeast(0)
}

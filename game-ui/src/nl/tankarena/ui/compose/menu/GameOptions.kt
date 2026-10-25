package nl.tankarena.ui.compose.menu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import java.io.File
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nl.tankarena.app.AppDirs
import nl.tankarena.net.DEFAULT_RELAY
import nl.tankarena.sim.Ruleset
import nl.tankarena.ui.compose.legacy.Mode13View

/**
 * The options the game plays with: the active [ruleset]'s own (saved apart, see [SavedSettings]) and
 * the shared ones. Classic's are the game options of legacy menu.c and the other settings TANK.CFG
 * keeps (original/src/game/settings.c): `race_laps`, `rnds`, `lose`, `gamcor`, `music_volume`,
 * `sound_volume` and `victory_count`.
 */
data class GameOptions(
    /** Legacy `race_laps`: 1..6. */
    val raceLaps: Int = 3,
    /** Legacy `rnds`: 1, 3, 5, 7 or 9 rounds, or [ENDLESS] (`'~'`, shown as ∞). */
    val rounds: Int = 3,
    /** Legacy `lose`: 0 off, 1 the losers tank after one round behind, 2 after three. */
    val losersTank: Int = 0,
    /** Legacy `gamcor`: -3..3. */
    val gamma: Int = 0,
    /** Legacy `music_volume` and `sound_volume`: 0, 85, 170 or 255. */
    val musicVolume: Int = 255,
    val soundVolume: Int = 255,
    /** Legacy `victory_count`: missions won since THUNDER.FLI last played. */
    val victoryCount: Int = 0,
    /** Classic or Extended (ADR 0023): whose options these are. Not saved: every start is Extended. */
    val ruleset: Ruleset = Ruleset.CLASSIC,
    /**
     * Legacy `single_code`, `dual_v_computer_code` and `dual_map_name` in TANK.CFG: the text the code
     * prompt of each game mode starts with, by [GameMode] name.
     */
    val lastCodes: Map<String, String> = emptyMap(),
    /** Legacy `show_radar` (F12). */
    val showRadar: Boolean = true,
    /** Legacy `win_size` (- and = in a game): 0 is the full screen, every step 5% smaller, up to 5. */
    val windowSize: Int = 0,
    /** Legacy `grab_no`: the number of the next screenshot (PrtScr). */
    val screenshotNumber: Int = 0,
    /** Legacy `plN_mode` and `keyN_*`: each player's control mode and keys. */
    val controls: List<PlayerControls> = DEFAULT_CONTROLS,
    /** Extended: show movement between the game's steps (33 a second), one step later. */
    val smoothMotion: Boolean = true,
    /** Extended: the picture fills the window instead of growing by whole multiples. */
    val fillWindow: Boolean = true,
    /** Extended: on a wide window the map windows grow across and show more of the map. */
    val wideView: Boolean = true,
    /** Extended network games: the internet relay's address (ADR 0028); empty for none. */
    val relay: String = DEFAULT_RELAY,
    /** Extended: player one's keys and steering (with the mouse). */
    val mouseControls: PlayerControls = MOUSE_PLAYER_CONTROLS,
    /** Extended: gamepads as twin sticks; off: as the original's joystick (player two's pad buttons). */
    val twinSticks: Boolean = true,
) {
    /** Legacy read_controls(): `=` makes the window bigger, `-` smaller. */
    fun biggerWindow(): GameOptions = copy(windowSize = (windowSize - 1).coerceAtLeast(0))
    fun smallerWindow(): GameOptions = copy(windowSize = (windowSize + 1).coerceAtMost(MAX_WINDOW_SIZE))

    /** Legacy select_laps(). */
    fun nextLaps(): GameOptions = copy(raceLaps = if (raceLaps >= 6) 1 else raceLaps + 1)

    /** Legacy select_rounds(): 1, 3, 5, 7, 9, ∞, 1, ... */
    fun nextRounds(): GameOptions = copy(
        rounds = when {
            rounds == ENDLESS -> 1
            rounds + 2 > 9 -> ENDLESS
            else -> rounds + 2
        },
    )

    /** Legacy enable_losers_tank(). */
    fun nextLosersTank(): GameOptions = copy(losersTank = (losersTank + 1) % 3)

    /** Legacy gamma_correction() and the F11 key. */
    fun nextGamma(): GameOptions = copy(gamma = if (gamma >= 3) -3 else gamma + 1)

    /** Legacy music_vol() / sound_vol(): off, low, medium, high, off, ... */
    fun nextMusicVolume(): GameOptions = copy(musicVolume = VOLUMES[(musicVolume / 85 + 1) and 3])
    fun nextSoundVolume(): GameOptions = copy(soundVolume = VOLUMES[(soundVolume / 85 + 1) and 3])

    /** Legacy `end_game`: a player has won more than half of the rounds; never with endless rounds. */
    fun matchDecided(wins: List<Int>): Boolean = rounds != ENDLESS && wins.any { it > rounds / 2 }

    /**
     * Legacy debrief(): every fifth mission won plays THUNDER.FLI. Returns the options with the
     * count moved on, and whether the animation plays now.
     */
    fun afterVictory(): Pair<GameOptions, Boolean> {
        val count = victoryCount + 1
        return if (count > 4) copy(victoryCount = 0) to true else copy(victoryCount = count) to false
    }

    /** The entries of legacy `gameop[]`, with the current values. */
    val labels: List<String>
        get() = listOf(
            "Race consists of $raceLaps laps",
            "Match consists of ${if (rounds == ENDLESS) INFINITY else rounds.toString()} rounds",
            LOSER[losersTank.coerceIn(0, 2)],
            "Gamma correction $gamma",
            MUSIC[(musicVolume / 85).coerceIn(0, 3)],
            SOUND[(soundVolume / 85).coerceIn(0, 3)],
            "Leave this menu",
        )

    companion object {
        /** Legacy `rnds == '~'`. */
        const val ENDLESS = 0

        const val MAX_WINDOW_SIZE = 5

        /** `'\xEC'`, the big font's infinity sign. */
        const val INFINITY = 'ì'

        internal val VOLUMES = intArrayOf(0, 85, 170, 255)
        private val LOSER = listOf(
            "Losers tank disabled",
            "Losers tank enabled at 1 loss",
            "Losers tank enabled at 3 losses",
        )
        private val SOUND = listOf("Sound off", "Sound volume - Low", "Sound volume - Medium", "Sound volume - High")
        private val MUSIC = listOf("Music off", "Music volume - Low", "Music volume - Medium", "Music volume - High")

        /** Legacy `gam[gamcor + 3]`. */
        private val GAMMA = floatArrayOf(1.8f, 1.4f, 1.1f, 1.0f, 0.8f, 0.6f, 0.4f)

        /**
         * Legacy correct_pal(): each 6-bit palette level `s` becomes `63^(1-g) * s^g`. Returns the
         * table for 8-bit levels, or null when gamma correction is off.
         */
        fun gammaTable(gamma: Int): ByteArray? {
            if (gamma == 0) return null
            val g = GAMMA[gamma.coerceIn(-3, 3) + 3].toDouble()
            return ByteArray(256) { level ->
                val corrected = 63.0.pow(1 - g) * (level / 255.0 * 63).pow(g)
                (corrected / 63 * 255).roundToInt().coerceIn(0, 255).toByte()
            }
        }
    }
}

private const val CONTROLS_SETUP = "Controls setup"

/** Shared by both rulesets: what is about the player's ears and screen, and the screenshots' numbering. */
@Serializable
data class SharedSettings(
    val musicVolume: Int = 255,
    val soundVolume: Int = 255,
    val gamma: Int = 0,
    val screenshotNumber: Int = 0,
)

/** Classic's own settings: what the original's TANK.CFG holds. */
@Serializable
data class ClassicSettings(
    val raceLaps: Int = 3,
    val rounds: Int = 3,
    val losersTank: Int = 0,
    val victoryCount: Int = 0,
    val lastCodes: Map<String, String> = emptyMap(),
    val showRadar: Boolean = true,
    val windowSize: Int = 0,
    val controls: List<PlayerControls> = DEFAULT_CONTROLS,
)

/**
 * Extended's own settings. Its gamepads in the original's joystick mode have the default buttons:
 * the buttons are set in Classic's setup only.
 */
@Serializable
data class ExtendedSettings(
    val raceLaps: Int = 3,
    val rounds: Int = 3,
    val losersTank: Int = 0,
    val victoryCount: Int = 0,
    val lastCodes: Map<String, String> = emptyMap(),
    val showRadar: Boolean = true,
    val windowSize: Int = 0,
    val mouseControls: PlayerControls = MOUSE_PLAYER_CONTROLS,
    val twinSticks: Boolean = true,
    val smoothMotion: Boolean = true,
    val fillWindow: Boolean = true,
    val wideView: Boolean = true,
    val relay: String = DEFAULT_RELAY,
)

/**
 * `settings.json`: the shared settings and each ruleset's own. A file of another [version] (or none)
 * starts clean.
 */
@Serializable
data class SavedSettings(
    val version: Int = 0,
    val shared: SharedSettings = SharedSettings(),
    val classic: ClassicSettings = ClassicSettings(),
    val extended: ExtendedSettings = ExtendedSettings(),
) {
    /** The options [ruleset] plays with. */
    fun options(ruleset: Ruleset): GameOptions {
        val shared = GameOptions(
            ruleset = ruleset,
            musicVolume = shared.musicVolume,
            soundVolume = shared.soundVolume,
            gamma = shared.gamma,
            screenshotNumber = shared.screenshotNumber,
        )
        return when (ruleset) {
            Ruleset.CLASSIC -> with(classic) {
                shared.copy(
                    raceLaps = raceLaps, rounds = rounds, losersTank = losersTank, victoryCount = victoryCount,
                    lastCodes = lastCodes, showRadar = showRadar, windowSize = windowSize, controls = controls,
                )
            }
            Ruleset.EXTENDED -> with(extended) {
                shared.copy(
                    raceLaps = raceLaps, rounds = rounds, losersTank = losersTank, victoryCount = victoryCount,
                    lastCodes = lastCodes, showRadar = showRadar, windowSize = windowSize, mouseControls = mouseControls,
                    twinSticks = twinSticks, smoothMotion = smoothMotion, fillWindow = fillWindow, wideView = wideView, relay = relay,
                )
            }
        }
    }

    /** [options] (of their ruleset) kept: the shared ones and that ruleset's own; the other ruleset's stay. */
    fun with(options: GameOptions): SavedSettings = with(options) {
        val kept = copy(shared = SharedSettings(musicVolume, soundVolume, gamma, screenshotNumber))
        when (ruleset) {
            Ruleset.CLASSIC -> kept.copy(
                classic = ClassicSettings(raceLaps, rounds, losersTank, victoryCount, lastCodes, showRadar, windowSize, controls),
            )
            Ruleset.EXTENDED -> kept.copy(
                extended = ExtendedSettings(
                    raceLaps, rounds, losersTank, victoryCount, lastCodes, showRadar, windowSize,
                    mouseControls, twinSticks, smoothMotion, fillWindow, wideView, relay,
                ),
            )
        }
    }

    companion object {
        /** Settings of an older layout are not read, but replaced. */
        const val VERSION = 2
    }
}

/**
 * The settings in the user's config directory (`settings.json` next to the known passwords).
 * Like [PasswordStore], IO failures fall back to the defaults or leave the file as it was.
 */
class GameOptionsStore(private val file: File = File(AppDirs.config, "settings.json")) {
    fun load(): SavedSettings = try {
        val saved = if (file.isFile) json.decodeFromString(SavedSettings.serializer(), file.readText()) else null
        saved?.takeIf { it.version == SavedSettings.VERSION } ?: SavedSettings(version = SavedSettings.VERSION)
    } catch (e: Exception) {
        System.err.println("Cannot read settings from $file: ${e.message}")
        SavedSettings(version = SavedSettings.VERSION)
    }

    /** Legacy save_settings(). */
    fun save(settings: SavedSettings) {
        try {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(SavedSettings.serializer(), settings))
        } catch (e: Exception) {
            System.err.println("Cannot save settings to $file: ${e.message}")
        }
    }

    private companion object {
        val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            encodeDefaults = true
        }
    }
}

/**
 * The options in play, observable by the UI: those of the active ruleset; every change is saved to
 * [store] (legacy save_settings()). Without a store they only live in memory (the tests). An update
 * with another ruleset switches to that ruleset's options and changes nothing else.
 */
class GameSettings(private val store: GameOptionsStore? = null) {
    private var saved: SavedSettings = store?.load() ?: SavedSettings(version = SavedSettings.VERSION)

    var options: GameOptions by mutableStateOf(saved.options(Ruleset.CLASSIC))
        private set

    fun update(changed: GameOptions) {
        if (changed == options) return
        if (changed.ruleset != options.ruleset) {
            options = saved.options(changed.ruleset)
            return
        }
        saved = saved.with(changed)
        options = changed
        store?.save(saved)
    }
}

/**
 * Legacy game_options(): the `menu()` of `gameop[]`. Enter on an entry changes that option
 * ([onChange]) and the menu stays, with the cursor where it was; "Leave this menu" and ESC
 * leave ([onLeave]).
 */
@Composable
fun GameOptionsScreen(
    options: GameOptions,
    onChange: (GameOptions) -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Not in the original, which set the controls in a separate setup program (TANK -SETUP): an entry
     * that opens the controls setup, above "Leave this menu".
     */
    onControls: (() -> Unit)? = null,
) {
    var selected by remember { mutableIntStateOf(0) }
    val labels = if (onControls == null) options.labels else options.labels.dropLast(1) + CONTROLS_SETUP + options.labels.last()
    Mode13View(
        modifier = modifier,
        onKey = { event ->
            when (event.key) {
                Key.DirectionUp -> selected = (selected - 1 + labels.size) % labels.size
                Key.DirectionDown -> selected = (selected + 1) % labels.size
                Key.Enter, Key.NumPadEnter -> when (selected) {
                    0 -> onChange(options.nextLaps())
                    1 -> onChange(options.nextRounds())
                    2 -> onChange(options.nextLosersTank())
                    3 -> onChange(options.nextGamma())
                    4 -> onChange(options.nextMusicVolume())
                    5 -> onChange(options.nextSoundVolume())
                    6 -> if (onControls != null) onControls() else onLeave()
                    else -> onLeave()
                }
                Key.Escape -> onLeave()
                else -> return@Mode13View false
            }
            true
        },
    ) {
        drawMenu("GAME OPTIONS:", labels, selected)
    }
}

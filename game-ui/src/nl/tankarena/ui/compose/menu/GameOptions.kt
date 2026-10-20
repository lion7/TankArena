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
import nl.tankarena.sim.Ruleset
import nl.tankarena.ui.compose.legacy.Mode13View
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The game options of legacy menu.c and the other settings TANK.CFG keeps (src/game/settings.c):
 * `race_laps`, `rnds`, `lose`, `gamcor`, `music_volume`, `sound_volume` and `victory_count`.
 */
@Serializable
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
    /** Classic or Extended (ADR 0023); null until the player has chosen. Not in TANK.CFG. */
    val ruleset: Ruleset? = null,
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

/**
 * The settings in the user's config directory (`settings.json` next to the known passwords).
 * Like [PasswordStore], IO failures fall back to the defaults or leave the file as it was.
 */
class GameOptionsStore(private val file: Path = PasswordStore.defaultFile().resolveSibling("settings.json")) {
    fun load(): GameOptions = try {
        if (Files.isRegularFile(file)) json.decodeFromString(GameOptions.serializer(), Files.readString(file)) else GameOptions()
    } catch (e: Exception) {
        System.err.println("Cannot read settings from $file: ${e.message}")
        GameOptions()
    }

    /** Legacy save_settings(). */
    fun save(options: GameOptions) {
        try {
            file.parent?.let { Files.createDirectories(it) }
            Files.writeString(file, json.encodeToString(GameOptions.serializer(), options))
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
 * The options in play, observable by the UI; every change is saved to [store] (legacy
 * save_settings()). Without a store they only live in memory (the tests).
 */
class GameSettings(private val store: GameOptionsStore? = null) {
    var options: GameOptions by mutableStateOf(store?.load() ?: GameOptions())
        private set

    fun update(changed: GameOptions) {
        if (changed == options) return
        options = changed
        store?.save(changed)
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

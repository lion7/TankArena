package nl.tankarena.app

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import nl.tankarena.audio.AudioBackend
import nl.tankarena.input.PadReading
import java.io.File

/**
 * What the game needs from the device it runs on (ADR 0025): sound, music, gamepads, quitting and
 * screenshots. The desktop and Android apps each give one to [TankArenaApp].
 */
interface GamePlatform {
    /** A new sound output for a game or replay (the mixer's backend). */
    fun audioBackend(): AudioBackend

    /** The music player, one for the whole game. */
    val music: Music

    /** The connected gamepads, in order; read once a frame. */
    fun gamepads(): List<PadReading> = emptyList()

    /**
     * Presses [Key] as if typed, so the pads work the menus (the desktop's pads only report their
     * state); null where the platform turns pad buttons into keys itself (Android).
     */
    val postKey: ((Key) -> Unit)?

    /** A touch screen: the game shows its virtual controller and takes taps (Android). */
    val touchScreen: Boolean get() = false

    /** This machine's name for the others in a network game. */
    val deviceName: String get() = System.getProperty("user.name")?.takeIf { it.isNotBlank() } ?: "Player"

    /** Leaves the game. */
    fun quit()

    /** Saves the screen as a PNG in [file]; false when the platform cannot. */
    fun screenshot(file: File): Boolean = false
}

/** The game's music (legacy init_player()): one tune at a time, looping. */
interface Music {
    /** Allegro set_volume(-1, music): 0..255. */
    var volume: Int

    /** Starts [tune] (a file in `music/`) unless it is already playing. */
    fun play(tune: String)

    fun dispose()

    companion object {
        const val TUNE = "TUNE.MID"
        const val NIGHT_TUNE = "BELVED.MID"
    }
}

/**
 * Where the game keeps the player's files (ADR 0020): [config] for `settings.json` and
 * `passwd.json`, [data] for maps, recordings and screenshots. The desktop uses the XDG folders
 * (`~/.config/tankarena`, `~/.local/share/tankarena`); Android sets its own before the game starts.
 */
object AppDirs {
    var config: File = File(
        System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() } ?: ((System.getProperty("user.home") ?: ".") + "/.config"),
        "tankarena",
    )
    var data: File = File(
        System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: ((System.getProperty("user.home") ?: ".") + "/.local/share"),
        "tankarena",
    )
}

/**
 * The keys the whole game sees first, wherever the focus is (legacy read_controls() in a game): the
 * platform's window or activity hands every key event to [handle]; the game sets [handler].
 */
class GameKeyHook {
    @Volatile
    var handler: (KeyEvent) -> Boolean = { false }

    fun handle(event: KeyEvent): Boolean = handler(event)
}

/** Whether the screens take taps (the platform's [GamePlatform.touchScreen]). */
val LocalTouchScreen = staticCompositionLocalOf { false }

/** The platform of the running game, for screens deep down (replays' sound). */
val LocalGamePlatform = staticCompositionLocalOf<GamePlatform> { error("no GamePlatform") }

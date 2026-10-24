package nl.tankarena.ui.compose.extended

import nl.tankarena.input.Seat
import nl.tankarena.input.keyName
import nl.tankarena.ui.compose.menu.ControlMode
import nl.tankarena.ui.compose.menu.ControlAction
import nl.tankarena.ui.compose.menu.PlayerControls
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import nl.tankarena.ui.compose.menu.GameOptions
import nl.tankarena.ui.compose.menu.JoystickAction
import nl.tankarena.ui.compose.menu.padButtonsText

private fun volumeText(volume: Int) = listOf("off", "low", "medium", "high")[(volume / 85).coerceIn(0, 3)]

/** A setting's value one step [up] or down, stopping at the ends (Left/Right on the settings page). */
internal fun GameOptions.stepped(setting: ExtendedSetting, up: Boolean): GameOptions {
    fun <T> along(values: List<T>, current: T): T = values[(values.indexOf(current).coerceAtLeast(0) + if (up) 1 else -1).coerceIn(0, values.lastIndex)]
    return when (setting) {
        // Right makes the window bigger: win_size counts down.
        ExtendedSetting.WINDOW_SIZE -> if (up) biggerWindow() else smallerWindow()
        ExtendedSetting.SMOOTH_MOTION -> copy(smoothMotion = !smoothMotion)
        ExtendedSetting.PICTURE -> copy(fillWindow = !fillWindow)
        ExtendedSetting.WIDE_VIEW -> copy(wideView = !wideView)
        ExtendedSetting.GAMMA -> copy(gamma = (gamma + if (up) 1 else -1).coerceIn(-3, 3))
        ExtendedSetting.RADAR -> copy(showRadar = !showRadar)
        ExtendedSetting.MUSIC -> copy(musicVolume = along(GameOptions.VOLUMES.toList(), musicVolume))
        ExtendedSetting.SOUND -> copy(soundVolume = along(GameOptions.VOLUMES.toList(), soundVolume))
        ExtendedSetting.ROUNDS -> copy(rounds = along(listOf(1, 3, 5, 7, 9, GameOptions.ENDLESS), rounds))
        ExtendedSetting.LAPS -> copy(raceLaps = (raceLaps + if (up) 1 else -1).coerceIn(1, 6))
        ExtendedSetting.LOSERS_TANK -> copy(losersTank = (losersTank + if (up) 1 else -1).coerceIn(0, 2))
        ExtendedSetting.CONTROLS -> this
    }
}

/** The settings on Extended's settings page, in order. */
internal enum class ExtendedSetting { CONTROLS, WINDOW_SIZE, SMOOTH_MOTION, PICTURE, WIDE_VIEW, GAMMA, RADAR, MUSIC, SOUND, ROUNDS, LAPS, LOSERS_TANK }

internal fun losersTankText(value: Int) = listOf("off", "after 1 lost round", "after 3 lost rounds")[value.coerceIn(0, 2)]

/**
 * Extended settings on one page: every entry shows its value; Left/Right step it down or up, Enter
 * goes round the choices. Controls opens the controls setup.
 */
@Composable
fun ExtendedSettingsPage(
    options: GameOptions,
    onChange: (GameOptions) -> Unit,
    onControls: () -> Unit,
    onBack: () -> Unit,
) {
    val keys = remember { KeyRouter() }
    val entries: List<Pair<ListEntry, () -> Unit>> = ExtendedSetting.entries.map { setting ->
        when (setting) {
            ExtendedSetting.CONTROLS -> ListEntry("Controls", "keyboard and mouse, gamepads, a second keyboard player") to onControls
            ExtendedSetting.WINDOW_SIZE -> ListEntry("Window size", "${100 - options.windowSize * 5}%") to {
                onChange(if (options.windowSize >= GameOptions.MAX_WINDOW_SIZE) options.copy(windowSize = 0) else options.smallerWindow())
            }
            ExtendedSetting.SMOOTH_MOTION -> ListEntry("Smooth motion", if (options.smoothMotion) "on" else "off") to { onChange(options.copy(smoothMotion = !options.smoothMotion)) }
            ExtendedSetting.PICTURE -> ListEntry("Picture", if (options.fillWindow) "fills the window" else "whole multiples, as in DOSBox") to {
                onChange(options.copy(fillWindow = !options.fillWindow))
            }
            ExtendedSetting.WIDE_VIEW -> ListEntry("Wide view", if (options.wideView) "on: more of the map on a wide window" else "off: 4:3") to {
                onChange(options.copy(wideView = !options.wideView))
            }
            ExtendedSetting.GAMMA -> ListEntry("Gamma correction", options.gamma.toString()) to { onChange(options.nextGamma()) }
            ExtendedSetting.RADAR -> ListEntry("Radar", if (options.showRadar) "on" else "off") to { onChange(options.copy(showRadar = !options.showRadar)) }
            ExtendedSetting.MUSIC -> ListEntry("Music", volumeText(options.musicVolume)) to { onChange(options.nextMusicVolume()) }
            ExtendedSetting.SOUND -> ListEntry("Sound", volumeText(options.soundVolume)) to { onChange(options.nextSoundVolume()) }
            ExtendedSetting.ROUNDS -> ListEntry("Rounds in a match", if (options.rounds == GameOptions.ENDLESS) "endless" else options.rounds.toString()) to {
                onChange(options.nextRounds())
            }
            ExtendedSetting.LAPS -> ListEntry("Race laps", options.raceLaps.toString()) to { onChange(options.nextLaps()) }
            ExtendedSetting.LOSERS_TANK -> ListEntry("Losers tank", losersTankText(options.losersTank)) to { onChange(options.nextLosersTank()) }
        }
    }
    ExtendedPage(
        title = "Settings",
        onBack = onBack,
        footer = "Left/Right change a setting, Enter goes round; settings are kept for next time",
        onKey = keys::handle,
    ) {
        ExtendedList(
            entries.map { it.first },
            onPick = { entries[it].second() },
            keys = keys,
            onStep = { index, up -> onChange(options.stepped(ExtendedSetting.entries[index], up)) },
        )
    }
}

/** Extended help: the original's two help files. */
@Composable
fun ExtendedHelpPage(onArenaInfo: () -> Unit, onKeys: () -> Unit, onBack: () -> Unit) {
    val keys = remember { KeyRouter() }
    val entries = listOf(
        ListEntry("How to play", "the arena, vehicles, weapons and objects") to onArenaInfo,
        ListEntry("Keys", "the keys in a game") to onKeys,
    )
    ExtendedPage(title = "Help", onBack = onBack, onKey = keys::handle) {
        ExtendedList(entries.map { it.first }, onPick = { entries[it].second() }, keys = keys)
    }
}

private fun keys(controls: PlayerControls, vararg actions: ControlAction) =
    actions.joinToString(" ") { action -> controls.key(action)?.let { keyName(it) } ?: "-" }

/**
 * What [seat] plays with, as label and controls, for the join screen and the keys help. [place] is the
 * player's place (an original joystick's buttons are those of player one's or two's settings); with
 * [arrows] the arrow keys drive the keyboard-and-mouse player too (nobody plays with a key set).
 */
fun seatControls(seat: Seat, place: Int, options: GameOptions, arrows: Boolean): List<Pair<String, String>> {
    val pad = seat.pad != null && seat.keys == null
    return when {
        seat.mouse -> options.mouseControls.let { one ->
            listOf(
                "Drive" to keys(one, ControlAction.UP, ControlAction.LEFT, ControlAction.DOWN, ControlAction.RIGHT) +
                    if (arrows) " or the arrow keys" else "",
                "Aim" to "the mouse pointer, or ${keys(one, ControlAction.AIM_LEFT, ControlAction.AIM_RIGHT)}",
                "Fire" to "left mouse button or ${keys(one, ControlAction.FIRE)}",
                "Shield" to "right mouse button or ${keys(one, ControlAction.SHIELD)}",
                "Weapons" to "mouse wheel or ${keys(one, ControlAction.WEAPON_LEFT, ControlAction.WEAPON_RIGHT)}",
            )
        }
        pad && options.twinSticks -> listOf(
            "Drive" to "left stick",
            "Aim" to "right stick",
            "Fire" to "right trigger or A",
            "Shield" to "left trigger or X",
            "Weapons" to "LB / RB",
        )
        pad -> options.controls.getOrNull(place.coerceAtMost(1))?.let { joystick ->
            listOf(
                "Drive" to "the stick",
                "Aim" to "hold ${padButtonsText(joystick.buttons(JoystickAction.AIM))}",
                "Fire" to padButtonsText(joystick.buttons(JoystickAction.FIRE)),
                "Shield" to padButtonsText(joystick.buttons(JoystickAction.SHIELD)),
                "Weapons" to padButtonsText(joystick.buttons(JoystickAction.WEAPON)),
            )
        }.orEmpty()
        else -> options.controls.getOrNull(seat.keys ?: return emptyList())?.let { set ->
            listOf(
                "Drive" to keys(set, ControlAction.UP, ControlAction.LEFT, ControlAction.DOWN, ControlAction.RIGHT),
                "Aim" to keys(set, ControlAction.AIM_LEFT, ControlAction.AIM_RIGHT),
                "Fire" to keys(set, ControlAction.FIRE),
                "Shield" to keys(set, ControlAction.SHIELD),
                "Weapons" to keys(set, ControlAction.WEAPON_LEFT, ControlAction.WEAPON_RIGHT),
            )
        }.orEmpty()
    }
}

/** Extended's keys help, from the current settings (the original's KEYS.HLP describes Classic). */
fun extendedKeysText(options: GameOptions): String {
    val one = options.mouseControls
    val two = options.controls[1]
    fun StringBuilder.lines(seat: Seat) {
        for ((label, controls) in seatControls(seat, place = 1, options, arrows = !options.keyboardSecond)) appendLine("$label: $controls")
    }
    return buildString {
        appendLine("PLAYER ONE: KEYBOARD AND MOUSE")
        appendLine()
        lines(Seat(keys = null, pad = null, mouse = true))
        appendLine("Steering: " + if (one.mode == ControlMode.DIRECT) "the vehicle drives the way you press" else "turn and drive, as the original")
        appendLine("Rocket marker: while fire is held it follows the pointer; let go to launch")
        appendLine()
        appendLine("Vehicles without a turret of their own fire straight ahead: aim by driving.")
        appendLine()
        appendLine("GAMEPADS: PLAYERS TWO TO FOUR (press A on the join screen)")
        appendLine()
        if (options.twinSticks) {
            lines(Seat(keys = null, pad = 0))
        } else {
            appendLine("As the original's joystick: the stick steers, with the buttons set in the controls.")
        }
        if (options.keyboardSecond) {
            appendLine()
            appendLine("SECOND KEYBOARD PLAYER (joins with ${keys(two, ControlAction.FIRE)})")
            appendLine()
            lines(Seat(keys = 1, pad = null))
        }
        appendLine()
        appendLine("IN A GAME")
        appendLine()
        appendLine("F1-F10 take over a friendly tank, P pauses (not in a network game), ESC leaves,")
        appendLine("F11 gamma, F12 radar, - and = window size, PrtScr saves a screenshot.")
    }
}

package nl.tankarena.ui.compose.extended

import nl.tankarena.input.Seat
import nl.tankarena.input.keyName
import nl.tankarena.ui.compose.menu.ControlMode
import nl.tankarena.ui.compose.menu.ControlAction
import nl.tankarena.ui.compose.menu.PlayerControls
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import nl.tankarena.ui.compose.menu.GameOptions
import nl.tankarena.ui.compose.menu.TouchSettings
import nl.tankarena.app.LocalTouchScreen
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
        ExtendedSetting.TOUCH_SIZE -> copy(touch = touch.copy(size = along(TouchSettings.SIZES, touch.size)))
        ExtendedSetting.TOUCH_VISIBILITY -> copy(touch = touch.copy(visibility = along(TouchSettings.VISIBILITIES, touch.visibility)))
        ExtendedSetting.TOUCH_HAND -> copy(touch = touch.copy(leftHanded = !touch.leftHanded))
        ExtendedSetting.TOUCH_AIM_FIRES -> copy(touch = touch.copy(aimFires = !touch.aimFires))
        ExtendedSetting.VIBRATION -> copy(touch = touch.copy(vibration = !touch.vibration))
        ExtendedSetting.CONTROLS -> this
    }
}

/** Enter on a setting: the next value, round to the first. */
private fun GameOptions.nextOf(setting: ExtendedSetting): GameOptions {
    fun <T> round(values: List<T>, current: T): T = values[(values.indexOf(current) + 1) % values.size]
    return when (setting) {
        ExtendedSetting.TOUCH_SIZE -> copy(touch = touch.copy(size = round(TouchSettings.SIZES, touch.size)))
        ExtendedSetting.TOUCH_VISIBILITY -> copy(touch = touch.copy(visibility = round(TouchSettings.VISIBILITIES, touch.visibility)))
        else -> stepped(setting, up = true)
    }
}

/** The settings on Extended's settings page, in order; [touchOnly] ones only on a touch screen. */
internal enum class ExtendedSetting(val touchOnly: Boolean = false) {
    CONTROLS, WINDOW_SIZE, SMOOTH_MOTION, PICTURE, WIDE_VIEW, GAMMA, RADAR, MUSIC, SOUND, ROUNDS, LAPS, LOSERS_TANK,
    TOUCH_SIZE(touchOnly = true), TOUCH_VISIBILITY(touchOnly = true), TOUCH_HAND(touchOnly = true),
    TOUCH_AIM_FIRES(touchOnly = true), VIBRATION(touchOnly = true),
}

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
    touch: Boolean = LocalTouchScreen.current,
) {
    val keys = remember { KeyRouter() }
    val shown = ExtendedSetting.entries.filter { touch || !it.touchOnly }
    val entries: List<Pair<ListEntry, () -> Unit>> = shown.map { setting ->
        val (entry, pick) = when (setting) {
            ExtendedSetting.CONTROLS -> ListEntry("Controls", "keyboard and mouse, gamepads") to onControls
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
            ExtendedSetting.TOUCH_SIZE -> ListEntry("Touch controls", "${options.touch.size}% size") to { onChange(options.nextOf(setting)) }
            ExtendedSetting.TOUCH_VISIBILITY -> ListEntry("Touch controls idle", "${options.touch.visibility}% visible") to { onChange(options.nextOf(setting)) }
            ExtendedSetting.TOUCH_HAND -> ListEntry("Aiming hand", if (options.touch.leftHanded) "left" else "right") to { onChange(options.nextOf(setting)) }
            ExtendedSetting.TOUCH_AIM_FIRES -> ListEntry("Aim stick", if (options.touch.aimFires) "fires when pushed far" else "aims only") to {
                onChange(options.nextOf(setting))
            }
            ExtendedSetting.VIBRATION -> ListEntry("Vibration", if (options.touch.vibration) "on firing and hits" else "off") to { onChange(options.nextOf(setting)) }
        }
        // Every setting but the controls has its < > for the mouse and touch.
        entry.copy(steppable = setting != ExtendedSetting.CONTROLS) to pick
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
            onStep = { index, up -> onChange(options.stepped(shown[index], up)) },
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
 * player's place (an original joystick's buttons are those of player one's or two's settings).
 */
fun seatControls(seat: Seat, place: Int, options: GameOptions): List<Pair<String, String>> {
    return when {
        seat is Seat.Own -> options.mouseControls.let { one ->
            listOf(
                "Drive" to keys(one, ControlAction.UP, ControlAction.LEFT, ControlAction.DOWN, ControlAction.RIGHT) + " or the arrow keys",
                "Aim" to "the mouse pointer, or ${keys(one, ControlAction.AIM_LEFT, ControlAction.AIM_RIGHT)}",
                "Fire" to "left mouse button or ${keys(one, ControlAction.FIRE)}",
                "Shield" to "right mouse button or ${keys(one, ControlAction.SHIELD)}",
                "Weapons" to "mouse wheel or ${keys(one, ControlAction.WEAPON_LEFT, ControlAction.WEAPON_RIGHT)}",
                "Next vehicle" to keys(one, ControlAction.NEXT_VEHICLE),
            )
        }
        options.twinSticks -> listOf(
            "Drive" to "left stick",
            "Aim" to "right stick",
            "Fire" to "right trigger or A",
            "Shield" to "left trigger or X",
            "Weapons" to "LB / RB",
            "Next vehicle" to "Y",
        )
        else -> options.controls.getOrNull(place.coerceAtMost(1))?.let { joystick ->
            listOf(
                "Drive" to "the stick",
                "Aim" to "hold ${padButtonsText(joystick.buttons(JoystickAction.AIM))}",
                "Fire" to padButtonsText(joystick.buttons(JoystickAction.FIRE)),
                "Shield" to padButtonsText(joystick.buttons(JoystickAction.SHIELD)),
                "Weapons" to padButtonsText(joystick.buttons(JoystickAction.WEAPON)),
                "Next vehicle" to "BACK",
            )
        }.orEmpty()
    }
}

/** Extended's keys help, from the current settings (the original's KEYS.HLP describes Classic). */
fun extendedKeysText(options: GameOptions): String {
    val one = options.mouseControls
    fun StringBuilder.lines(seat: Seat) {
        for ((label, controls) in seatControls(seat, place = 1, options)) appendLine("$label: $controls")
    }
    return buildString {
        appendLine("PLAYER ONE: KEYBOARD AND MOUSE")
        appendLine()
        lines(Seat.Own())
        appendLine("Steering: " + if (one.mode == ControlMode.DIRECT) "the vehicle drives the way you press" else "turn and drive, as the original")
        appendLine("Rocket marker: while fire is held it follows the pointer; let go to launch")
        appendLine()
        appendLine("Vehicles without a turret of their own fire straight ahead: aim by driving.")
        appendLine()
        appendLine("GAMEPADS: PLAYERS TWO TO FOUR (press A on the join screen)")
        appendLine()
        if (options.twinSticks) {
            lines(Seat.Pad(0))
        } else {
            appendLine("As the original's joystick: the stick steers, with the buttons set in the controls.")
        }
        appendLine()
        appendLine("IN A GAME")
        appendLine()
        appendLine("Next vehicle (or F1-F10 by its number) takes over a friendly tank; not against each other.")
        appendLine("P pauses (not in a network game), ESC leaves, F11 gamma, F12 radar, - and = window size,")
        appendLine("PrtScr saves a screenshot.")
    }
}

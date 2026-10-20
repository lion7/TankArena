package com.tankarena.ui.compose.extended

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tankarena.ui.compose.menu.GameOptions

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
            ExtendedSetting.CONTROLS -> ListEntry("Controls", "keys and steering for both players") to onControls
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

package com.tankarena.ui.compose.extended

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tankarena.ui.compose.menu.GameOptions

private fun volumeText(volume: Int) = listOf("off", "low", "medium", "high")[(volume / 85).coerceIn(0, 3)]

/**
 * Extended settings on one page: every entry shows its value and Enter steps through the choices.
 * Controls opens the controls setup; the last entry switches the game to Classic.
 */
@Composable
fun ExtendedSettingsPage(
    options: GameOptions,
    onChange: (GameOptions) -> Unit,
    onControls: () -> Unit,
    onClassic: () -> Unit,
    onBack: () -> Unit,
) {
    val keys = remember { KeyRouter() }
    val entries: List<Pair<ListEntry, () -> Unit>> = listOf(
        ListEntry("Controls", "keys and steering for both players") to onControls,
        ListEntry("Window size", "${100 - options.windowSize * 5}%") to {
            onChange(if (options.windowSize >= GameOptions.MAX_WINDOW_SIZE) options.copy(windowSize = 0) else options.smallerWindow())
        },
        ListEntry("Gamma correction", options.gamma.toString()) to { onChange(options.nextGamma()) },
        ListEntry("Radar", if (options.showRadar) "on" else "off") to { onChange(options.copy(showRadar = !options.showRadar)) },
        ListEntry("Music", volumeText(options.musicVolume)) to { onChange(options.nextMusicVolume()) },
        ListEntry("Sound", volumeText(options.soundVolume)) to { onChange(options.nextSoundVolume()) },
        ListEntry("Rounds in a match", if (options.rounds == GameOptions.ENDLESS) "endless" else options.rounds.toString()) to {
            onChange(options.nextRounds())
        },
        ListEntry("Race laps", options.raceLaps.toString()) to { onChange(options.nextLaps()) },
        ListEntry("Losers tank", listOf("off", "after 1 lost round", "after 3 lost rounds")[options.losersTank.coerceIn(0, 2)]) to {
            onChange(options.nextLosersTank())
        },
        ListEntry("Switch to Classic", "the original game and its menus") to onClassic,
    )
    ExtendedPage(
        title = "Settings",
        onBack = onBack,
        footer = "Enter changes a setting; settings are kept for next time",
        onKey = keys::handle,
    ) {
        ExtendedList(entries.map { it.first }, onPick = { entries[it].second() }, keys = keys)
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

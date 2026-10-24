package nl.tankarena.ui.compose.extended

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.tankarena.input.Seat
import nl.tankarena.input.keyName
import nl.tankarena.ui.compose.menu.ControlAction
import nl.tankarena.ui.compose.menu.PlayerControls
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Spacer

/**
 * "Press to join" (ADR 0026): players take the free places in order, each with a key set (by pressing
 * its fire key) or a gamepad (by pressing A). A key set or pad joins once.
 */
class JoinState(
    val maxPlayers: Int,
    /** A place taken from the start (Extended's keyboard-and-mouse player one), never left. */
    private val first: Seat? = null,
) {
    var seats: List<Seat> = listOfNotNull(first)
        private set

    /** The key set whose fire key is [key] joins, if it is one of [allowed]; true when it did. */
    fun key(key: Key, keySets: List<PlayerControls>, allowed: Set<Int> = keySets.indices.toSet()): Boolean {
        val set = keySets.indexOfFirst { it.key(ControlAction.FIRE) == key }.takeIf { it >= 0 && it in allowed } ?: return false
        return join(Seat(keys = set, pad = null)) { it.keys == set }
    }

    /** Pad [index] joins; true when it did. */
    fun pad(index: Int): Boolean = join(Seat(keys = null, pad = index)) { it.pad == index }

    /** The last one to join leaves again. */
    fun leave() {
        if (seats.size > (if (first != null) 1 else 0)) seats = seats.dropLast(1)
    }

    private fun join(seat: Seat, taken: (Seat) -> Boolean): Boolean {
        if (seats.size >= maxPlayers || seats.any(taken)) return false
        seats = seats + seat
        return true
    }
}

/**
 * The join screen before a local game: [minPlayers] to [maxPlayers] places; [readPadButtons] gives
 * each connected pad's buttons. A on a pad or a key set's fire key joins, B or DELETE takes the last
 * one out again, START or ENTER begins once there are enough, ESC goes back.
 */
@Composable
fun JoinScreen(
    title: String,
    minPlayers: Int,
    maxPlayers: Int,
    keySets: List<PlayerControls>,
    readPadButtons: () -> List<Int>,
    onStart: (List<Seat>) -> Unit,
    onBack: () -> Unit,
    /** Player one's place, taken from the start (Extended: keyboard and mouse). */
    first: Seat? = null,
    /** The key sets that may join with their fire key. */
    keyboardSets: Set<Int> = keySets.indices.toSet(),
    /** What the player in a place plays with, as label and controls, given everyone who plays. */
    controlsOf: (place: Int, seat: Seat, seats: List<Seat>) -> List<Pair<String, String>> = { _, _, _ -> emptyList() },
) {
    val state = remember { JoinState(maxPlayers, first) }
    var seats by remember { mutableStateOf(state.seats) }
    fun begin() {
        if (seats.size >= minPlayers) onStart(seats)
    }
    // The pads' buttons, as they go down: A joins, B takes the last one out, START begins.
    LaunchedEffect(Unit) {
        var last = emptyList<Int>()
        while (true) {
            val now = withFrameNanos { readPadButtons() }
            for ((index, buttons) in now.withIndex()) {
                val pressed = buttons and (last.getOrElse(index) { 0 }).inv()
                when {
                    pressed and 1 != 0 -> state.pad(index)
                    pressed and 2 != 0 -> state.leave()
                    pressed and (1 shl 7) != 0 -> begin()
                }
            }
            last = now
            seats = state.seats
        }
    }
    ExtendedPage(
        title = title,
        subtitle = when {
            keyboardSets.isEmpty() -> "Press A on a gamepad to join"
            first != null -> "Press A on a gamepad to join, or ${keySets.getOrNull(keyboardSets.first())?.key(ControlAction.FIRE)?.let { keyName(it) } ?: "fire"} for the second keyboard player"
            else -> "Press fire on your keys or A on a gamepad to join"
        },
        onBack = onBack,
        footer = if (seats.size >= minPlayers) "Enter or START begins · Delete or B takes the last one out" else "At least $minPlayers players",
        actions = listOfNotNull(
            PageAction("Begin") { begin() }.takeIf { seats.size >= minPlayers },
            PageAction("Take out") {
                state.leave()
                seats = state.seats
            }.takeIf { seats.size > (if (first != null) 1 else 0) },
        ),
        onKey = { event ->
            when (event.key) {
                Key.Enter, Key.NumPadEnter -> begin()
                Key.Delete, Key.Backspace -> state.leave()
                else -> if (!state.key(event.key, keySets, keyboardSets)) return@ExtendedPage false
            }
            seats = state.seats
            true
        },
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) { PlaceBoxes(maxPlayers) { place ->
            val seat = seats.getOrNull(place)
            RetroText("Player ${place + 1}", color = if (seat != null) RetroColors.high else RetroColors.normal, maxLines = 1)
            val text = when {
                seat == null -> if (place < minPlayers) "waiting…" else "free"
                seat.mouse -> "keyboard and mouse"
                seat.pad != null -> "gamepad ${seat.pad + 1}"
                else -> "keys of player ${seat.keys!! + 1} (fire: ${keySets[seat.keys].key(ControlAction.FIRE)?.let { keyName(it) } ?: "-"})"
            }
            RetroText(text, color = if (seat != null) RetroColors.text else RetroColors.dim)
            // The bottom half: what this player plays with.
            if (seat != null) {
                Spacer(Modifier.height(retroDp(4)))
                for ((label, controls) in controlsOf(place, seat, seats)) {
                    RetroText(label, color = RetroColors.dim, font = RetroFont.SMALL)
                    RetroText(controls, color = RetroColors.text)
                }
            }
        } }
    }
}

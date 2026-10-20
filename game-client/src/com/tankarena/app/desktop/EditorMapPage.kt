package com.tankarena.app.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tankarena.content.GameModeCompatibility
import com.tankarena.sim.map.MapEditor
import com.tankarena.sim.map.MapSettings

/** Legacy `view_file`: lines are shown as they are, and 51 characters fit across. */
private const val TEXT_COLUMNS = 51

private val TYPE_LABELS = mapOf(
    GameModeCompatibility.DONT_CARE to "any mode",
    GameModeCompatibility.SINGLE to "one player",
    GameModeCompatibility.DUAL to "two players against each other",
    GameModeCompatibility.DUAL_VS_COMPUTER to "two players together",
    GameModeCompatibility.SINGLE_OR_DUAL to "one or two players against the computer",
)

/** What the start pads and flags give room for (legacy MAPC_*). */
private fun contentsText(contents: Int): String = buildList {
    if (contents and 1 != 0) add("tanks")
    if (contents and 2 != 0) add("helicopters")
    if (contents and 4 != 0) add("planes")
    if (contents and 8 != 0) add("a race")
    if (contents and 16 != 0) add("a flag hunt")
}.joinToString(", ").ifEmpty { "nothing yet (no start pads or flags)" }

/**
 * The map's header (the original editor's SPECIAL menu: codes, mode, public code, day or night, random
 * bonuses) and its three texts, which the original took from `MAPS/name.0..2` files. [onTyping] tells
 * the editor a text field has the keyboard, so its one-key shortcuts stay out of the way.
 */
@Composable
internal fun EditorMapPage(editor: MapEditor, revision: Int, onChange: () -> Unit, onTyping: (Boolean) -> Unit) {
    val settings = revision.let { editor.settings }
    val texts = editor.texts
    fun change(new: MapSettings) {
        editor.changeSettings(new)
        onChange()
    }
    val typing = Modifier.onFocusChanged { onTyping(it.isFocused) }
    Row(modifier = Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(
            modifier = Modifier.width(420.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Map", color = Color(0xFFE0B04A), fontSize = 20.sp)
            OutlinedTextField(
                value = settings.code,
                onValueChange = { change(settings.copy(code = it.uppercase().take(MapSettings.CODE_LENGTH))) },
                label = { Text("Code of this mission (empty: the map's name)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().then(typing),
            )
            OutlinedTextField(
                value = settings.next,
                onValueChange = { change(settings.copy(next = it.uppercase().take(MapSettings.CODE_LENGTH))) },
                label = { Text("Code of the next mission") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().then(typing),
            )
            Text("Played by", color = Color.White)
            for ((type, label) in TYPE_LABELS) {
                FilterChip(selected = settings.type == type, onClick = { change(settings.copy(type = type)) }, label = { Text(label) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = settings.publicCode, onClick = { change(settings.copy(publicCode = !settings.publicCode)) }, label = { Text("code public") })
                FilterChip(selected = settings.night, onClick = { change(settings.copy(night = !settings.night)) }, label = { Text("night") })
                FilterChip(selected = settings.randomBonus, onClick = { change(settings.copy(randomBonus = !settings.randomBonus)) }, label = { Text("random bonuses") })
            }
            Text("Room for: ${contentsText(editor.contents)}", color = Color.LightGray, fontSize = 13.sp)
            if (editor.startConflict) {
                Text("Start point conflict: race pads go with no other pads and no flags.", color = Color(0xFFFF8080), fontSize = 13.sp)
            }
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Texts", color = Color(0xFFE0B04A), fontSize = 20.sp)
            for ((label, text, with) in listOf(
                Triple("Briefing", texts.briefing) { value: String -> texts.copy(briefing = value) },
                Triple("After a won mission", texts.success) { value: String -> texts.copy(success = value) },
                Triple("After a lost mission", texts.failure) { value: String -> texts.copy(failure = value) },
            )) {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        editor.changeTexts(with(it))
                        onChange()
                    },
                    label = { Text(label) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    modifier = Modifier.fillMaxWidth().height(170.dp).then(typing),
                )
                val long = text.lines().withIndex().filter { it.value.length > TEXT_COLUMNS }.map { it.index + 1 }
                if (long.isNotEmpty()) {
                    Text(
                        "Line ${long.joinToString(", ")} is longer than $TEXT_COLUMNS characters; the rest does not show.",
                        color = Color(0xFFFF8080),
                        fontSize = 12.sp,
                    )
                }
            }
            Text("Shown in capitals, line by line, as the original does.", color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.align(Alignment.Start))
        }
    }
}

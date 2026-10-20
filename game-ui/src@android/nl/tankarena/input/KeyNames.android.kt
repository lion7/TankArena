package nl.tankarena.input

import android.view.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.nativeKeyCode

actual fun keyName(key: Key): String =
    KeyEvent.keyCodeToString(key.nativeKeyCode).removePrefix("KEYCODE_").lowercase().split('_')
        .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }

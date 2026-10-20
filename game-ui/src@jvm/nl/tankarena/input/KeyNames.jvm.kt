package nl.tankarena.input

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.nativeKeyCode

actual fun keyName(key: Key): String = java.awt.event.KeyEvent.getKeyText(key.nativeKeyCode)

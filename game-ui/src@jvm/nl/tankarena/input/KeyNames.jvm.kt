package nl.tankarena.input

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.nativeKeyCode

actual fun keyName(key: Key): String = java.awt.event.KeyEvent.getKeyText(key.nativeKeyCode)

private val blankCursor: androidx.compose.ui.input.pointer.PointerIcon by lazy {
    val image = java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB)
    androidx.compose.ui.input.pointer.PointerIcon(java.awt.Toolkit.getDefaultToolkit().createCustomCursor(image, java.awt.Point(0, 0), "none"))
}

actual fun hiddenPointerIcon(): androidx.compose.ui.input.pointer.PointerIcon = blankCursor

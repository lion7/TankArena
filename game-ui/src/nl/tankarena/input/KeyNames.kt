package nl.tankarena.input

import androidx.compose.ui.input.key.Key

/** A key's name as the platform calls it ("Page Down", "K", ...). */
expect fun keyName(key: Key): String

/** A mouse cursor that is not drawn: Extended's game draws its own crosshair. */
expect fun hiddenPointerIcon(): androidx.compose.ui.input.pointer.PointerIcon

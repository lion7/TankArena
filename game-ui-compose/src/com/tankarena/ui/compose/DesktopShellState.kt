package com.tankarena.ui.compose

enum class DesktopShellScreen {
    TITLE,
    PLAYING,
    EDITOR,
}

data class DesktopShellState(
    val screen: DesktopShellScreen = DesktopShellScreen.TITLE,
)


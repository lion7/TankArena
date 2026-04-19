package com.tankarena.ui.compose.menu

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

object RetroColors {
    val Background = Color(0xFF0B1B2C)
    val PanelFill = Color(0xFF000000)
    val PanelBorderOuter = Color(0xFF1FA8FF)
    val PanelBorderInner = Color(0xFF0E5C99)
    val Title = Color(0xFFE6E6E6)
    val ItemSelected = Color(0xFFFFFFFF)
    val ItemIdle = Color(0xFF6F7DA0)
    val ItemDisabled = Color(0xFF3F4762)
    val Subtitle = Color(0xFF8FA2C8)
    val Footer = Color(0xFF6F7DA0)
}

object RetroTypography {
    val Title: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = 2.sp,
    )

    val Item: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = 1.sp,
    )

    val Subtitle: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
    )

    val Footer: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 1.sp,
    )
}

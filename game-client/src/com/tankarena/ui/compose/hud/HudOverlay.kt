package com.tankarena.ui.compose.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tankarena.core.FixedStepClock
import com.tankarena.protocol.snapshot.HudState

/**
 * Top-of-arena HUD overlay matching the legacy strip:
 *  ARMOR FUEL    MISSION TANK
 *   xx   xx       x       x
 *
 * Pure function of authoritative replicated HUD data; safe to recompose every tick.
 */
@Composable
fun HudOverlay(
    hudState: HudState,
    modifier: Modifier = Modifier,
    /** Split-screen halves drop the match-wide gauges (score, kills, time, code). */
    compact: Boolean = false,
) {
    Row(
        modifier = modifier
            .background(HudColors.Background)
            .border(width = 1.dp, color = HudColors.Border, shape = RectangleShape)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HudGauge(label = "ARMOR", value = percent(hudState.armor, hudState.maxArmor))
        Spacer(modifier = Modifier.width(8.dp))
        HudGauge(label = "FUEL", value = percent(hudState.fuel, hudState.maxFuel))
        Spacer(modifier = Modifier.width(16.dp))
        // Legacy status bar: good and evil goal counters side by side.
        HudGauge(label = "GOAL", value = "${hudState.missionProgress}/${hudState.missionProgressBad}")
        Spacer(modifier = Modifier.width(8.dp))
        HudGauge(label = "TANK", value = hudState.lives.toString())
        Spacer(modifier = Modifier.width(16.dp))
        HudGauge(label = weaponLabel(hudState.currentWeapon), value = weaponAmmo(hudState))
        if (!compact) {
            Spacer(modifier = Modifier.width(16.dp))
            HudGauge(label = "SCORE", value = hudState.score.toString())
            Spacer(modifier = Modifier.width(8.dp))
            HudGauge(label = "KILLS", value = hudState.kills.toString())
            Spacer(modifier = Modifier.width(8.dp))
            HudGauge(label = "TIME", value = formatTime(hudState.time))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = hudState.missionCode,
                style = HudTypography.Small,
                color = HudColors.Subtitle,
            )
        }
    }
}

/** Legacy meters show percentages of the vehicle's maximum. */
private fun percent(value: Int, max: Int): String {
    if (max <= 0) return value.coerceAtLeast(0).toString()
    return "${(value.coerceAtLeast(0) * 100 / max).coerceIn(0, 100)}%"
}

/** Format tick count as MM:SS. */
private fun formatTime(ticks: Long): String {
    val seconds = ticks / FixedStepClock.TICKS_PER_SECOND
    val minutes = seconds / 60
    val secs = seconds % 60
    return "${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
}

/** Legacy weapon slots (src/tanks/fire.c). */
private fun weaponLabel(weapon: Int): String = when (weapon) {
    0 -> "MAIN"
    1 -> "CHAIN"
    2 -> "FLAME"
    3 -> "MINE"
    4 -> "ROCKET"
    5 -> "MORTAR"
    6 -> "A-BOMB"
    7 -> "GUNNERS"
    8 -> "FLAMERS"
    9 -> "SMOKE"
    10 -> "CLOAK"
    11 -> "SPEED"
    else -> "WPN"
}

private fun weaponAmmo(hud: HudState): String = when (hud.currentWeapon) {
    1 -> hud.chainAmmo.toString()
    3 -> hud.mineAmmo.toString()
    4 -> hud.rocketAmmo.toString()
    5 -> hud.mortarAmmo.toString()
    else -> "--"
}

@Composable
private fun HudGauge(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = label, style = HudTypography.Label, color = HudColors.Label)
        Text(text = value, style = HudTypography.Value, color = HudColors.Value)
    }
}

private object HudColors {
    val Background: Color = Color(0xCC000000)
    val Border: Color = Color(0xFF1FA8FF)
    val Label: Color = Color(0xFFCBD9F0)
    val Value: Color = Color(0xFFFFFFFF)
    val Subtitle: Color = Color(0xFF8FA2C8)
}

private object HudTypography {
    val Label: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 1.sp,
    )
    val Value: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        letterSpacing = 1.sp,
    )
    val Small: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
    )
}

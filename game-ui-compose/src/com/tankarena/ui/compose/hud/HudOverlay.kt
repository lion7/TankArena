package com.tankarena.ui.compose.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import com.tankarena.sim.WorldState

/**
 * Top-of-arena HUD overlay matching the legacy strip:
 *  ARMOR FUEL    MISSION TANK
 *   xx   xx       x       x
 *
 * Pure function of [WorldState] plus the active mission code; safe to recompose every tick.
 */
@Composable
fun HudOverlay(
    world: WorldState,
    missionCode: String,
    modifier: Modifier = Modifier,
    playerIndex: Int = 0,
) {
    val player = world.tanks.firstOrNull { it.playerIndex == playerIndex }
    val armor = player?.armor ?: 0
    val fuel = player?.fuel ?: 0
    val livesRemaining = player?.lives ?: 0
    val missionGoal = world.mission.goalGood

    Row(
        modifier = modifier
            .background(HudColors.Background)
            .border(width = 1.dp, color = HudColors.Border, shape = RectangleShape)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HudGauge(label = "ARMOR", value = armor.coerceAtLeast(0).toString().padStart(2, '0'))
        Spacer(modifier = Modifier.width(8.dp))
        HudGauge(label = "FUEL", value = fuel.coerceAtLeast(0).toString().padStart(2, '0'))
        Spacer(modifier = Modifier.width(20.dp))
        HudGauge(label = "MISSION", value = missionGoal.toString())
        Spacer(modifier = Modifier.width(8.dp))
        HudGauge(label = "TANK", value = livesRemaining.toString())
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = missionCode,
            style = HudTypography.Small,
            color = HudColors.Subtitle,
        )
    }
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

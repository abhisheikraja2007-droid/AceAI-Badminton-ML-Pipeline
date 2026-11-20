package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CarbonBlack
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberCyanSubtle
import com.example.ui.theme.FaultCrimson
import com.example.ui.theme.FaultCrimsonSubtle
import com.example.ui.theme.HudContainer
import com.example.ui.theme.NeonLimeSubtle
import com.example.ui.theme.OpticNeonLime
import com.example.ui.theme.OutlineMuted
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Primary Kinetic Trigger Button: Background #CCFF00, text #0B0F19, 4px corner radius.
 */
@Composable
fun PrimaryKineticButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tag: String = "primary_kinetic_button"
) {
    Box(
        modifier = modifier
            .testTag(tag)
            .clip(RoundedCornerShape(4.dp))
            .background(if (enabled) OpticNeonLime else OutlineMuted)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            color = CarbonBlack,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.05.sp
        )
    }
}

/**
 * Secondary HUD Action: Background rgba(19, 27, 46, 0.85), border 1px solid #00E5FF, text #00E5FF.
 */
@Composable
fun SecondaryHudButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    tag: String = "secondary_hud_button"
) {
    val borderColor = if (active) OpticNeonLime else CyberCyan
    val textColor = if (active) OpticNeonLime else CyberCyan
    Box(
        modifier = modifier
            .testTag(tag)
            .clip(RoundedCornerShape(4.dp))
            .background(HudContainer.copy(alpha = 0.85f))
            .border(if (active) 2.dp else 1.dp, borderColor, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.04.sp
        )
    }
}

/**
 * Fault / Kill Trigger Button: Transparent, border 1px solid #FF3366, text #FF3366.
 */
@Composable
fun FaultTriggerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String = "fault_trigger_button"
) {
    Box(
        modifier = modifier
            .testTag(tag)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Transparent)
            .border(1.dp, FaultCrimson, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            color = FaultCrimson,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.04.sp
        )
    }
}

/**
 * Edge Diagnostic Slot: 1px dashed #00E5FF border, background #131B2E (85% alpha).
 */
@Composable
fun EdgeDiagnosticSlot(
    title: String,
    token: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    isNominal: Boolean = true,
    isFault: Boolean = false
) {
    val borderColor = if (isFault) FaultCrimson else CyberCyan
    val statusColor = if (isFault) FaultCrimson else if (isNominal) OpticNeonLime else CyberCyan

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(HudContainer.copy(alpha = 0.85f))
            .border(
                width = 1.dp,
                color = borderColor.copy(alpha = if (isFault) alphaAnim else 0.85f),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(10.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title.uppercase(),
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.08.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = token,
                        color = TextMuted.copy(alpha = 0.6f),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(statusColor)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = value,
                    color = if (isFault) FaultCrimson else TextPrimary,
                    fontSize = 26.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.02).sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = unit.uppercase(),
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
    }
}

/**
 * Nominal, Kinematic, and Fault Status Chips
 */
@Composable
fun TelemetryStatusChip(
    text: String,
    type: ChipType,
    modifier: Modifier = Modifier
) {
    val (bg, fg, border) = when (type) {
        ChipType.NOMINAL -> Triple(NeonLimeSubtle, OpticNeonLime, OpticNeonLime)
        ChipType.SPATIAL -> Triple(CyberCyanSubtle, CyberCyan, CyberCyan)
        ChipType.FAULT -> Triple(FaultCrimsonSubtle, FaultCrimson, FaultCrimson)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(2.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text.uppercase(),
            color = fg,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.05.sp
        )
    }
}

enum class ChipType {
    NOMINAL,
    SPATIAL,
    FAULT
}

/**
 * Telemetry Pair Row: Border bottom 1px #1E293B, height 36px, zero internal vertical padding.
 */
@Composable
fun TelemetryPairRow(
    label: String,
    value: String,
    unit: String = "",
    isHighlight: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .border(width = 0.dp, color = Color.Transparent)
            .background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = TextMuted,
                fontSize = 12.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Normal
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = value,
                    color = if (isHighlight) OpticNeonLime else TextPrimary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                if (unit.isNotBlank()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = unit.uppercase(),
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
        // Bottom divider 1px #1E293B
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(OutlineMuted)
                .align(Alignment.BottomCenter)
        )
    }
}

/**
 * Sliders (Angle Tolerance Adjuster): Track height 2px #1E293B, active #00E5FF, thumb 12x12px square #CCFF00
 */
@Composable
fun HudToleranceSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 5f..35f,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ANGLE TOLERANCE ADJ",
                color = TextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "±${value.toInt()}° DEG",
                color = OpticNeonLime,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = OpticNeonLime,
                activeTrackColor = CyberCyan,
                inactiveTrackColor = OutlineMuted
            ),
            modifier = Modifier.testTag("hud_tolerance_slider")
        )
    }
}

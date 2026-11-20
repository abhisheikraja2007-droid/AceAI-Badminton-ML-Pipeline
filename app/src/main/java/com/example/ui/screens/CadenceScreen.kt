package com.example.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AceAiViewModel
import com.example.ui.components.ChipType
import com.example.ui.components.EdgeDiagnosticSlot
import com.example.ui.components.PrimaryKineticButton
import com.example.ui.components.SecondaryHudButton
import com.example.ui.components.TelemetryPairRow
import com.example.ui.components.TelemetryStatusChip
import com.example.ui.theme.CarbonBlack
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.HudContainer
import com.example.ui.theme.OpticNeonLime
import com.example.ui.theme.OutlineMuted
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun CadenceScreen(
    viewModel: AceAiViewModel,
    modifier: Modifier = Modifier
) {
    val bpm by viewModel.bpm.collectAsState()
    val isPlaying by viewModel.isPlayingBeat.collectAsState()

    val presets = listOf(
        Pair("6-Corner Footwork Split Step", 135),
        Pair("Smash-and-Rush Net Kill", 150),
        Pair("Baseline Defensive Recovery", 120),
        Pair("Elite Shadow Speed Drills", 165)
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isPlaying) 1.25f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween((60_000 / bpm).coerceAtLeast(200)),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CarbonBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. Header Array ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "ACEAI // CADENCE PACER",
                        color = OpticNeonLime,
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.05.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TelemetryStatusChip(
                        text = if (isPlaying) "BEAT: ACTIVE" else "MUTED",
                        type = if (isPlaying) ChipType.NOMINAL else ChipType.SPATIAL
                    )
                }
                Text(
                    text = "FOOTWORK SPLIT-STEP AUDIO TEMPO GENERATOR",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = "Cadence Audio",
                tint = CyberCyan,
                modifier = Modifier.size(24.dp)
            )
        }

        // --- 2. Central Pulse Visualizer ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(HudContainer.copy(alpha = 0.85f))
                .border(1.dp, if (isPlaying) OpticNeonLime else OutlineMuted, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            // Pulsing Outer Rings
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(if (isPlaying) OpticNeonLime.copy(alpha = 0.15f) else Color.Transparent)
                    .border(1.5.dp, if (isPlaying) OpticNeonLime else OutlineMuted, CircleShape)
            )

            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(if (isPlaying) CyberCyan.copy(alpha = 0.2f) else HudContainer)
                    .border(2.dp, if (isPlaying) CyberCyan else OutlineMuted, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$bpm",
                        color = if (isPlaying) OpticNeonLime else TextPrimary,
                        fontSize = 28.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "BPM",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Status Tag
            Text(
                text = if (isPlaying) "TEMPO LOCK: ${(60_000 / bpm)} MS PER STEP" else "AUDIO SYNTHESIZER STANDBY",
                color = if (isPlaying) CyberCyan else TextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            )
        }

        // --- 3. Play / Stop Control ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PrimaryKineticButton(
                text = if (isPlaying) "STOP AUDIO PACER" else "START AUDIO PACER",
                onClick = { viewModel.toggleCadenceBeat() },
                modifier = Modifier.weight(1f),
                tag = "toggle_cadence_btn"
            )
        }

        // --- 4. BPM Slider ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(HudContainer)
                .border(1.dp, OutlineMuted, RoundedCornerShape(4.dp))
                .padding(14.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TEMPO VELOCITY SELECTOR",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$bpm BPM",
                        color = OpticNeonLime,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Slider(
                    value = bpm.toFloat(),
                    onValueChange = { viewModel.setBpm(it.toInt()) },
                    valueRange = 80f..200f,
                    colors = SliderDefaults.colors(
                        thumbColor = OpticNeonLime,
                        activeTrackColor = CyberCyan,
                        inactiveTrackColor = OutlineMuted
                    ),
                    modifier = Modifier.testTag("bpm_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("80 BPM (Recovery)", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    Text("140 BPM (Match Pace)", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    Text("200 BPM (Peak Burst)", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        // --- 5. Drill Presets ---
        Text(
            text = "ATHLETIC DRILL CADENCE PRESETS",
            color = OpticNeonLime,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.05.sp
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { (title, presetBpm) ->
                val isSelected = (bpm == presetBpm)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) HudContainer.copy(alpha = 0.95f) else HudContainer)
                        .border(1.dp, if (isSelected) CyberCyan else OutlineMuted, RoundedCornerShape(4.dp))
                        .clickable {
                            viewModel.setBpm(presetBpm)
                        }
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = title.uppercase(),
                                color = if (isSelected) OpticNeonLime else TextPrimary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Timed footwork cadence at $presetBpm steps/min",
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                        }

                        TelemetryStatusChip(
                            text = "$presetBpm BPM",
                            type = if (isSelected) ChipType.NOMINAL else ChipType.SPATIAL
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

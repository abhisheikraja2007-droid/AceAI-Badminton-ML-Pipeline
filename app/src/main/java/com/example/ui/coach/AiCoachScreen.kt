package com.example.ui.coach

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.domain.model.KineticAdjustment
import com.example.domain.viewmodel.CoachChatViewModel
import com.example.ui.theme.CarbonBlack
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.FaultCrimson
import com.example.ui.theme.HudContainer
import com.example.ui.theme.OpticNeonLime
import com.example.ui.theme.OutlineMuted
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiCoachScreen(
    sessionId: String = "active_court_session",
    viewModel: CoachChatViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val analysis by viewModel.analysisState.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()

    LaunchedEffect(sessionId) {
        viewModel.loadDiagnosticsForSession(sessionId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "ACEAI // GEMINI 2.5 FLASH",
                            color = OpticNeonLime,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Biomechanical Diagnostics",
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadDiagnosticsForSession(sessionId) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Re-analyze", tint = CyberCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HudContainer)
            )
        },
        containerColor = CarbonBlack
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isAnalyzing && analysis == null) {
                // Loading Shimmer / Spinner
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = OpticNeonLime, strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Synthesizing Kinematic Telemetry via Gemini...",
                        color = TextMuted,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                analysis?.let { report ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. Biomechanical Grade Banner
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = HudContainer),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, OutlineMuted, RoundedCornerShape(16.dp))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "BIOMECHANICAL GRADE",
                                            color = TextMuted,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = report.primaryFlawDetected,
                                            color = TextPrimary,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }

                                    val gradeColor = when (report.biomechanicalGrade.uppercase()) {
                                        "S" -> OpticNeonLime
                                        "A" -> CyberCyan
                                        "B" -> Color(0xFFFFD600)
                                        else -> FaultCrimson
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .background(gradeColor.copy(alpha = 0.15f))
                                            .border(2.dp, gradeColor, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = report.biomechanicalGrade,
                                            color = gradeColor,
                                            fontSize = 32.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Physics & Kinematic Chain Explanation
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = HudContainer),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, OutlineMuted, RoundedCornerShape(14.dp))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "PHYSICS EXPLANATION",
                                        color = CyberCyan,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = report.physicsExplanation,
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                        }

                        // 3. Kinetic Chain Joint Adjustments
                        item {
                            Text(
                                text = "KINETIC CHAIN ADJUSTMENTS",
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        items(report.kineticChainAdjustments) { adjustment ->
                            JointAdjustmentCard(adjustment)
                        }

                        // 4. Recommended Court Drill
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = HudContainer),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, OpticNeonLime.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "RECOMMENDED COURT DRILL",
                                            color = OpticNeonLime,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Icon(
                                            imageVector = Icons.Default.FitnessCenter,
                                            contentDescription = "Drill",
                                            tint = OpticNeonLime,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = report.recommendedCourtDrill.drillName,
                                        color = TextPrimary,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Sets & Reps: ${report.recommendedCourtDrill.setsAndReps}",
                                        color = TextMuted,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "Target Focus: ${report.recommendedCourtDrill.focusMetric}",
                                        color = CyberCyan,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        // 5. Voice Coach Summary & Audio Playback
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = HudContainer),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, OutlineMuted, RoundedCornerShape(14.dp))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "VOICE COACH SUMMARY (<25 WORDS)",
                                            color = CyberCyan,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                        IconButton(
                                            onClick = {
                                                if (isSpeaking) {
                                                    viewModel.stopVoiceSummary()
                                                } else {
                                                    viewModel.playVoiceSummary()
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (isSpeaking) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                                                contentDescription = "Play Voice Summary",
                                                tint = if (isSpeaking) FaultCrimson else OpticNeonLime
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "\"${report.voiceCoachSummary}\"",
                                        color = TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        lineHeight = 22.sp
                                    )
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JointAdjustmentCard(item: KineticAdjustment) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CarbonBlack),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, OutlineMuted, RoundedCornerShape(10.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.jointName.replace("_", " ").uppercase(),
                    color = CyberCyan,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Measured: ${item.measuredValue}° → Target: ${item.targetValue}°",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = item.actionableCue,
                color = TextPrimary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

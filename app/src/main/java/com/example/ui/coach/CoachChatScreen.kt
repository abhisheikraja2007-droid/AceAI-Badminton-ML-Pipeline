package com.example.ui.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.domain.model.SessionSummary
import com.example.domain.viewmodel.CoachChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachChatScreen(
    viewModel: CoachChatViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val analysis by viewModel.analysisState.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadDiagnosticsForSession("active_court_session")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AceAI Biomechanics Coach", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF121212),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF121212)
    ) { paddingValues ->
        if (isAnalyzing) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF00E676))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Calibrating Kinematic Chain...", color = Color.White)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                analysis?.let { report ->
                    // Grade and Flaw Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("BIOMECHANICAL GRADE", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        report.biomechanicalGrade,
                                        color = Color(0xFF00E676),
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(report.primaryFlawDetected, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(report.physicsExplanation, color = Color.LightGray, fontSize = 14.sp)
                            }
                        }
                    }

                    // Kinetic Chain Adjustments
                    item {
                        Text("Kinetic Chain Adjustments", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    items(report.kineticChainAdjustments.size) { index ->
                        val item = report.kineticChainAdjustments[index]
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF242424)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(item.jointName.replace("_", " ").uppercase(), color = Color(0xFF40C4FF), fontWeight = FontWeight.Bold)
                                    Text("Measured: ${item.measuredValue}° → Target: ${item.targetValue}°", color = Color.Gray, fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(item.actionableCue, color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }

                    // Recommended Court Drill
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20).copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("RECOMMENDED COURT DRILL", color = Color(0xFF00E676), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(report.recommendedCourtDrill.drillName, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Sets & Reps: ${report.recommendedCourtDrill.setsAndReps}", color = Color.LightGray, fontSize = 13.sp)
                                Text("Focus Metric: ${report.recommendedCourtDrill.focusMetric}", color = Color.LightGray, fontSize = 13.sp)
                            }
                        }
                    }

                    // Voice Coach Summary
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D47A1).copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("VOICE COACH SUMMARY (<25 WORDS)", color = Color(0xFF40C4FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                                            imageVector = if (isSpeaking) Icons.Default.Stop else Icons.Default.VolumeUp,
                                            contentDescription = if (isSpeaking) "Stop Audio" else "Play Audio",
                                            tint = if (isSpeaking) Color(0xFFFF5252) else Color(0xFF00E676)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("\"${report.voiceCoachSummary}\"", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }
}


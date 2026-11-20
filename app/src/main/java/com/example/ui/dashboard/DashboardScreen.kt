package com.example.ui.dashboard

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Psychology
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
import com.example.domain.model.SmashRecord
import com.example.domain.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToCoach: () -> Unit
) {
    val summary by viewModel.sessionSummary.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadSessionAnalytics("active_court_session", 1800)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Session Biomechanics Analytics", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToCoach) {
                        Icon(Icons.Default.Psychology, contentDescription = "Gemini Coach", tint = Color(0xFF00E676))
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
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF00E676))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Key KPI Summary
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MetricCard("TOTAL SMASHES", "${summary?.totalSmashes ?: 0}", Modifier.weight(1f))
                        MetricCard("MAX SPEED", "${String.format("%.1f", summary?.maxSpeedKmh ?: 0.0)} km/h", Modifier.weight(1f), Color(0xFFFFD600))
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MetricCard("AVG SPEED", "${String.format("%.1f", summary?.avgSpeedKmh ?: 0.0)} km/h", Modifier.weight(1f))
                        MetricCard("AVG ELBOW", "${String.format("%.1f", summary?.avgElbowAngleDeg ?: 0.0)}°", Modifier.weight(1f), Color(0xFF00E676))
                    }
                }

                // AI Diagnostics Trigger CTA
                item {
                    Button(
                        onClick = onNavigateToCoach,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                    ) {
                        Text("Get Gemini AI Biomechanical Diagnostics", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }

                // Fastest Smash Records
                item {
                    Text("Fastest Smashes (Top Kinetic Transfer)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                items(summary?.fastestSmashes ?: emptyList()) { record ->
                    SmashRecordRow(record)
                }
            }
        }
    }
}

@Composable
fun MetricCard(title: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.White) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, color = valueColor, fontSize = 20.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun SmashRecordRow(record: SmashRecord) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${String.format("%.1f", record.wristVelocityKmh)} km/h", color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
            Text("Elbow: ${String.format("%.1f", record.elbowExtensionDeg)}°", color = Color.LightGray, fontSize = 13.sp)
            Text("Shoulder: ${String.format("%.1f", record.shoulderElevationDeg)}°", color = Color.LightGray, fontSize = 13.sp)
        }
    }
}


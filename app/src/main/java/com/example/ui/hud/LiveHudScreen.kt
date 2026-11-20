package com.example.ui.hud

import androidx.camera.view.PreviewView
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.domain.model.PoseFrameResult
import com.example.domain.model.StrokeType
import com.example.domain.viewmodel.LiveHudViewModel
import com.example.ui.components.RealtimePoseSkeletonCanvas

@Composable
fun LiveHudScreen(
    viewModel: LiveHudViewModel = hiltViewModel(),
    onNavigateToDashboard: () -> Unit,
    onNavigateToCoach: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val kinematics by viewModel.currentKinematics.collectAsState()
    val poseResult by viewModel.poseResults.collectAsState(initial = PoseFrameResult.EMPTY)
    val isTracking by viewModel.isTracking.collectAsState()
    val faultCount by viewModel.faultCount.collectAsState()
    val maxSpeed by viewModel.maxSpeedKmH.collectAsState()

    val previewView = remember { PreviewView(context) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Camera Preview
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Realtime 60 FPS Pose Skeleton Overlay
        RealtimePoseSkeletonCanvas(
            poseResult = poseResult,
            isFault = kinematics?.isFault == true
        )

        // Top Telemetry Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.75f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("STROKE VELOCITY", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        val speed = kinematics?.peakWristVelocityKmH ?: 0.0
                        Text(
                            "${String.format("%.1f", speed)} km/h",
                            color = if (speed > 180.0) Color(0xFF00E676) else Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.75f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("SESSION PEAK", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${String.format("%.1f", maxSpeed ?: 0.0)} km/h",
                            color = Color(0xFFFFD600),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row {
                    IconButton(
                        onClick = onNavigateToDashboard,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.Default.Assessment, contentDescription = "Dashboard", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onNavigateToCoach,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.Default.Psychology, contentDescription = "Coach", tint = Color(0xFF00E676))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Joint Angle HUD Badges
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                kinematics?.let { k ->
                    val elbowColor = if (k.elbowExtensionDeg in 168.0..176.0) Color(0xFF00E676) else Color(0xFFFF5252)
                    BadgeMetric("ELBOW EXT", "${String.format("%.1f", k.elbowExtensionDeg)}°", elbowColor)
                    val shoulderColor = if (k.shoulderElevationDeg >= 125.0) Color(0xFF00E676) else Color(0xFFFF5252)
                    BadgeMetric("SHOULDER ELEV", "${String.format("%.1f", k.shoulderElevationDeg)}°", shoulderColor)
                    BadgeMetric("TYPE", k.strokeType.name, Color(0xFF40C4FF))
                }
            }

            // Fault Pill Banner
            kinematics?.let { k ->
                if (k.isFault && k.strokeType == StrokeType.OVERHEAD_SMASH) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFD50000).copy(alpha = 0.85f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "⚠ ${k.faultReason}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // Bottom Controls
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { viewModel.toggleCadence() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2979FF)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.VolumeUp, contentDescription = "Metronome")
                Spacer(modifier = Modifier.width(6.dp))
                Text("135 BPM Cadence")
            }

            FloatingActionButton(
                onClick = {
                    if (isTracking) {
                        viewModel.stopSession()
                    } else {
                        viewModel.startSession(lifecycleOwner, previewView)
                    }
                },
                containerColor = if (isTracking) Color(0xFFFF1744) else Color(0xFF00E676)
            ) {
                Icon(
                    imageVector = if (isTracking) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isTracking) "Stop Tracking" else "Start Tracking",
                    tint = Color.Black
                )
            }
        }
    }
}

@Composable
fun BadgeMetric(title: String, value: String, color: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(title, color = Color.LightGray, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
            Text(value, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}


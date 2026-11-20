package com.example.ui.hud

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.domain.viewmodel.CourtHudViewModel
import com.example.ui.theme.CarbonBlack
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.FaultCrimson
import com.example.ui.theme.HudContainer
import com.example.ui.theme.OpticNeonLime
import com.example.ui.theme.OutlineMuted
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.util.OverlayProfilerBadge
import kotlin.math.roundToInt

@Composable
fun CourtHudScreen(
    viewModel: CourtHudViewModel = hiltViewModel(),
    onNavigateToDashboard: () -> Unit,
    onNavigateToCoach: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val profilerMetrics by viewModel.profilerMetrics.collectAsState()

    val previewView = remember { PreviewView(context) }

    val requiredPermissions = remember {
        arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasCameraPermission = results[Manifest.permission.CAMERA] == true
        if (hasCameraPermission) {
            viewModel.startSession(lifecycleOwner, previewView)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CarbonBlack)
    ) {
        // 1. CameraX High-Throughput Preview
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Real-Time 60 FPS Anti-Aliased Skeleton Canvas Layer
        SkeletonOverlayCanvas(
            poseFrame = uiState.poseFrame,
            elbowAngleDeg = uiState.elbowAngleDeg,
            isRightHanded = uiState.isRightHanded
        )

        // 3. Real-Time Profiler Diagnostics Badge (CameraX FPS, MediaPipe Latency, Kinematics Time)
        OverlayProfilerBadge(
            metrics = profilerMetrics,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 18.dp, end = 20.dp)
        )

        // 4. Top HUD Speed Banner & Real-Time Telemetry
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            // Speed Banner Card
            Card(
                colors = CardDefaults.cardColors(containerColor = HudContainer.copy(alpha = 0.88f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, OutlineMuted, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Current Smoothed Velocity with Animated Number Transitions
                        Column {
                            Text(
                                text = "STROKE VELOCITY",
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                val currentSpeedInt = uiState.smoothedSpeedKmh.roundToInt()
                                AnimatedContent(
                                    targetState = currentSpeedInt,
                                    transitionSpec = {
                                        if (targetState > initialState) {
                                            (slideInVertically { height -> height } + fadeIn()) togetherWith
                                                    (slideOutVertically { height -> -height } + fadeOut())
                                        } else {
                                            (slideInVertically { height -> -height } + fadeIn()) togetherWith
                                                    (slideOutVertically { height -> height } + fadeOut())
                                        }
                                    },
                                    label = "SpeedCounterAnimation"
                                ) { speed ->
                                    val speedColor = if (speed >= 180) OpticNeonLime else TextPrimary
                                    Text(
                                        text = "$speed",
                                        color = speedColor,
                                        fontSize = 42.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "KM/H",
                                    color = OpticNeonLime,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                        }

                        // Session Peak & Total Smashes
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "SESSION PEAK",
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val peakSpeedInt = uiState.maxSpeedKmh.roundToInt()
                            AnimatedContent(
                                targetState = peakSpeedInt,
                                transitionSpec = {
                                    (slideInVertically { height -> height } + fadeIn()) togetherWith
                                            (slideOutVertically { height -> -height } + fadeOut())
                                },
                                label = "PeakCounterAnimation"
                            ) { peak ->
                                Text(
                                    text = "$peak km/h",
                                    color = Color(0xFFFFD600),
                                    fontSize = 24.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "SMASHES: ${uiState.totalSmashes}",
                                color = CyberCyan,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Angle Telemetry Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val elbowColor = if (uiState.elbowAngleDeg >= 165.0) OpticNeonLime else FaultCrimson
                        HudAngleBadge(
                            title = "ELBOW EXT",
                            value = "${uiState.elbowAngleDeg.roundToInt()}°",
                            target = "168°-176°",
                            valueColor = elbowColor,
                            modifier = Modifier.weight(1f)
                        )

                        val shoulderColor = if (uiState.shoulderAngleDeg >= 125.0) OpticNeonLime else FaultCrimson
                        HudAngleBadge(
                            title = "SHOULDER",
                            value = "${uiState.shoulderAngleDeg.roundToInt()}°",
                            target = ">125°",
                            valueColor = shoulderColor,
                            modifier = Modifier.weight(1f)
                        )

                        // Dominant Arm Toggle Pill
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CarbonBlack.copy(alpha = 0.7f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(0.7f)
                                .border(1.dp, OutlineMuted, RoundedCornerShape(8.dp))
                        ) {
                            IconButton(
                                onClick = { viewModel.toggleDominantHand() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.PanTool,
                                        contentDescription = "Hand",
                                        tint = CyberCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (uiState.isRightHanded) "RIGHT" else "LEFT",
                                        color = TextPrimary,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Real-Time Fault Alert Banner
            AnimatedVisibility(
                visible = uiState.lastSmashEvent?.isFault == true,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = FaultCrimson.copy(alpha = 0.92f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⚠ ${uiState.lastSmashEvent?.faultReason ?: "Mechanical Inefficiency Detected"}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // 4. Bottom Court Session Controls
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Analytics Dashboard Shortcut
            IconButton(
                onClick = onNavigateToDashboard,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(HudContainer.copy(alpha = 0.9f))
                    .border(1.dp, OutlineMuted, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Assessment,
                    contentDescription = "Dashboard",
                    tint = TextPrimary
                )
            }

            // Central Play / Stop Recording Button
            FloatingActionButton(
                onClick = {
                    if (uiState.isTracking) {
                        viewModel.stopSession()
                    } else {
                        if (hasCameraPermission) {
                            viewModel.startSession(lifecycleOwner, previewView)
                        } else {
                            permissionLauncher.launch(requiredPermissions)
                        }
                    }
                },
                containerColor = if (uiState.isTracking) FaultCrimson else OpticNeonLime,
                shape = CircleShape,
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    imageVector = if (uiState.isTracking) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (uiState.isTracking) "Stop Session" else "Start Session",
                    tint = CarbonBlack,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Gemini Coach Chat Shortcut
            IconButton(
                onClick = onNavigateToCoach,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(HudContainer.copy(alpha = 0.9f))
                    .border(1.dp, OutlineMuted, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = "Gemini Coach",
                    tint = OpticNeonLime
                )
            }
        }
    }
}

@Composable
private fun HudAngleBadge(
    title: String,
    value: String,
    target: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CarbonBlack.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.border(1.dp, OutlineMuted, RoundedCornerShape(8.dp))
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                text = title,
                color = TextMuted,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = value,
                    color = valueColor,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = target,
                    color = TextMuted.copy(alpha = 0.7f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}


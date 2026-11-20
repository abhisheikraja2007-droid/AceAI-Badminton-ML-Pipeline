package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AceAiViewModel
import com.example.ui.components.CameraKinematicViewport
import com.example.ui.components.ChipType
import com.example.ui.components.EdgeDiagnosticSlot
import com.example.ui.components.HudToleranceSlider
import com.example.ui.components.KinematicOverlayCanvas
import com.example.ui.components.PrimaryKineticButton
import com.example.ui.components.SecondaryHudButton
import com.example.ui.components.TelemetryPairRow
import com.example.ui.components.TelemetryStatusChip
import com.example.ui.theme.CarbonBlack
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.FaultCrimson
import com.example.ui.theme.HudContainer
import com.example.ui.theme.OpticNeonLime
import com.example.ui.theme.OutlineMuted
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun LiveHudScreen(
    viewModel: AceAiViewModel,
    modifier: Modifier = Modifier
) {
    val isTracking by viewModel.isTracking.collectAsState()
    val trackingMode by viewModel.trackingMode.collectAsState()
    val elbowAngle by viewModel.elbowAngle.collectAsState()
    val shoulderRotation by viewModel.shoulderRotation.collectAsState()
    val kneeFlexion by viewModel.kneeFlexion.collectAsState()
    val velocityKmH by viewModel.velocityKmH.collectAsState()
    val peakVelocityKmH by viewModel.peakVelocityKmH.collectAsState()
    val toleranceDegrees by viewModel.toleranceDegrees.collectAsState()
    val isFault by viewModel.isFault.collectAsState()
    val faultReason by viewModel.faultReason.collectAsState()
    val latencyMs by viewModel.inferenceLatencyMs.collectAsState()
    val fps by viewModel.fps.collectAsState()
    val totalStrokes by viewModel.totalStrokes.collectAsState()
    val faultCount by viewModel.faultCount.collectAsState()
    val athleteName by viewModel.athleteName.collectAsState()
    val latestFeedback by viewModel.latestCoachFeedback.collectAsState()
    val isAnalyzing by viewModel.isAnalyzingStroke.collectAsState()
    val saveStatus by viewModel.saveStatus.collectAsState()

    val modes = listOf("Smash Analysis", "Service Arc", "Footwork Kinematics")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CarbonBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // --- 1. Top HUD Header Array ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "ACEAI // HUD",
                        color = OpticNeonLime,
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.05.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TelemetryStatusChip(
                        text = if (isTracking) "EDGE: LIVE" else "STANDBY",
                        type = if (isTracking) ChipType.NOMINAL else ChipType.SPATIAL
                    )
                }
                Text(
                    text = "ATHLETE: $athleteName // CAM_01",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Real-time FPS & Latency Readout
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$fps FPS",
                        color = CyberCyan,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$latencyMs MS",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- 2. Tracking Mode Segmented Tabs ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            modes.forEach { mode ->
                SecondaryHudButton(
                    text = mode,
                    active = (mode == trackingMode),
                    onClick = { viewModel.setTrackingMode(mode) },
                    modifier = Modifier.testTag("mode_${mode.replace(" ", "_")}")
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- 3. Main Optical Viewport with CameraX and Kinematic HUD ---
        CameraKinematicViewport(
            elbowAngleDeg = elbowAngle,
            shoulderRotationDeg = shoulderRotation,
            kneeFlexionDeg = kneeFlexion,
            velocityKmH = velocityKmH,
            isFault = isFault,
            faultReason = faultReason
        )

        Spacer(modifier = Modifier.height(14.dp))

        // --- 4. Edge Diagnostic Telemetry Slots Grid ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EdgeDiagnosticSlot(
                title = "Smash Peak",
                token = "{{VEL_PEAK}}",
                value = "${velocityKmH.toInt()}",
                unit = "KM/H",
                isNominal = velocityKmH > 320,
                modifier = Modifier.weight(1f)
            )
            EdgeDiagnosticSlot(
                title = "Elbow Angle",
                token = "{{ANG_ELBOW}}",
                value = "$elbowAngle",
                unit = "DEG",
                isNominal = !isFault,
                isFault = isFault,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EdgeDiagnosticSlot(
                title = "Shoulder Rot",
                token = "{{ROT_SHLDR}}",
                value = "$shoulderRotation",
                unit = "DEG",
                isNominal = true,
                modifier = Modifier.weight(1f)
            )
            EdgeDiagnosticSlot(
                title = "Session Peak",
                token = "{{MAX_VEL}}",
                value = "${peakVelocityKmH.toInt()}",
                unit = "KM/H",
                isNominal = true,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 5. Real-Time Telemetry Key-Value Pair Table ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(HudContainer.copy(alpha = 0.85f))
                .border(1.dp, OutlineMuted, RoundedCornerShape(4.dp))
                .padding(12.dp)
        ) {
            Column {
                Text(
                    text = "DIAGNOSTIC TELEMETRY ARRAY",
                    color = OpticNeonLime,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.06.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                TelemetryPairRow(label = "Tracking Latency", value = "$latencyMs", unit = "MS")
                TelemetryPairRow(label = "Knee Flexion (Ground Drive)", value = "$kneeFlexion", unit = "DEG")
                TelemetryPairRow(label = "Total Strokes Evaluated", value = "$totalStrokes")
                TelemetryPairRow(
                    label = "Biomechanical Faults",
                    value = "$faultCount",
                    isHighlight = faultCount > 0
                )
                TelemetryPairRow(
                    label = "Kinematic Chain Efficiency",
                    value = "${((totalStrokes - faultCount).coerceAtLeast(0) * 100 / totalStrokes.coerceAtLeast(1))}%",
                    unit = "EFF"
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 6. Tolerance Adjuster Slider ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(HudContainer.copy(alpha = 0.85f))
                .border(1.dp, OutlineMuted, RoundedCornerShape(4.dp))
                .padding(12.dp)
        ) {
            HudToleranceSlider(
                value = toleranceDegrees,
                onValueChange = { viewModel.setTolerance(it) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 7. Primary Action Controls ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PrimaryKineticButton(
                text = if (isTracking) "PAUSE TRACKING" else "START TRACKING",
                onClick = { viewModel.toggleTracking() },
                modifier = Modifier.weight(1.2f),
                tag = "toggle_tracking_btn"
            )

            SecondaryHudButton(
                text = if (isAnalyzing) "ANALYZING..." else "AI STROKE AUDIT",
                onClick = { viewModel.triggerAiStrokeAnalysis() },
                modifier = Modifier.weight(1f),
                active = isAnalyzing,
                tag = "ai_stroke_audit_btn"
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SecondaryHudButton(
                text = "SYNC TO FIRESTORE",
                onClick = { viewModel.saveCurrentSession() },
                modifier = Modifier.weight(1f),
                tag = "sync_session_btn"
            )
        }

        // Save Status Toast / Indicator
        saveStatus?.let { status ->
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(OpticNeonLime.copy(alpha = 0.15f))
                    .border(1.dp, OpticNeonLime, RoundedCornerShape(4.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = status,
                    color = OpticNeonLime,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // --- 8. Latest AI Coach Feedback Card with TTS Audio Readout ---
        latestFeedback?.let { feedback ->
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(HudContainer)
                    .border(1.dp, CyberCyan, RoundedCornerShape(4.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "AI Coach",
                                tint = OpticNeonLime,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ACEAI COACHING INSIGHT",
                                color = OpticNeonLime,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Vocalize Coach Advice (TTS)
                        IconButton(
                            onClick = { viewModel.ttsService.speak(feedback) },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("tts_feedback_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Listen to Advice",
                                tint = CyberCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = feedback,
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.SansSerif,
                        lineHeight = 19.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

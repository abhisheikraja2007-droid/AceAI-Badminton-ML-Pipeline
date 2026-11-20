package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.CarbonBlack
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.FaultCrimson
import com.example.ui.theme.HudContainer
import com.example.ui.theme.OpticNeonLime
import com.example.ui.theme.OutlineMuted
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

private const val TAG = "CameraKinematic"

@Composable
fun CameraKinematicViewport(
    elbowAngleDeg: Double,
    shoulderRotationDeg: Double,
    kneeFlexionDeg: Double,
    velocityKmH: Double,
    isFault: Boolean,
    faultReason: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var useFrontCamera by remember { mutableStateOf(false) }
    var isCameraActive by remember { mutableStateOf(true) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    // Runtime Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            cameraError = "Camera permission was denied. Real-time vision tracking fallback active."
        } else {
            cameraError = null
        }
    }

    // Initialize CameraProvider when permission is granted
    LaunchedEffect(hasCameraPermission) {
        if (hasCameraPermission) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    cameraProvider = cameraProviderFuture.get()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to obtain ProcessCameraProvider", e)
                    cameraError = "Optical sensor unavailable: ${e.localizedMessage}"
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(320.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(CarbonBlack)
            .border(
                width = 1.dp,
                color = if (isFault) FaultCrimson else if (hasCameraPermission) CyberCyan else OutlineMuted,
                shape = RoundedCornerShape(4.dp)
            )
            .testTag("camera_kinematic_viewport")
    ) {
        if (hasCameraPermission) {
            // Live CameraX Feed
            if (isCameraActive && cameraProvider != null) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                        }

                        val selector = if (useFrontCamera) {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        } else {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        }

                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        try {
                            cameraProvider?.unbindAll()
                            cameraProvider?.bindToLifecycle(
                                lifecycleOwner,
                                selector,
                                preview
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "CameraX binding error", e)
                        }

                        previewView
                    },
                    update = { previewView ->
                        val selector = if (useFrontCamera) {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        } else {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        }

                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        try {
                            cameraProvider?.unbindAll()
                            cameraProvider?.bindToLifecycle(
                                lifecycleOwner,
                                selector,
                                preview
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "CameraX update error", e)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Darkening scrim so high-contrast neon HUD telemetry lines remain crystal clear
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CarbonBlack.copy(alpha = 0.35f))
                )
            } else {
                // Synthetic vision background when optical stream is paused
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(HudContainer.copy(alpha = 0.5f))
                )
            }

            // Real-time Kinematic Vector Skeleton Overlay
            KinematicOverlayCanvas(
                elbowAngleDeg = elbowAngleDeg,
                shoulderRotationDeg = shoulderRotationDeg,
                kneeFlexionDeg = kneeFlexionDeg,
                velocityKmH = velocityKmH,
                isFault = isFault
            )

            // Top Status Bar Overlay on Camera Viewport
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TelemetryStatusChip(
                        text = if (isCameraActive) "OPTICAL: LIVE" else "OPTICAL: PAUSED",
                        type = if (isCameraActive) ChipType.NOMINAL else ChipType.SPATIAL
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (useFrontCamera) "FRONT_SENSOR" else "COURT_CAM_01",
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Switch Camera & Pause Optical Feed
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { useFrontCamera = !useFrontCamera },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(HudContainer.copy(alpha = 0.85f))
                            .border(1.dp, OutlineMuted, RoundedCornerShape(4.dp))
                            .testTag("switch_camera_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "Switch Camera",
                            tint = CyberCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = { isCameraActive = !isCameraActive },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(HudContainer.copy(alpha = 0.85f))
                            .border(1.dp, OutlineMuted, RoundedCornerShape(4.dp))
                            .testTag("toggle_optical_feed_btn")
                    ) {
                        Icon(
                            imageVector = if (isCameraActive) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            contentDescription = "Toggle Optical Stream",
                            tint = if (isCameraActive) OpticNeonLime else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Fault Alert Banner
            if (isFault && faultReason.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(2.dp))
                        .background(FaultCrimson.copy(alpha = 0.95f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Fault",
                            tint = TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "KINEMATIC FAULT: $faultReason",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Bottom Viewport Info Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AI EDGE VISION // 18-NODE POSE MESH",
                    color = CyberCyan,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
                TelemetryStatusChip(
                    text = if (isFault) "FAULT DRIFT" else "IN TOLERANCE",
                    type = if (isFault) ChipType.FAULT else ChipType.NOMINAL
                )
            }

        } else {
            // Permission Request Flow UI
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(HudContainer.copy(alpha = 0.95f))
                    .padding(20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = "Camera Permission",
                    tint = CyberCyan,
                    modifier = Modifier.size(44.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "OPTICAL KINEMATIC SENSOR REQUIRED",
                    color = OpticNeonLime,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    letterSpacing = 0.04.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "AceAI HUD requires camera access to perform live pose estimation, joint angle measurement, and smash speed vector tracking.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                PrimaryKineticButton(
                    text = "ALLOW CAMERA TRACKING",
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.fillMaxWidth(0.85f),
                    tag = "grant_camera_permission_btn"
                )

                cameraError?.let { err ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = err,
                        color = FaultCrimson,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

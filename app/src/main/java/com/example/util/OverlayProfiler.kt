package com.example.util

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CarbonBlack
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.FaultCrimson
import com.example.ui.theme.OpticNeonLime
import com.example.ui.theme.OutlineMuted
import com.example.ui.theme.TextMuted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton

data class ProfilerMetrics(
    val currentFps: Double = 0.0,
    val mediaPipeLatencyMs: Double = 0.0,
    val kinematicsLatencyMs: Double = 0.0,
    val totalPipelineLatencyMs: Double = 0.0,
    val droppedFrames: Int = 0,
    val isThermalThrottling: Boolean = false,
    val frameCount: Long = 0L
)

/**
 * Diagnostic profiling utility for real-time validation of steady 60 FPS performance,
 * MediaPipe Tasks Vision GPU inference latency, and Kinematics Engine processing time
 * during live court testing on devices such as Nothing Phone (2a).
 */
@Singleton
class OverlayProfiler @Inject constructor() {

    companion object {
        private const val FPS_WINDOW_SIZE = 60
        private const val TARGET_FRAME_DURATION_MS = 16.666 // 60 FPS = ~16.6ms per frame
        private const val THROTTLE_FPS_THRESHOLD = 48.0
    }

    private val frameTimestamps = ArrayDeque<Long>(FPS_WINDOW_SIZE)
    private val mpLatencies = ArrayDeque<Long>(30)
    private val kinLatencies = ArrayDeque<Long>(30)

    private var totalDroppedFrames = 0
    private var totalFrameCount = 0L
    private var lastFrameTimeMs = 0L

    private val _metrics = MutableStateFlow(ProfilerMetrics())
    val metrics: StateFlow<ProfilerMetrics> = _metrics.asStateFlow()

    /**
     * Records a newly captured and analyzed camera frame.
     * @param mediaPipeLatencyMs Time taken by MediaPipe Tasks Vision PoseLandmarker.
     * @param kinematicsLatencyMs Time taken by 3D dot-product math & TFLite sliding window.
     */
    fun recordFrame(mediaPipeLatencyMs: Long, kinematicsLatencyMs: Long) {
        val now = SystemClock.uptimeMillis()
        totalFrameCount++

        synchronized(this) {
            // Check frame drop (if frame interval exceeds 25ms, a frame was dropped at 60 FPS)
            if (lastFrameTimeMs > 0L) {
                val frameDelta = now - lastFrameTimeMs
                if (frameDelta > 25L) {
                    val missed = ((frameDelta - TARGET_FRAME_DURATION_MS) / TARGET_FRAME_DURATION_MS).toInt().coerceAtLeast(1)
                    totalDroppedFrames += missed
                }
            }
            lastFrameTimeMs = now

            // Update sliding window of frame timestamps
            if (frameTimestamps.size >= FPS_WINDOW_SIZE) {
                frameTimestamps.removeFirst()
            }
            frameTimestamps.addLast(now)

            // Update latency queues
            if (mpLatencies.size >= 30) mpLatencies.removeFirst()
            mpLatencies.addLast(mediaPipeLatencyMs)

            if (kinLatencies.size >= 30) kinLatencies.removeFirst()
            kinLatencies.addLast(kinematicsLatencyMs)

            // Calculate rolling FPS
            val fps = if (frameTimestamps.size >= 2) {
                val durationMs = frameTimestamps.last() - frameTimestamps.first()
                if (durationMs > 0) {
                    ((frameTimestamps.size - 1) * 1000.0) / durationMs
                } else 0.0
            } else 0.0

            val avgMp = if (mpLatencies.isNotEmpty()) mpLatencies.average() else 0.0
            val avgKin = if (kinLatencies.isNotEmpty()) kinLatencies.average() else 0.0
            val totalPipe = avgMp + avgKin
            val isThrottling = fps < THROTTLE_FPS_THRESHOLD && frameTimestamps.size >= 30

            _metrics.value = ProfilerMetrics(
                currentFps = fps,
                mediaPipeLatencyMs = avgMp,
                kinematicsLatencyMs = avgKin,
                totalPipelineLatencyMs = totalPipe,
                droppedFrames = totalDroppedFrames,
                isThermalThrottling = isThrottling,
                frameCount = totalFrameCount
            )
        }
    }

    fun reset() {
        synchronized(this) {
            frameTimestamps.clear()
            mpLatencies.clear()
            kinLatencies.clear()
            totalDroppedFrames = 0
            totalFrameCount = 0L
            lastFrameTimeMs = 0L
            _metrics.value = ProfilerMetrics()
        }
    }
}

/**
 * On-screen real-time diagnostic badge rendered on the Camera HUD.
 */
@Composable
fun OverlayProfilerBadge(
    metrics: ProfilerMetrics,
    modifier: Modifier = Modifier
) {
    val fpsColor = when {
        metrics.currentFps >= 55.0 -> OpticNeonLime
        metrics.currentFps >= 45.0 -> Color(0xFFFFD600)
        else -> FaultCrimson
    }

    Row(
        modifier = modifier
            .background(CarbonBlack.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
            .border(1.dp, OutlineMuted, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // FPS Indicator
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${String.format("%.1f", metrics.currentFps)}",
                color = fpsColor,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "FPS",
                color = TextMuted,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Text("•", color = OutlineMuted, fontSize = 10.sp)

        // MediaPipe Tasks Vision Latency
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "MP: ${String.format("%.1f", metrics.mediaPipeLatencyMs)}ms",
                color = CyberCyan,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        Text("•", color = OutlineMuted, fontSize = 10.sp)

        // Kinematics Math Engine Processing Time
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "KIN: ${String.format("%.1f", metrics.kinematicsLatencyMs)}ms",
                color = OpticNeonLime,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        if (metrics.droppedFrames > 0) {
            Text("•", color = OutlineMuted, fontSize = 10.sp)
            Text(
                text = "DROP: ${metrics.droppedFrames}",
                color = if (metrics.isThermalThrottling) FaultCrimson else Color(0xFFFFD600),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

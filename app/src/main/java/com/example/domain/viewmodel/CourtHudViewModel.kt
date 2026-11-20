package com.example.domain.viewmodel

import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.room.SessionDao
import com.example.data.local.room.SessionEntity
import com.example.data.local.room.SmashRecordDao
import com.example.data.local.room.SmashRecordEntity
import com.example.domain.audio.AudioCoachManager
import com.example.domain.engine.KinematicsEngine
import com.example.domain.model.PoseFrameResult
import com.example.domain.model.SmashEvent
import com.example.hardware.camerax.CameraManager
import com.example.hardware.mediapipe.PoseLandmarkerHelper
import com.example.util.OverlayProfiler
import com.example.util.ProfilerMetrics
import dagger.hilt.android.lifecycle.HiltViewModel
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import kotlin.math.max

data class HudUiState(
    val currentSpeedKmh: Double = 0.0,
    val smoothedSpeedKmh: Double = 0.0,
    val maxSpeedKmh: Double = 0.0,
    val totalSmashes: Int = 0,
    val elbowAngleDeg: Double = 0.0,
    val shoulderAngleDeg: Double = 0.0,
    val contactHeightMeters: Double = 0.0,
    val isSmashCandidate: Boolean = false,
    val lastSmashEvent: SmashEvent? = null,
    val poseFrame: PoseFrameResult = PoseFrameResult.EMPTY,
    val isTracking: Boolean = false,
    val sessionId: String = "",
    val isRightHanded: Boolean = true
)

@HiltViewModel
class CourtHudViewModel @Inject constructor(
    private val cameraManager: CameraManager,
    private val poseLandmarkerHelper: PoseLandmarkerHelper,
    private val kinematicsEngine: KinematicsEngine,
    private val audioCoachManager: AudioCoachManager,
    private val overlayProfiler: OverlayProfiler,
    private val sessionDao: SessionDao,
    private val smashRecordDao: SmashRecordDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(HudUiState(sessionId = UUID.randomUUID().toString()))
    val uiState: StateFlow<HudUiState> = _uiState.asStateFlow()

    val profilerMetrics: StateFlow<ProfilerMetrics> = overlayProfiler.metrics

    init {
        // Collect real-time telemetry from KinematicsEngine
        viewModelScope.launch {
            kinematicsEngine.liveTelemetry.collect { telemetry ->
                _uiState.update { current ->
                    current.copy(
                        currentSpeedKmh = telemetry.instantaneousVelocityKmh,
                        smoothedSpeedKmh = telemetry.smoothedVelocityKmh,
                        elbowAngleDeg = telemetry.elbowExtensionDeg,
                        shoulderAngleDeg = telemetry.shoulderElevationDeg,
                        contactHeightMeters = telemetry.contactHeightMeters,
                        isSmashCandidate = telemetry.isSmashCandidate
                    )
                }
            }
        }

        // Collect and persist verified SmashEvents
        viewModelScope.launch {
            kinematicsEngine.smashEvents.collect { smash ->
                handleSmashEvent(smash)
            }
        }
    }

    private suspend fun handleSmashEvent(smash: SmashEvent) {
        val currentSessionId = _uiState.value.sessionId

        // 1. Persist to Room SQLite
        val record = SmashRecordEntity(
            smashId = UUID.randomUUID().toString(),
            sessionId = currentSessionId,
            timestampMs = smash.timestampMs,
            velocityKmh = smash.peakVelocityKmh,
            elbowAngleDeg = smash.impactElbowAngleDeg,
            shoulderAngleDeg = smash.impactShoulderAngleDeg,
            contactHeightMeters = smash.contactHeightMeters,
            isSynced = false
        )
        smashRecordDao.insertSmash(record)

        // 2. Trigger Audio Coach Immediate Callout
        audioCoachManager.speakSmashCallout(smash)

        // 3. Update UI state with new speed peak and counter
        _uiState.update { current ->
            current.copy(
                maxSpeedKmh = max(current.maxSpeedKmh, smash.peakVelocityKmh),
                totalSmashes = current.totalSmashes + 1,
                lastSmashEvent = smash
            )
        }
    }

    fun startSession(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val activeSessionId = _uiState.value.sessionId

        // Create or initialize Session in Room database
        viewModelScope.launch {
            val session = SessionEntity(
                sessionId = activeSessionId,
                userId = "athlete_local",
                startTimeMs = System.currentTimeMillis()
            )
            sessionDao.insertSession(session)
        }

        poseLandmarkerHelper.setupPoseLandmarker(
            modelType = PoseLandmarkerHelper.ModelType.HEAVY,
            preferGpu = true
        )

        // Feed incoming camera poses to the Kinematics Engine & Profile Pipeline
        viewModelScope.launch {
            poseLandmarkerHelper.poseResults.collect { poseResult ->
                _uiState.update { it.copy(poseFrame = poseResult) }
                val kinStart = SystemClock.uptimeMillis()
                kinematicsEngine.processPoseFrame(poseResult)
                val kinLatency = SystemClock.uptimeMillis() - kinStart

                overlayProfiler.recordFrame(
                    mediaPipeLatencyMs = poseResult.inferenceTimeMs,
                    kinematicsLatencyMs = kinLatency
                )
            }
        }

        // Launch CameraX 60 FPS capture
        cameraManager.startCamera60Fps(lifecycleOwner, previewView, targetFps = 60) { mpImage, timestampMs ->
            poseLandmarkerHelper.detectLiveStream(
                mpImage = mpImage,
                timestampMs = timestampMs,
                isFrontCamera = false
            )
        }

        _uiState.update { it.copy(isTracking = true) }
    }

    fun stopSession() {
        cameraManager.stopCamera()
        poseLandmarkerHelper.clearPoseLandmarker()

        val activeSessionId = _uiState.value.sessionId
        val state = _uiState.value

        viewModelScope.launch {
            sessionDao.closeSession(activeSessionId, System.currentTimeMillis())
            sessionDao.updateSessionStats(
                sessionId = activeSessionId,
                totalSmashes = state.totalSmashes,
                maxSpeed = state.maxSpeedKmh,
                avgSpeed = state.smoothedSpeedKmh,
                avgElbow = state.elbowAngleDeg
            )
        }

        _uiState.update { it.copy(isTracking = false) }
    }

    fun toggleDominantHand() {
        val next = !_uiState.value.isRightHanded
        kinematicsEngine.isRightHandDominant = next
        _uiState.update { it.copy(isRightHanded = next) }
    }

    fun resetSession() {
        stopSession()
        kinematicsEngine.reset()
        _uiState.value = HudUiState(sessionId = UUID.randomUUID().toString())
    }

    override fun onCleared() {
        super.onCleared()
        stopSession()
        audioCoachManager.shutdown()
    }
}


package com.example.domain.viewmodel

import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.StrokeRepository
import com.example.domain.audio.AudioCoachManager
import com.example.domain.audio.CadenceAudioEngine
import com.example.domain.model.StrokeKinematics
import com.example.domain.model.StrokeType
import com.example.domain.usecase.ProcessLivePoseUseCase
import com.example.hardware.camerax.CameraManager
import com.example.hardware.mediapipe.PoseLandmarkerHelper
import com.example.hardware.sensors.CourtSensorFusion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class LiveHudViewModel @Inject constructor(
    private val cameraManager: CameraManager,
    private val poseLandmarkerHelper: PoseLandmarkerHelper,
    private val sensorFusion: CourtSensorFusion,
    private val processLivePoseUseCase: ProcessLivePoseUseCase,
    private val strokeRepository: StrokeRepository,
    private val audioCoachManager: AudioCoachManager,
    private val cadenceAudioEngine: CadenceAudioEngine
) : ViewModel() {

    val currentSessionId: String = UUID.randomUUID().toString()

    val currentKinematics: StateFlow<StrokeKinematics?> = processLivePoseUseCase.currentKinematics
    val poseResults = poseLandmarkerHelper.poseResults
    val sensorData = sensorFusion.sensorData

    val faultCount: StateFlow<Int> = strokeRepository.getFaultCount(currentSessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val maxSpeedKmH: StateFlow<Double?> = strokeRepository.getMaxSpeed(currentSessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    init {
        sensorFusion.startListening()
        processLivePoseUseCase.setSessionId(currentSessionId)

        viewModelScope.launch {
            processLivePoseUseCase.smashEvents.collect { smash ->
                audioCoachManager.speakSmashCallout(smash)
            }
        }
    }

    fun startSession(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        poseLandmarkerHelper.setupPoseLandmarker(
            modelType = PoseLandmarkerHelper.ModelType.HEAVY,
            preferGpu = true
        )

        viewModelScope.launch {
            poseLandmarkerHelper.poseResults.collect { poseResult ->
                processLivePoseUseCase.processFrame(poseResult, currentSessionId)
            }
        }

        cameraManager.startCamera60Fps(lifecycleOwner, previewView, targetFps = 60) { mpImage, timestampMs ->
            poseLandmarkerHelper.detectLiveStream(mpImage, timestampMs, isFrontCamera = false)
        }
        _isTracking.value = true
    }

    fun toggleCadence(bpm: Int = 135) {
        if (cadenceAudioEngine.isCadenceActive()) {
            cadenceAudioEngine.stopCadence()
        } else {
            cadenceAudioEngine.startCadence(bpm)
        }
    }

    fun stopSession() {
        cameraManager.stopCamera()
        poseLandmarkerHelper.clearPoseLandmarker()
        sensorFusion.stopListening()
        cadenceAudioEngine.stopCadence()
        _isTracking.value = false
    }

    override fun onCleared() {
        super.onCleared()
        stopSession()
        audioCoachManager.shutdown()
    }
}


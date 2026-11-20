package com.example.domain.usecase

import com.example.data.local.room.StrokeEntity
import com.example.data.repository.StrokeRepository
import com.example.domain.engine.KinematicsEngine
import com.example.domain.model.PoseFrameResult
import com.example.domain.model.SmashEvent
import com.example.domain.model.StrokeKinematics
import com.example.domain.model.StrokeType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProcessLivePoseUseCase @Inject constructor(
    val kinematicsEngine: KinematicsEngine,
    private val strokeRepository: StrokeRepository
) {
    private val _currentKinematics = MutableStateFlow<StrokeKinematics?>(null)
    val currentKinematics: StateFlow<StrokeKinematics?> = _currentKinematics.asStateFlow()

    val smashEvents: SharedFlow<SmashEvent> = kinematicsEngine.smashEvents
    val liveTelemetry = kinematicsEngine.liveTelemetry

    private var activeSessionId: String = "active_court_session"

    init {
        CoroutineScope(Dispatchers.Default).launch {
            kinematicsEngine.smashEvents.collect { smash ->
                // Map SmashEvent to Room database entity
                val entity = StrokeEntity(
                    sessionId = activeSessionId,
                    strokeType = StrokeType.OVERHEAD_SMASH.name,
                    peakWristVelocityKmH = smash.peakVelocityKmh,
                    elbowExtensionDeg = smash.impactElbowAngleDeg,
                    shoulderElevationDeg = smash.impactShoulderAngleDeg,
                    contactHeightM = smash.contactHeightMeters,
                    isFault = smash.isFault,
                    faultReason = smash.faultReason,
                    timestampMs = smash.timestampMs
                )
                strokeRepository.saveStroke(entity)

                // Update currentKinematics for UI HUD display
                _currentKinematics.value = StrokeKinematics(
                    strokeType = StrokeType.OVERHEAD_SMASH,
                    peakWristVelocityKmH = smash.peakVelocityKmh,
                    elbowExtensionDeg = smash.impactElbowAngleDeg,
                    shoulderElevationDeg = smash.impactShoulderAngleDeg,
                    contactPointHeightM = smash.contactHeightMeters,
                    torsoXFactorDeg = 32.5,
                    isFault = smash.isFault,
                    faultReason = smash.faultReason,
                    timestampMs = smash.timestampMs
                )
            }
        }
    }

    fun setSessionId(sessionId: String) {
        this.activeSessionId = sessionId
    }

    suspend fun processFrame(
        poseResult: PoseFrameResult,
        sessionId: String
    ) {
        this.activeSessionId = sessionId
        kinematicsEngine.processPoseFrame(poseResult)

        val telemetry = kinematicsEngine.liveTelemetry.value
        if (_currentKinematics.value?.strokeType != StrokeType.OVERHEAD_SMASH ||
            System.currentTimeMillis() - (_currentKinematics.value?.timestampMs ?: 0L) > 1500L
        ) {
            _currentKinematics.value = StrokeKinematics(
                strokeType = if (telemetry.isSmashCandidate) StrokeType.OVERHEAD_SMASH else StrokeType.IDLE_FOOTWORK,
                peakWristVelocityKmH = telemetry.smoothedVelocityKmh,
                elbowExtensionDeg = telemetry.elbowExtensionDeg,
                shoulderElevationDeg = telemetry.shoulderElevationDeg,
                contactPointHeightM = telemetry.contactHeightMeters,
                torsoXFactorDeg = 28.0,
                isFault = false,
                faultReason = "",
                timestampMs = telemetry.timestampMs
            )
        }
    }
}

package com.example.domain.usecase

import com.example.data.local.room.SessionDao
import com.example.data.local.room.SmashRecordDao
import com.example.domain.model.SessionSummary
import com.example.domain.model.SmashRecord
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyzeSessionUseCase @Inject constructor(
    private val sessionDao: SessionDao,
    private val smashRecordDao: SmashRecordDao
) {
    suspend fun execute(sessionId: String, durationSec: Int): SessionSummary {
        val activeSession = sessionDao.observeActiveSession().first()
            ?: sessionDao.observeAllSessionsSummary().first().firstOrNull()

        val targetSessionId = if (sessionId.isNotBlank() && sessionId != "active_court_session") {
            sessionId
        } else {
            activeSession?.sessionId ?: ""
        }

        val smashes = if (targetSessionId.isNotBlank()) {
            smashRecordDao.observeCurrentSessionSmashes(targetSessionId).first()
        } else {
            emptyList()
        }

        if (smashes.isEmpty()) {
            return SessionSummary(
                sessionId = targetSessionId.ifEmpty { "active_court_session" },
                durationSec = durationSec,
                totalSmashes = 0,
                maxSpeedKmh = 0.0,
                avgSpeedKmh = 0.0,
                avgElbowAngleDeg = 0.0,
                avgShoulderAngleDeg = 0.0
            )
        }

        val maxSpeed = smashes.maxOf { it.velocityKmh }
        val avgSpeed = smashes.map { it.velocityKmh }.average()
        val avgElbow = smashes.map { it.elbowAngleDeg }.average()
        val avgShoulder = smashes.map { it.shoulderAngleDeg }.average()

        val sortedBySpeed = smashes.sortedByDescending { it.velocityKmh }
        val fastest = sortedBySpeed.take(5).map {
            SmashRecord(
                timestampMs = it.timestampMs,
                wristVelocityKmh = it.velocityKmh,
                elbowExtensionDeg = it.elbowAngleDeg,
                shoulderElevationDeg = it.shoulderAngleDeg,
                contactHeightM = it.contactHeightMeters
            )
        }
        val slowest = sortedBySpeed.takeLast(5).reversed().map {
            SmashRecord(
                timestampMs = it.timestampMs,
                wristVelocityKmh = it.velocityKmh,
                elbowExtensionDeg = it.elbowAngleDeg,
                shoulderElevationDeg = it.shoulderAngleDeg,
                contactHeightM = it.contactHeightMeters
            )
        }

        return SessionSummary(
            sessionId = targetSessionId,
            durationSec = durationSec,
            totalSmashes = smashes.size,
            maxSpeedKmh = maxSpeed,
            avgSpeedKmh = avgSpeed,
            avgElbowAngleDeg = avgElbow,
            avgShoulderAngleDeg = avgShoulder,
            fastestSmashes = fastest,
            slowestSmashes = slowest
        )
    }
}

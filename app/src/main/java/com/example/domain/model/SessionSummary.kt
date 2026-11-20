package com.example.domain.model

data class SmashRecord(
    val timestampMs: Long,
    val wristVelocityKmh: Double,
    val elbowExtensionDeg: Double,
    val shoulderElevationDeg: Double,
    val contactHeightM: Double
)

data class SessionSummary(
    val sessionId: String,
    val durationSec: Int,
    val totalSmashes: Int,
    val maxSpeedKmh: Double,
    val avgSpeedKmh: Double,
    val avgElbowAngleDeg: Double,
    val avgShoulderAngleDeg: Double,
    val fastestSmashes: List<SmashRecord> = emptyList(),
    val slowestSmashes: List<SmashRecord> = emptyList()
)


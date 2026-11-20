package com.example.domain.model

enum class StrokeType {
    OVERHEAD_SMASH,
    OTHER_STROKE,
    IDLE_FOOTWORK,
    UNKNOWN
}

data class StrokeKinematics(
    val strokeType: StrokeType,
    val peakWristVelocityKmH: Double,
    val elbowExtensionDeg: Double,
    val shoulderElevationDeg: Double,
    val contactPointHeightM: Double,
    val torsoXFactorDeg: Double,
    val isFault: Boolean,
    val faultReason: String,
    val timestampMs: Long = System.currentTimeMillis()
)


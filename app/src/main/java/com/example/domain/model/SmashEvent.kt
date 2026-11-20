package com.example.domain.model

/**
 * Emitted when a genuine overhead smash is detected by KinematicsEngine and verified by TFLite.
 */
data class SmashEvent(
    val peakVelocityKmh: Double,
    val impactElbowAngleDeg: Double,
    val impactShoulderAngleDeg: Double,
    val contactHeightMeters: Double,
    val timestampMs: Long,
    val probability: Float,
    val isFault: Boolean = false,
    val faultReason: String = ""
)


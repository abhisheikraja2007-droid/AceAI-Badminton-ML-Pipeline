package com.example.data.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Anthropometric athlete calibration parameters for scaling kinematics and leverage ratios.
 */
@Entity(tableName = "calibrations")
data class CalibrationEntity(
    @PrimaryKey
    val userId: String,
    val heightCm: Double,
    val forearmCm: Double,
    val dominantHand: String = "right",
    val smashThresholdKmh: Double = 110.0,
    val updatedAt: Long = System.currentTimeMillis()
)


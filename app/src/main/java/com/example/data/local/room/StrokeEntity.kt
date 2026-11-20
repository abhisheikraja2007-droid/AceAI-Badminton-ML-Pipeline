package com.example.data.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "strokes")
data class StrokeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val strokeType: String,
    val peakWristVelocityKmH: Double,
    val elbowExtensionDeg: Double,
    val shoulderElevationDeg: Double,
    val contactHeightM: Double,
    val isFault: Boolean,
    val faultReason: String,
    val timestampMs: Long
)


package com.example.data.local.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted badminton training or match session record.
 */
@Entity(
    tableName = "sessions",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["startTimeMs"]),
        Index(value = ["isSynced"])
    ]
)
data class SessionEntity(
    @PrimaryKey
    val sessionId: String,
    val userId: String,
    val startTimeMs: Long,
    val endTimeMs: Long? = null,
    val totalSmashes: Int = 0,
    val maxSpeedKmh: Double = 0.0,
    val avgSpeedKmh: Double = 0.0,
    val avgElbowAngleDeg: Double = 0.0,
    val aiCoachingJson: String? = null,
    val isSynced: Boolean = false
)


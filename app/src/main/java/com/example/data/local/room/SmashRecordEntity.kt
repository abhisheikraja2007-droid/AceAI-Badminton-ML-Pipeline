package com.example.data.local.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Granular stroke telemetry captured at impact for a single verified overhead smash.
 * Cascades on delete when parent [SessionEntity] is removed.
 */
@Entity(
    tableName = "smash_records",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["timestampMs"]),
        Index(value = ["isSynced"])
    ]
)
data class SmashRecordEntity(
    @PrimaryKey
    val smashId: String,
    val sessionId: String,
    val timestampMs: Long,
    val velocityKmh: Double,
    val elbowAngleDeg: Double,
    val shoulderAngleDeg: Double,
    val contactHeightMeters: Double,
    val isSynced: Boolean = false
)


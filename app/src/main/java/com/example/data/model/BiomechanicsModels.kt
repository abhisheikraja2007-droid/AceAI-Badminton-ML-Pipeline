package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class TrackingSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sessionName: String = "",
    val athleteName: String = "",
    val sport: String = "Badminton",
    val trackingMode: String = "Smash Analysis",
    val peakSmashVelocity: Double = 0.0,
    val avgElbowAngle: Double = 0.0,
    val faultCount: Int = 0,
    val totalStrokes: Int = 0,
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class TelemetryLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val strokeType: String = "",
    val elbowAngle: Double = 0.0,
    val shoulderRotation: Double = 0.0,
    val velocityKmH: Double = 0.0,
    val inferenceLatencyMs: Double = 0.0,
    val isFault: Boolean = false,
    val faultReason: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CoachingNote(
    @DocumentId val id: String = "",
    val userId: String = "",
    val topic: String = "",
    val coachAdvice: String = "",
    val modelUsed: String = "gemini-3.8-flash",
    @ServerTimestamp val createdAt: Timestamp? = null
)

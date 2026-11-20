package com.example.data.remote.firestore

import com.example.data.local.room.StrokeEntity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class SessionTelemetryPayload(
    val sessionId: String = "",
    val athleteId: String = "",
    val durationSec: Int = 0,
    val totalSmashes: Int = 0,
    val maxSpeedKmh: Double = 0.0,
    val avgSpeedKmh: Double = 0.0,
    val avgElbowAngleDeg: Double = 0.0,
    val avgShoulderAngleDeg: Double = 0.0,
    val timestampMs: Long = System.currentTimeMillis()
)

@Singleton
class FirestoreDataSource @Inject constructor() {
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun uploadSessionSummary(payload: SessionTelemetryPayload) {
        firestore.collection("sessions")
            .document(payload.sessionId)
            .set(payload)
            .await()
    }

    suspend fun uploadStroke(stroke: StrokeEntity) {
        firestore.collection("sessions")
            .document(stroke.sessionId)
            .collection("strokes")
            .add(stroke)
            .await()
    }
}


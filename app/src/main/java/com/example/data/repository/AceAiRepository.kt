package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.CoachingNote
import com.example.data.model.TelemetryLog
import com.example.data.model.TrackingSession
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class AceAiRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeSessions(): Flow<List<TrackingSession>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/sessions"
        emitAll(
            db.collection("users").document(uid).collection("sessions")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot -> snapshot.toObjects(TrackingSession::class.java) }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun saveSession(
        sessionName: String,
        athleteName: String,
        sport: String,
        trackingMode: String,
        peakSmashVelocity: Double,
        avgElbowAngle: Double,
        faultCount: Int,
        totalStrokes: Int,
        notes: String
    ): String {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("sessions")
        val payload = hashMapOf(
            "userId" to uid,
            "sessionName" to sessionName,
            "athleteName" to athleteName,
            "sport" to sport,
            "trackingMode" to trackingMode,
            "peakSmashVelocity" to peakSmashVelocity,
            "avgElbowAngle" to avgElbowAngle,
            "faultCount" to faultCount,
            "totalStrokes" to totalStrokes,
            "notes" to notes,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            val doc = colRef.add(payload).await()
            doc.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, colRef.path)
            throw e
        }
    }

    fun observeTelemetryLogs(): Flow<List<TelemetryLog>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/telemetryLogs"
        emitAll(
            db.collection("users").document(uid).collection("telemetryLogs")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .snapshots()
                .map { snapshot -> snapshot.toObjects(TelemetryLog::class.java) }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun logTelemetry(
        strokeType: String,
        elbowAngle: Double,
        shoulderRotation: Double,
        velocityKmH: Double,
        inferenceLatencyMs: Double,
        isFault: Boolean,
        faultReason: String
    ) {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("telemetryLogs")
        val payload = hashMapOf(
            "userId" to uid,
            "strokeType" to strokeType,
            "elbowAngle" to elbowAngle,
            "shoulderRotation" to shoulderRotation,
            "velocityKmH" to velocityKmH,
            "inferenceLatencyMs" to inferenceLatencyMs,
            "isFault" to isFault,
            "faultReason" to faultReason,
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            colRef.add(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, colRef.path)
            throw e
        }
    }

    fun observeCoachingNotes(): Flow<List<CoachingNote>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/coachingNotes"
        emitAll(
            db.collection("users").document(uid).collection("coachingNotes")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot -> snapshot.toObjects(CoachingNote::class.java) }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun saveCoachingNote(
        topic: String,
        advice: String,
        modelUsed: String
    ) {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("coachingNotes")
        val payload = hashMapOf(
            "userId" to uid,
            "topic" to topic,
            "coachAdvice" to advice,
            "modelUsed" to modelUsed,
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            colRef.add(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, colRef.path)
            throw e
        }
    }
}

package com.example.data.repository

import com.example.data.local.room.StrokeEntity
import com.example.data.remote.firestore.SessionTelemetryPayload
import kotlinx.coroutines.flow.Flow

interface StrokeRepository {
    suspend fun saveStroke(stroke: StrokeEntity): Long
    fun getStrokesBySession(sessionId: String): Flow<List<StrokeEntity>>
    fun getFaultCount(sessionId: String): Flow<Int>
    fun getMaxSpeed(sessionId: String): Flow<Double?>
    suspend fun syncSessionToRemote(payload: SessionTelemetryPayload)
}


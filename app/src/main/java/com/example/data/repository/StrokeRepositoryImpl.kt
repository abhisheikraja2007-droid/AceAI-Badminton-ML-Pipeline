package com.example.data.repository

import com.example.data.local.room.StrokeDao
import com.example.data.local.room.StrokeEntity
import com.example.data.remote.firestore.FirestoreDataSource
import com.example.data.remote.firestore.SessionTelemetryPayload
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StrokeRepositoryImpl @Inject constructor(
    private val strokeDao: StrokeDao,
    private val firestoreDataSource: FirestoreDataSource
) : StrokeRepository {

    override suspend fun saveStroke(stroke: StrokeEntity): Long {
        val id = strokeDao.insertStroke(stroke)
        try {
            firestoreDataSource.uploadStroke(stroke)
        } catch (e: Exception) {
            // Keep local-first resiliency on network disconnect
        }
        return id
    }

    override fun getStrokesBySession(sessionId: String): Flow<List<StrokeEntity>> {
        return strokeDao.getStrokesBySession(sessionId)
    }

    override fun getFaultCount(sessionId: String): Flow<Int> {
        return strokeDao.getFaultCountBySession(sessionId)
    }

    override fun getMaxSpeed(sessionId: String): Flow<Double?> {
        return strokeDao.getMaxSpeedBySession(sessionId)
    }

    override suspend fun syncSessionToRemote(payload: SessionTelemetryPayload) {
        firestoreDataSource.uploadSessionSummary(payload)
    }
}


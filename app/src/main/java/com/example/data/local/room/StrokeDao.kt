package com.example.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StrokeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStroke(stroke: StrokeEntity): Long

    @Query("SELECT * FROM strokes WHERE sessionId = :sessionId ORDER BY timestampMs DESC")
    fun getStrokesBySession(sessionId: String): Flow<List<StrokeEntity>>

    @Query("SELECT COUNT(*) FROM strokes WHERE sessionId = :sessionId AND isFault = 1")
    fun getFaultCountBySession(sessionId: String): Flow<Int>

    @Query("SELECT MAX(peakWristVelocityKmH) FROM strokes WHERE sessionId = :sessionId")
    fun getMaxSpeedBySession(sessionId: String): Flow<Double?>
}


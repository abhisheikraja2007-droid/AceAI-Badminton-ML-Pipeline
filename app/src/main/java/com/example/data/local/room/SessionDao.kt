package com.example.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<SessionEntity>)

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Query("SELECT * FROM sessions ORDER BY startTimeMs DESC")
    fun observeAllSessionsSummary(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE sessionId = :sessionId LIMIT 1")
    fun observeSessionById(sessionId: String): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE userId = :userId ORDER BY startTimeMs DESC")
    fun observeSessionsByUser(userId: String): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE endTimeMs IS NULL ORDER BY startTimeMs DESC LIMIT 1")
    fun observeActiveSession(): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE isSynced = 0")
    suspend fun getUnsyncedSessions(): List<SessionEntity>

    @Query("UPDATE sessions SET isSynced = 1 WHERE sessionId = :sessionId")
    suspend fun markSessionSynced(sessionId: String)

    @Query("UPDATE sessions SET isSynced = 1 WHERE sessionId IN (:sessionIds)")
    suspend fun markSessionsSynced(sessionIds: List<String>)

    @Query("""
        UPDATE sessions 
        SET totalSmashes = :totalSmashes, 
            maxSpeedKmh = :maxSpeed, 
            avgSpeedKmh = :avgSpeed, 
            avgElbowAngleDeg = :avgElbow 
        WHERE sessionId = :sessionId
    """)
    suspend fun updateSessionStats(
        sessionId: String,
        totalSmashes: Int,
        maxSpeed: Double,
        avgSpeed: Double,
        avgElbow: Double
    )

    @Query("UPDATE sessions SET aiCoachingJson = :coachingJson WHERE sessionId = :sessionId")
    suspend fun updateAiCoachingJson(sessionId: String, coachingJson: String)

    @Query("UPDATE sessions SET endTimeMs = :endTimeMs WHERE sessionId = :sessionId")
    suspend fun closeSession(sessionId: String, endTimeMs: Long)

    @Query("DELETE FROM sessions WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM sessions")
    suspend fun deleteAllSessions()
}


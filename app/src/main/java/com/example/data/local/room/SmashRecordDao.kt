package com.example.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SmashRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSmash(smash: SmashRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSmashes(smashes: List<SmashRecordEntity>)

    @Query("SELECT * FROM smash_records WHERE sessionId = :sessionId ORDER BY timestampMs DESC")
    fun observeCurrentSessionSmashes(sessionId: String): Flow<List<SmashRecordEntity>>

    @Query("SELECT MAX(velocityKmh) FROM smash_records WHERE sessionId = :sessionId")
    fun observeSessionMaxSpeed(sessionId: String): Flow<Double?>

    @Query("SELECT COUNT(*) FROM smash_records WHERE sessionId = :sessionId")
    fun observeSessionSmashCount(sessionId: String): Flow<Int>

    @Query("SELECT AVG(velocityKmh) FROM smash_records WHERE sessionId = :sessionId")
    fun observeSessionAvgSpeed(sessionId: String): Flow<Double?>

    @Query("SELECT AVG(elbowAngleDeg) FROM smash_records WHERE sessionId = :sessionId")
    fun observeSessionAvgElbowAngle(sessionId: String): Flow<Double?>

    @Query("SELECT * FROM smash_records WHERE sessionId = :sessionId ORDER BY velocityKmh DESC LIMIT :limit")
    fun observeTopSmashes(sessionId: String, limit: Int = 5): Flow<List<SmashRecordEntity>>

    @Query("SELECT * FROM smash_records WHERE sessionId = :sessionId ORDER BY velocityKmh ASC LIMIT :limit")
    fun observeSlowestSmashes(sessionId: String, limit: Int = 5): Flow<List<SmashRecordEntity>>

    @Query("SELECT * FROM smash_records WHERE isSynced = 0")
    suspend fun getUnsyncedSmashes(): List<SmashRecordEntity>

    @Query("UPDATE smash_records SET isSynced = 1 WHERE smashId = :smashId")
    suspend fun markSmashSynced(smashId: String)

    @Query("UPDATE smash_records SET isSynced = 1 WHERE smashId IN (:smashIds)")
    suspend fun markSmashesSynced(smashIds: List<String>)

    @Query("DELETE FROM smash_records WHERE sessionId = :sessionId")
    suspend fun deleteSmashesBySession(sessionId: String)

    @Query("DELETE FROM smash_records WHERE smashId = :smashId")
    suspend fun deleteSmash(smashId: String)
}


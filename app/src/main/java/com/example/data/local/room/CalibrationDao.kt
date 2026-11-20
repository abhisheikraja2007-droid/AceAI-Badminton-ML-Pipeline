package com.example.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CalibrationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCalibration(calibration: CalibrationEntity)

    @Query("SELECT * FROM calibrations WHERE userId = :userId LIMIT 1")
    fun observeCalibration(userId: String): Flow<CalibrationEntity?>

    @Query("SELECT * FROM calibrations WHERE userId = :userId LIMIT 1")
    suspend fun getCalibration(userId: String): CalibrationEntity?

    @Query("SELECT * FROM calibrations ORDER BY updatedAt DESC LIMIT 1")
    fun observeLatestCalibration(): Flow<CalibrationEntity?>
}


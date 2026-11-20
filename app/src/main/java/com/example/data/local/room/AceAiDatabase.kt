package com.example.data.local.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        CalibrationEntity::class,
        SessionEntity::class,
        SmashRecordEntity::class,
        StrokeEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(AceAiConverters::class)
abstract class AceAiDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun smashRecordDao(): SmashRecordDao
    abstract fun calibrationDao(): CalibrationDao
    abstract fun strokeDao(): StrokeDao
}

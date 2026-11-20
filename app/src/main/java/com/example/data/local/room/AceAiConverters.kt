package com.example.data.local.room

import androidx.room.TypeConverter
import java.util.Date

/**
 * Room TypeConverters for timestamps and complex structures.
 */
class AceAiConverters {

    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}


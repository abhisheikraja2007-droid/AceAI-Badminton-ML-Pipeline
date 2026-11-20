package com.example.data.local.room

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAceAiDatabase(
        @ApplicationContext context: Context
    ): AceAiDatabase {
        return Room.databaseBuilder(
            context,
            AceAiDatabase::class.java,
            "aceai_kinematics.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideSessionDao(database: AceAiDatabase): SessionDao {
        return database.sessionDao()
    }

    @Provides
    fun provideSmashRecordDao(database: AceAiDatabase): SmashRecordDao {
        return database.smashRecordDao()
    }

    @Provides
    fun provideCalibrationDao(database: AceAiDatabase): CalibrationDao {
        return database.calibrationDao()
    }

    @Provides
    fun provideStrokeDao(database: AceAiDatabase): StrokeDao {
        return database.strokeDao()
    }
}

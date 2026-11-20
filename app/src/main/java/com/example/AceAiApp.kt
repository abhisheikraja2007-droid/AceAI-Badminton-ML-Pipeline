package com.example

import android.app.Application
import android.util.Log
import com.example.data.remote.SecurityInitializer
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AceAiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("AceAiApp", "Uncaught exception on thread ${thread.name}: ${throwable.message}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
        try {
            SecurityInitializer.initialize(this)
        } catch (t: Throwable) {
            Log.e("AceAiApp", "SecurityInitializer non-fatal failure: ${t.message}", t)
        }
    }
}


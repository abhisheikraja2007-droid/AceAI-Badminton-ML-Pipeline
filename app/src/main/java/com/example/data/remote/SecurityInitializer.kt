package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Handles security initialization at application startup:
 * 1. Bootstraps Firebase application context.
 * 2. Installs Firebase App Check with Play Integrity provider (or Debug provider for emulator/dev).
 */
object SecurityInitializer {
    private const val TAG = "SecurityInitializer"

    fun initialize(context: Context) {
        try {
            // 1. Initialize Firebase core safely
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
                Log.i(TAG, "Firebase core initialized successfully.")
            }

            // 2. Install Firebase App Check with Play Integrity or Debug fallback
            try {
                val appCheck = FirebaseAppCheck.getInstance()
                if (BuildConfig.DEBUG) {
                    try {
                        appCheck.installAppCheckProviderFactory(
                            DebugAppCheckProviderFactory.getInstance()
                        )
                        Log.i(TAG, "Firebase App Check initialized with DebugAppCheckProviderFactory.")
                    } catch (t: Throwable) {
                        Log.w(TAG, "DebugAppCheckProviderFactory unavailable: ${t.message}")
                    }
                } else {
                    try {
                        appCheck.installAppCheckProviderFactory(
                            PlayIntegrityAppCheckProviderFactory.getInstance()
                        )
                        Log.i(TAG, "Firebase App Check initialized with PlayIntegrityAppCheckProviderFactory.")
                    } catch (t: Throwable) {
                        Log.w(TAG, "PlayIntegrityAppCheckProviderFactory unavailable: ${t.message}")
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Firebase App Check setup bypassed: ${t.message}")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "SecurityInitializer failure (non-fatal): ${t.message}", t)
        }
    }
}


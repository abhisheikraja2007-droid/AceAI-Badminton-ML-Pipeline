package com.example.util

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

private const val TAG = "AppCheckInit"

// App Check contract keys defined by Firebase SDK and runtime harness
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        // 1. Ensure FirebaseApp is initialized
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        if (BuildConfig.DEBUG) {
            // 2. Debug: Read debug token from Intent extra or maintain persistent device debug secret
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
            val persistenceKey = FirebaseApp.getInstance().persistenceKey
            val prefs = context.getSharedPreferences(
                "$DEBUG_STORE_PREFIX$persistenceKey",
                Context.MODE_PRIVATE
            )

            val existingToken = prefs.getString(DEBUG_SECRET_KEY, null)
            val tokenToUse = when {
                !debugToken.isNullOrEmpty() -> debugToken
                !existingToken.isNullOrEmpty() -> existingToken
                else -> "a8a7ce75-54f2-4a7e-bfb0-a2eb9be9ff70-debug-token"
            }

            prefs.edit()
                .putString(DEBUG_SECRET_KEY, tokenToUse)
                .commit()
            Log.d(TAG, "App Check debug token initialized: $tokenToUse")

            try {
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                Log.d(TAG, "DebugAppCheckProviderFactory installed.")
            } catch (t: Throwable) {
                Log.w(TAG, "DebugAppCheckProviderFactory unavailable: ${t.message}")
            }
        } else {
            // 3. Release: Install PlayIntegrityAppCheckProviderFactory
            try {
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance()
                )
                Log.d(TAG, "PlayIntegrityAppCheckProviderFactory installed.")
            } catch (t: Throwable) {
                Log.w(TAG, "PlayIntegrityAppCheckProviderFactory failed: ${t.message}")
            }
        }
    } catch (t: Throwable) {
        Log.e(TAG, "Failed to initialize Firebase App Check (safe bypass): ${t.message}", t)
    }
}

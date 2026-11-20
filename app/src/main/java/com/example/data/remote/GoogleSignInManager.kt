package com.example.data.remote

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleSignInManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "GoogleSignInManager"
        private const val CONFIG_ASSET_NAME = "firebase-applet-config.json"
    }

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val credentialManager: CredentialManager = CredentialManager.create(context)

    /**
     * Reads `firebase-applet-config.json` dynamically from assets and parses oAuthClientId.
     */
    fun loadOAuthClientId(): String {
        val assetId = try {
            context.assets.open(CONFIG_ASSET_NAME).use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                val jsonString = reader.readText()
                val jsonObject = JSONObject(jsonString)
                when {
                    jsonObject.has("oAuthClientId") -> jsonObject.getString("oAuthClientId")
                    jsonObject.has("client_id") -> jsonObject.getString("client_id")
                    jsonObject.has("webClientId") -> jsonObject.getString("webClientId")
                    else -> ""
                }
            }
        } catch (e: Exception) {
            ""
        }

        if (assetId.isNotBlank() && !assetId.contains("placeholder")) {
            return assetId
        }

        return try {
            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (resId != 0) context.getString(resId) else "994660975565-brjnoq8khe1su1ambttiibndq3unrnrj.apps.googleusercontent.com"
        } catch (e: Exception) {
            "994660975565-brjnoq8khe1su1ambttiibndq3unrnrj.apps.googleusercontent.com"
        }
    }

    /**
     * Authenticates athlete anonymously so Cloud Firestore and Vertex AI have a valid session
     * without blocking on Google Play Services or OAuth SHA-1 registration.
     */
    suspend fun signInAnonymously(): Result<FirebaseUser> = withContext(Dispatchers.IO) {
        try {
            val authResult = auth.signInAnonymously().await()
            val user = authResult.user ?: return@withContext Result.failure(IllegalStateException("Anonymous user is null"))
            initializeUserDocument(user)
            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Anonymous sign-in fallback failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Launches the CredentialManager flow, exchanges GoogleIdTokenCredential for Firebase
     * session, and creates/updates the athlete's document at `users/{userId}` in Cloud Firestore.
     */
    suspend fun signInWithGoogle(activityContext: Activity): Result<FirebaseUser> = withContext(Dispatchers.IO) {
        try {
            val serverClientId = loadOAuthClientId()
            if (serverClientId.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Empty oAuthClientId from asset configuration"))
            }

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val credentialResponse = credentialManager.getCredential(activityContext, request)
            val credential = credentialResponse.credential

            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken

                val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = auth.signInWithCredential(firebaseCredential).await()
                val user = authResult.user ?: return@withContext Result.failure(IllegalStateException("FirebaseUser is null"))

                // Initialize user document in Firestore: users/{userId}
                initializeUserDocument(user)

                Result.success(user)
            } else {
                Result.failure(IllegalStateException("Unexpected credential type: ${credential.type}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Attempts silent background sign-in for previously authorized accounts.
     */
    suspend fun attemptSilentSignIn(): Result<FirebaseUser> = withContext(Dispatchers.IO) {
        auth.currentUser?.let {
            return@withContext Result.success(it)
        }

        try {
            val serverClientId = loadOAuthClientId()
            if (serverClientId.isBlank()) return@withContext Result.failure(IllegalStateException("Missing client ID"))

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val credentialResponse = credentialManager.getCredential(context, request)
            val credential = credentialResponse.credential

            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authResult = auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
                val user = authResult.user ?: return@withContext Result.failure(IllegalStateException("User is null"))
                initializeUserDocument(user)
                Result.success(user)
            } else {
                Result.failure(IllegalStateException("No authorized account found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun initializeUserDocument(user: FirebaseUser) {
        try {
            val userDocRef = firestore.collection("users").document(user.uid)
            val userData = hashMapOf(
                "userId" to user.uid,
                "email" to (user.email ?: ""),
                "displayName" to (user.displayName ?: "AceAI Athlete"),
                "photoUrl" to (user.photoUrl?.toString() ?: ""),
                "sport" to "Badminton",
                "lastLoginAt" to FieldValue.serverTimestamp()
            )
            userDocRef.set(userData, SetOptions.merge()).await()
            Log.i(TAG, "Initialized user document at users/${user.uid}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firestore user document: ${e.message}", e)
        }
    }

    suspend fun signOut(): Unit = withContext(Dispatchers.IO) {
        auth.signOut()
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing credential state: ${e.message}")
        }
    }

    fun getCurrentUser(): FirebaseUser? = auth.currentUser
}


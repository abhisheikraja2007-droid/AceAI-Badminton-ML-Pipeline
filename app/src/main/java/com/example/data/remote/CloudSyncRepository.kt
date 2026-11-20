package com.example.data.remote

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.local.room.SessionDao
import com.example.data.local.room.SessionEntity
import com.example.data.local.room.SmashRecordDao
import com.example.data.local.room.SmashRecordEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class SyncResultSummary(
    val sessionsSynced: Int = 0,
    val smashesSynced: Int = 0,
    val isSuccess: Boolean = true,
    val errorMessage: String? = null
)

@Singleton
class CloudSyncRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionDao: SessionDao,
    private val smashRecordDao: SmashRecordDao
) {
    companion object {
        private const val TAG = "CloudSyncRepository"
        private const val FIRESTORE_MAX_BATCH_SIZE = 450
        const val PERIODIC_SYNC_WORK_NAME = "AceAi_CloudSync_Periodic"
        const val ONE_TIME_SYNC_WORK_NAME = "AceAi_CloudSync_OneTime"
    }

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val workManager = WorkManager.getInstance(context)

    private val _syncState = MutableStateFlow<SyncResultSummary?>(null)
    val syncState: StateFlow<SyncResultSummary?> = _syncState.asStateFlow()

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    /**
     * Executes the offline-first synchronization pass using Firestore WriteBatch.
     * Uploads to:
     * - users/{userId}/sessions/{sessionId}
     * - users/{userId}/sessions/{sessionId}/smashes/{smashId}
     * Marks local Room database rows isSynced = true upon atomic commit.
     */
    suspend fun syncPendingData(): Result<SyncResultSummary> = withContext(Dispatchers.IO) {
        val user = auth.currentUser
        if (user == null) {
            val msg = "Sync skipped: No authenticated Firebase user."
            Log.w(TAG, msg)
            return@withContext Result.failure(IllegalStateException(msg))
        }

        val userId = user.uid

        try {
            // 1. Fetch unsynced entities from Room SQLite
            val unsyncedSessions = sessionDao.getUnsyncedSessions()
            val unsyncedSmashes = smashRecordDao.getUnsyncedSmashes()

            if (unsyncedSessions.isEmpty() && unsyncedSmashes.isEmpty()) {
                val summary = SyncResultSummary(sessionsSynced = 0, smashesSynced = 0, isSuccess = true)
                _syncState.value = summary
                return@withContext Result.success(summary)
            }

            Log.i(TAG, "Starting sync for user $userId: ${unsyncedSessions.size} sessions, ${unsyncedSmashes.size} smashes.")

            var totalSessionsUploaded = 0
            var totalSmashesUploaded = 0

            // 2. Upload Sessions in chunks adhering to Firestore 500-op limit
            for (sessionChunk in unsyncedSessions.chunked(FIRESTORE_MAX_BATCH_SIZE)) {
                val batch = firestore.batch()
                val chunkIds = mutableListOf<String>()

                for (session in sessionChunk) {
                    val sessionRef = firestore.collection("users")
                        .document(userId)
                        .collection("sessions")
                        .document(session.sessionId)

                    val sessionMap = hashMapOf<String, Any?>(
                        "sessionId" to session.sessionId,
                        "userId" to userId,
                        "startTimeMs" to session.startTimeMs,
                        "endTimeMs" to session.endTimeMs,
                        "totalSmashes" to session.totalSmashes,
                        "maxSpeedKmh" to session.maxSpeedKmh,
                        "avgSpeedKmh" to session.avgSpeedKmh,
                        "avgElbowAngleDeg" to session.avgElbowAngleDeg,
                        "aiCoachingJson" to session.aiCoachingJson,
                        "syncedAt" to System.currentTimeMillis()
                    )

                    batch.set(sessionRef, sessionMap, SetOptions.merge())
                    chunkIds.add(session.sessionId)
                }

                batch.commit().await()
                sessionDao.markSessionsSynced(chunkIds)
                totalSessionsUploaded += chunkIds.size
            }

            // 3. Upload Smashes in chunks to: users/{userId}/sessions/{sessionId}/smashes/{smashId}
            for (smashChunk in unsyncedSmashes.chunked(FIRESTORE_MAX_BATCH_SIZE)) {
                val batch = firestore.batch()
                val chunkIds = mutableListOf<String>()

                for (smash in smashChunk) {
                    val smashRef = firestore.collection("users")
                        .document(userId)
                        .collection("sessions")
                        .document(smash.sessionId)
                        .collection("smashes")
                        .document(smash.smashId)

                    val smashMap = hashMapOf<String, Any?>(
                        "smashId" to smash.smashId,
                        "sessionId" to smash.sessionId,
                        "timestampMs" to smash.timestampMs,
                        "velocityKmh" to smash.velocityKmh,
                        "elbowAngleDeg" to smash.elbowAngleDeg,
                        "shoulderAngleDeg" to smash.shoulderAngleDeg,
                        "contactHeightMeters" to smash.contactHeightMeters,
                        "syncedAt" to System.currentTimeMillis()
                    )

                    batch.set(smashRef, smashMap, SetOptions.merge())
                    chunkIds.add(smash.smashId)
                }

                batch.commit().await()
                smashRecordDao.markSmashesSynced(chunkIds)
                totalSmashesUploaded += chunkIds.size
            }

            val summary = SyncResultSummary(
                sessionsSynced = totalSessionsUploaded,
                smashesSynced = totalSmashesUploaded,
                isSuccess = true
            )
            _syncState.value = summary
            Log.i(TAG, "Sync commit complete: $totalSessionsUploaded sessions, $totalSmashesUploaded smashes marked isSynced=true.")
            Result.success(summary)
        } catch (e: Exception) {
            val errorSummary = SyncResultSummary(isSuccess = false, errorMessage = e.message)
            _syncState.value = errorSummary
            Log.e(TAG, "Sync failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Schedules periodic background sync every 15 minutes when connected to network.
     */
    fun schedulePeriodicSync(intervalMinutes: Long = 15L) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicWork = PeriodicWorkRequestBuilder<CloudSyncWorker>(intervalMinutes, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWork
        )
        Log.i(TAG, "Enqueued periodic sync every $intervalMinutes minutes.")
    }

    /**
     * Enqueues an immediate one-time sync task with exponential backoff retry.
     */
    fun triggerImmediateSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val oneTimeWork = OneTimeWorkRequestBuilder<CloudSyncWorker>()
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniqueWork(
            ONE_TIME_SYNC_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            oneTimeWork
        )
    }

    fun syncNowAsync() {
        coroutineScope.launch {
            syncPendingData()
        }
    }
}

/**
 * Background WorkManager worker executing offline-first synchronization.
 */
class CloudSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val db = com.example.data.local.room.DatabaseModule.provideAceAiDatabase(applicationContext)
        val sessionDao = db.sessionDao()
        val smashRecordDao = db.smashRecordDao()

        val repository = CloudSyncRepository(
            context = applicationContext,
            sessionDao = sessionDao,
            smashRecordDao = smashRecordDao
        )

        val result = repository.syncPendingData()
        return if (result.isSuccess) {
            Result.success()
        } else {
            Result.retry()
        }
    }
}


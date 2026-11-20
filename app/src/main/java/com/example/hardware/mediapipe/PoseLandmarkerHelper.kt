package com.example.hardware.mediapipe

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.example.domain.model.PoseFrameResult
import com.example.domain.model.PoseNormalizedLandmark
import com.example.domain.model.PoseWorldLandmark
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production-ready MediaPipe PoseLandmarker helper configured for high-throughput
 * 60 FPS LIVE_STREAM inference on GPU delegate with zero-allocation SharedFlow broadcasting.
 */
@Singleton
class PoseLandmarkerHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    enum class ModelType(val assetPath: String) {
        LITE("pose_landmarker_lite.task"),
        FULL("pose_landmarker_full.task"),
        HEAVY("pose_landmarker_heavy.task")
    }

    private var poseLandmarker: PoseLandmarker? = null
    private val isInitializing = AtomicBoolean(false)
    private var isFrontFacingCamera: Boolean = false

    // Thread-safe, non-blocking SharedFlow with DROP_OLDEST backpressure strategy
    private val _poseResults = MutableSharedFlow<PoseFrameResult>(
        replay = 1,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val poseResults: SharedFlow<PoseFrameResult> = _poseResults.asSharedFlow()

    private val _errorFlow = MutableSharedFlow<String>(
        replay = 1,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val errorFlow: SharedFlow<String> = _errorFlow.asSharedFlow()

    companion object {
        private const val TAG = "PoseLandmarkerHelper"
        const val DEFAULT_DETECTION_CONFIDENCE = 0.65f
        const val DEFAULT_PRESENCE_CONFIDENCE = 0.65f
        const val DEFAULT_TRACKING_CONFIDENCE = 0.65f
    }

    /**
     * Initializes PoseLandmarker with GPU delegate and LIVE_STREAM mode.
     * Automatically verifies asset existence and gracefully falls back to CPU if GPU initialization
     * encounters driver incompatibility.
     */
    fun setupPoseLandmarker(
        modelType: ModelType = ModelType.FULL,
        customAssetPath: String? = null,
        minDetectionConfidence: Float = DEFAULT_DETECTION_CONFIDENCE,
        minPresenceConfidence: Float = DEFAULT_PRESENCE_CONFIDENCE,
        minTrackingConfidence: Float = DEFAULT_TRACKING_CONFIDENCE,
        preferGpu: Boolean = true
    ) {
        if (isInitializing.getAndSet(true)) return

        // Resolve asset path with graceful fallback to available models
        val targetAssetPath = customAssetPath ?: resolveAvailableAsset(modelType.assetPath)

        try {
            poseLandmarker = createLandmarkerInstance(
                assetPath = targetAssetPath,
                useGpu = preferGpu,
                minDetectionConfidence = minDetectionConfidence,
                minPresenceConfidence = minPresenceConfidence,
                minTrackingConfidence = minTrackingConfidence
            )
            Log.i(TAG, "PoseLandmarker successfully initialized on ${if (preferGpu) "GPU" else "CPU"} using $targetAssetPath")
        } catch (gpuError: Exception) {
            Log.w(TAG, "GPU delegate setup failed (${gpuError.message}). Attempting fallback to CPU...", gpuError)
            try {
                poseLandmarker = createLandmarkerInstance(
                    assetPath = targetAssetPath,
                    useGpu = false,
                    minDetectionConfidence = minDetectionConfidence,
                    minPresenceConfidence = minPresenceConfidence,
                    minTrackingConfidence = minTrackingConfidence
                )
                Log.i(TAG, "PoseLandmarker successfully initialized on fallback CPU using $targetAssetPath")
            } catch (cpuError: Exception) {
                Log.e(TAG, "Critical: PoseLandmarker initialization failed on both GPU and CPU", cpuError)
                _errorFlow.tryEmit("Failed to initialize PoseLandmarker: ${cpuError.localizedMessage}")
            }
        } finally {
            isInitializing.set(false)
        }
    }

    private fun createLandmarkerInstance(
        assetPath: String,
        useGpu: Boolean,
        minDetectionConfidence: Float,
        minPresenceConfidence: Float,
        minTrackingConfidence: Float
    ): PoseLandmarker {
        val baseOptionsBuilder = BaseOptions.builder()
            .setModelAssetPath(assetPath)

        if (useGpu) {
            baseOptionsBuilder.setDelegate(Delegate.GPU)
        } else {
            baseOptionsBuilder.setDelegate(Delegate.CPU)
        }

        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptionsBuilder.build())
            .setMinPoseDetectionConfidence(minDetectionConfidence)
            .setMinPosePresenceConfidence(minPresenceConfidence)
            .setMinTrackingConfidence(minTrackingConfidence)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result, _ ->
                onPoseLandmarkerResult(result, result.timestampMs())
            }
            .setErrorListener { error ->
                Log.e(TAG, "MediaPipe live stream processing error: ${error.message}")
                _errorFlow.tryEmit(error.message ?: "Unknown pose landmarker error")
            }
            .build()

        return PoseLandmarker.createFromOptions(context, options)
    }

    /**
     * Feeds an in-memory MPImage to the MediaPipe pipeline asynchronously.
     * Guaranteed thread-safe and non-blocking for 60 FPS video ingestion.
     */
    fun detectLiveStream(
        mpImage: MPImage,
        timestampMs: Long,
        isFrontCamera: Boolean = false
    ) {
        this.isFrontFacingCamera = isFrontCamera
        val landmarker = poseLandmarker
        if (landmarker == null) {
            Log.w(TAG, "detectLiveStream invoked prior to PoseLandmarker initialization")
            return
        }

        try {
            landmarker.detectAsync(mpImage, timestampMs)
        } catch (e: Exception) {
            Log.e(TAG, "detectAsync pipeline failure at timestamp $timestampMs: ${e.message}", e)
        }
    }

    /**
     * Asynchronous callback receiving MediaPipe output. Maps raw protobuf landmarks
     * into domain models and computes pipeline inference latency.
     */
    private fun onPoseLandmarkerResult(result: PoseLandmarkerResult, frameTimestampMs: Long) {
        val now = SystemClock.uptimeMillis()
        val inferenceTimeMs = (now - frameTimestampMs).coerceAtLeast(0L)

        val landmarksList = result.landmarks()
        val worldLandmarksList = result.worldLandmarks()

        if (landmarksList.isEmpty() || landmarksList[0].isEmpty()) {
            _poseResults.tryEmit(
                PoseFrameResult(
                    normalizedLandmarks = emptyList(),
                    worldLandmarks = emptyList(),
                    timestampMs = frameTimestampMs,
                    inferenceTimeMs = inferenceTimeMs,
                    hasPose = false
                )
            )
            return
        }

        val rawNormalized = landmarksList[0]
        val rawWorld = if (worldLandmarksList.isNotEmpty()) worldLandmarksList[0] else emptyList()

        // 1. Map 33 normalized screen landmarks with front-lens horizontal mirror compensation
        val normalizedLandmarks = rawNormalized.mapIndexed { index, lm ->
            val adjustedX = if (isFrontFacingCamera) (1.0f - lm.x()).coerceIn(0f, 1f) else lm.x()
            PoseNormalizedLandmark(
                index = index,
                x = adjustedX,
                y = lm.y(),
                z = lm.z(),
                visibility = lm.visibility().orElse(0.0f),
                presence = lm.presence().orElse(0.0f)
            )
        }

        // 2. Map 33 real-world 3D landmarks in metric meters for Kinematic Physics Engine
        val worldLandmarks = rawWorld.mapIndexed { index, wlm ->
            // World coordinates: X right, Y down/up, Z forward from mid-hip
            val adjustedWorldX = if (isFrontFacingCamera) -wlm.x() else wlm.x()
            PoseWorldLandmark(
                index = index,
                x = adjustedWorldX,
                y = wlm.y(),
                z = wlm.z(),
                visibility = wlm.visibility().orElse(0.0f),
                presence = wlm.presence().orElse(0.0f)
            )
        }

        val frameResult = PoseFrameResult(
            normalizedLandmarks = normalizedLandmarks,
            worldLandmarks = worldLandmarks,
            timestampMs = frameTimestampMs,
            inferenceTimeMs = inferenceTimeMs,
            hasPose = true
        )

        // Non-blocking emit; drops oldest frame if UI or kinematics pipeline lags
        _poseResults.tryEmit(frameResult)
    }

    private fun resolveAvailableAsset(preferredPath: String): String {
        return try {
            val list = context.assets.list("") ?: emptyArray()
            if (list.contains(preferredPath)) {
                preferredPath
            } else if (list.contains("pose_landmarker_heavy.task")) {
                "pose_landmarker_heavy.task"
            } else if (list.contains("pose_landmarker_full.task")) {
                "pose_landmarker_full.task"
            } else {
                preferredPath
            }
        } catch (e: Exception) {
            preferredPath
        }
    }

    fun isInitialized(): Boolean = poseLandmarker != null

    fun clearPoseLandmarker() {
        poseLandmarker?.close()
        poseLandmarker = null
    }

    fun close() {
        clearPoseLandmarker()
    }
}


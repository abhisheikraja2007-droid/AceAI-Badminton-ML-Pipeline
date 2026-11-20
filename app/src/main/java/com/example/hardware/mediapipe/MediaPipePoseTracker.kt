package com.example.hardware.mediapipe

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaPipePoseTracker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var poseLandmarker: PoseLandmarker? = null
    private var onPoseResult: ((PoseLandmarkerResult, Long) -> Unit)? = null

    fun initialize(
        modelName: String = "pose_landmarker_heavy.task",
        minPoseDetectionConfidence: Float = 0.5f,
        minPosePresenceConfidence: Float = 0.5f,
        minTrackingConfidence: Float = 0.5f,
        onResult: (PoseLandmarkerResult, Long) -> Unit
    ) {
        this.onPoseResult = onResult
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(modelName)
            .build()

        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setMinPoseDetectionConfidence(minPoseDetectionConfidence)
            .setMinPosePresenceConfidence(minPosePresenceConfidence)
            .setMinTrackingConfidence(minTrackingConfidence)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result, _ ->
                onPoseResult?.invoke(result, result.timestampMs())
            }
            .setErrorListener { error ->
                error.printStackTrace()
            }
            .build()

        poseLandmarker = PoseLandmarker.createFromOptions(context, options)
    }

    fun detectLiveStream(mpImage: MPImage, timestampMs: Long) {
        poseLandmarker?.detectAsync(mpImage, timestampMs)
    }

    fun detectLiveStream(imageProxy: ImageProxy) {
        val bitmap = imageProxyToBitmap(imageProxy)
        val mpImage = BitmapImageBuilder(bitmap).build()
        val frameTime = SystemClock.uptimeMillis()

        poseLandmarker?.detectAsync(mpImage, frameTime)
        imageProxy.close()
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val planes = image.planes
        val buffer: ByteBuffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }

    fun close() {
        poseLandmarker?.close()
        poseLandmarker = null
    }
}


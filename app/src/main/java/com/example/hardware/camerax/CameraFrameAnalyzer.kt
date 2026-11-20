package com.example.hardware.camerax

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.hardware.camera2.CaptureRequest
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.util.Range
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-performance, zero-leak CameraX ImageAnalysis.Analyzer designed for real-time
 * biomechanical pose tracking at 60 FPS with MediaPipe Tasks Vision.
 *
 * Privacy Guarantees:
 * - Purely in-memory processing; strictly ZERO image or video bytes are written to disk, cache, or external storage.
 * - ImageProxy buffers are instantly released in a guaranteed finally block to prevent memory leaks and frame drops.
 */
class CameraFrameAnalyzer(
    private val onFrameAnalyzed: (mpImage: MPImage, timestampMs: Long) -> Unit
) : ImageAnalysis.Analyzer {

    private val isClosed = AtomicBoolean(false)

    override fun analyze(imageProxy: ImageProxy) {
        if (isClosed.get()) {
            imageProxy.close()
            return
        }

        try {
            // High-precision monotonically increasing uptime timestamp for MediaPipe LIVE_STREAM
            val frameTimestampMs = SystemClock.uptimeMillis()

            // Convert frame bytes directly into an in-memory MPImage
            val mpImage = convertToMpImage(imageProxy)

            // Deliver in-memory MPImage to the MediaPipe processor
            onFrameAnalyzed(mpImage, frameTimestampMs)
        } catch (e: Exception) {
            Log.e(TAG, "Biomechanical frame extraction failed: ${e.message}", e)
        } finally {
            // STRICT GUARANTEE: Never leak ImageReader native buffers
            imageProxy.close()
        }
    }

    /**
     * Converts the ImageProxy into an upright, in-memory MPImage.
     * Supports high-throughput RGBA_8888 direct buffer extraction with row-padding compensation,
     * as well as high-speed YUV_420_888 fallback.
     */
    private fun convertToMpImage(imageProxy: ImageProxy): MPImage {
        val width = imageProxy.width
        val height = imageProxy.height
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        val baseBitmap = when (imageProxy.format) {
            PixelFormat.RGBA_8888 -> {
                val plane = imageProxy.planes[0]
                val buffer: ByteBuffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * width

                val bitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888
                )
                buffer.rewind()
                bitmap.copyPixelsFromBuffer(buffer)

                // If row padding exists, crop to exact sensor width
                if (rowPadding != 0) {
                    val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                    if (croppedBitmap != bitmap) {
                        bitmap.recycle()
                    }
                    croppedBitmap
                } else {
                    bitmap
                }
            }
            ImageFormat.YUV_420_888 -> {
                // High-speed CameraX native YUV to ARGB converter
                imageProxy.toBitmap()
            }
            else -> {
                imageProxy.toBitmap()
            }
        }

        // Apply sensor rotation matrix so MediaPipe receives an anatomically upright athlete pose
        val rotatedBitmap = if (rotationDegrees != 0) {
            val matrix = Matrix().apply {
                postRotate(rotationDegrees.toFloat())
            }
            val oriented = Bitmap.createBitmap(
                baseBitmap,
                0,
                0,
                baseBitmap.width,
                baseBitmap.height,
                matrix,
                true
            )
            if (oriented != baseBitmap) {
                baseBitmap.recycle()
            }
            oriented
        } else {
            baseBitmap
        }

        // Build volatile in-memory MPImage for MediaPipe
        return BitmapImageBuilder(rotatedBitmap).build()
    }

    fun release() {
        isClosed.set(true)
    }

    companion object {
        private const val TAG = "CameraFrameAnalyzer"

        /**
         * Dedicated high-priority single-thread Executor for frame processing
         * running at THREAD_PRIORITY_FOREGROUND to avoid UI thread jitter and GC stalls.
         */
        fun createHighPriorityExecutor(): ExecutorService {
            return Executors.newSingleThreadExecutor(object : ThreadFactory {
                override fun newThread(runnable: Runnable): Thread {
                    return Thread {
                        Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND)
                        runnable.run()
                    }.apply {
                        name = "AceAi-CameraFrameAnalyzer-60Fps"
                        priority = Thread.MAX_PRIORITY
                    }
                }
            })
        }

        /**
         * Creates an ImageAnalysis instance configured with:
         * 1. STRATEGY_KEEP_ONLY_LATEST (drops stale frames to prevent buffer backlog).
         * 2. OUTPUT_IMAGE_FORMAT_RGBA_8888 (zero-copy RGBA direct plane memory).
         * 3. Camera2Interop target 60 FPS capture range (CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE).
         */
        @OptIn(ExperimentalCamera2Interop::class)
        fun create60FpsImageAnalysis(
            executor: Executor = createHighPriorityExecutor(),
            analyzer: ImageAnalysis.Analyzer,
            targetFps: Int = 60
        ): ImageAnalysis {
            val builder = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)

            // Force Camera2 AE Target FPS Range to 60 FPS
            val interopExtender = Camera2Interop.Extender(builder)
            interopExtender.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                Range(targetFps, targetFps)
            )

            val imageAnalysis = builder.build()
            imageAnalysis.setAnalyzer(executor, analyzer)
            return imageAnalysis
        }
    }
}

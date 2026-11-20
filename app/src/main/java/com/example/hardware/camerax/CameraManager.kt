package com.example.hardware.camerax

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.MPImage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ExecutorService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var highPriorityExecutor: ExecutorService? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var activeAnalyzer: CameraFrameAnalyzer? = null

    /**
     * Starts CameraX bound to the given lifecycle, configuring 60 FPS target capture
     * via Camera2Interop and routing in-memory MPImage frames directly to onMpImageAvailable.
     */
    fun startCamera60Fps(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        targetFps: Int = 60,
        onMpImageAvailable: (mpImage: MPImage, timestampMs: Long) -> Unit
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            val executor = CameraFrameAnalyzer.createHighPriorityExecutor()
            highPriorityExecutor = executor

            val analyzer = CameraFrameAnalyzer(onMpImageAvailable)
            activeAnalyzer = analyzer

            val imageAnalysis = CameraFrameAnalyzer.create60FpsImageAnalysis(
                executor = executor,
                analyzer = analyzer,
                targetFps = targetFps
            )

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } catch (exc: Exception) {
                exc.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stopCamera() {
        cameraProvider?.unbindAll()
        activeAnalyzer?.release()
        activeAnalyzer = null
        highPriorityExecutor?.shutdown()
        highPriorityExecutor = null
    }
}
